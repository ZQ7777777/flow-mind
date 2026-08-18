import { HttpStatus, Inject, Injectable, OnModuleInit } from "@nestjs/common";
import { createHash, randomUUID } from "node:crypto";
import {
  copyFileSync,
  existsSync,
  lstatSync,
  mkdirSync,
  readFileSync,
  renameSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { dirname, join, resolve } from "node:path";
import type { ArtifactManifest } from "@flowmind/agent-contracts";
import { AgentError } from "../common/agent-error.js";
import { DatabaseService, type GenerationRow } from "../persistence/database.service.js";
import { assertInside, assertNoLinkInExistingPath } from "../generation/path-safety.js";
import { parseManifest } from "../generation/staging.service.js";
import { TargetContractService, sha256 } from "../generation/target-contract.service.js";

export interface ConfirmArtifactWriteRequest {
  generationRevision: number;
  files: Array<{ relativePath: string; stagedSha256: string }>;
}

export interface ArtifactWriteResult {
  completed: true;
  generationId: string;
  revision: number;
  writtenAt: string;
}

interface WriteEntry {
  relativePath: string;
  targetPath: string;
  stagedPath: string;
  backupPath: string;
  tempPath: string;
  oldPath: string;
  existed: boolean;
  status: "PENDING" | "APPLYING" | "WRITTEN" | "ROLLED_BACK";
}

@Injectable()
export class ArtifactWriterService implements OnModuleInit {
  constructor(
    @Inject(DatabaseService) private readonly database: DatabaseService,
    @Inject(TargetContractService) private readonly targets: TargetContractService,
  ) {}

  onModuleInit(): void {
    this.recoverInterruptedWrites();
  }

  replay(
    generation: GenerationRow,
    request: ConfirmArtifactWriteRequest,
    idempotencyKey: string,
  ): ArtifactWriteResult | undefined {
    if (!idempotencyKey?.trim()) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required.", generation.session_id);
    }
    const requestHash = hashJson(request);
    const replay = this.database.db.prepare(`
      SELECT status, request_hash, completed_at FROM agent_artifact_write
      WHERE generation_id = ? AND idempotency_key = ?
    `).get(generation.id, idempotencyKey) as { status: string; request_hash: string; completed_at: string | null } | undefined;
    if (!replay) return undefined;
    if (replay.request_hash !== requestHash) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_IDEMPOTENCY_CONFLICT", "Idempotency key was reused with different content.", generation.session_id);
    }
    if (replay.status === "COMPLETED" && replay.completed_at) {
      return { completed: true, generationId: generation.id, revision: request.generationRevision, writtenAt: replay.completed_at };
    }
    throw new AgentError(HttpStatus.CONFLICT, "AGENT_WRITE_RETRY_KEY_REQUIRED", "A new idempotency key is required after a failed write.", generation.session_id);
  }

  confirm(
    generation: GenerationRow,
    request: ConfirmArtifactWriteRequest,
    idempotencyKey: string,
    fault?: { failAfterWrites: number },
  ): ArtifactWriteResult {
    const replay = this.replay(generation, request, idempotencyKey);
    if (replay) return replay;
    const requestHash = hashJson(request);
    const manifest = this.validate(generation, request);
    const writeId = `write_${randomUUID()}`;
    const backupDir = join(this.database.dataDir, "backups", generation.id, writeId);
    mkdirSync(backupDir, { recursive: true });
    const entries: WriteEntry[] = manifest.files.map((file, index) => {
      const targetPath = resolve(generation.target_root, file.relativePath);
      const stagedPath = resolve(generation.staging_dir, file.relativePath);
      const backupPath = resolve(backupDir, file.relativePath);
      const tempPath = join(dirname(targetPath), `.${writeId}-${index}.flowmind.tmp`);
      const oldPath = join(dirname(targetPath), `.${writeId}-${index}.flowmind.old`);
      assertInside(generation.target_root, targetPath, generation.session_id);
      assertInside(generation.staging_dir, stagedPath, generation.session_id);
      assertInside(backupDir, backupPath, generation.session_id);
      assertNoLinkInExistingPath(generation.target_root, targetPath, generation.session_id);
      assertNoLinkInExistingPath(generation.staging_dir, stagedPath, generation.session_id);
      const existed = existsSync(targetPath);
      if (file.changeType === "ADD" && existed) this.conflict(generation, file.relativePath);
      if (file.changeType === "MODIFY" && (!existed || sha256(readFileSync(targetPath)) !== file.baseSha256)) {
        this.conflict(generation, file.relativePath);
      }
      if (!existsSync(stagedPath) || lstatSync(stagedPath).isSymbolicLink()
        || sha256(readFileSync(stagedPath)) !== file.stagedSha256) {
        throw new AgentError(HttpStatus.CONFLICT, "AGENT_STAGED_FILE_CHANGED", `Staged file changed: ${file.relativePath}`, generation.session_id);
      }
      if (existed) {
        mkdirSync(dirname(backupPath), { recursive: true });
        copyFileSync(targetPath, backupPath);
      }
      return {
        relativePath: file.relativePath,
        targetPath,
        stagedPath,
        backupPath,
        tempPath,
        oldPath,
        existed,
        status: "PENDING" as const,
      };
    });
    const startedAt = new Date().toISOString();
    this.database.transaction(() => {
      this.database.db.prepare(`
        INSERT INTO agent_artifact_write (
          id, generation_id, revision, idempotency_key, request_hash, status,
          manifest_json, backup_dir, journal_json, started_at
        ) VALUES (?, ?, ?, ?, ?, 'PREPARED', ?, ?, ?, ?)
      `).run(writeId, generation.id, generation.generation_revision, idempotencyKey, requestHash, JSON.stringify(manifest), backupDir, JSON.stringify(entries), startedAt);
      this.database.db.prepare(`
        UPDATE agent_code_generation SET status = 'WRITING', write_status = 'WRITING',
          write_confirm_key = ?, write_confirm_hash = ?, backup_dir = ?,
          write_journal_json = ?, updated_at = ? WHERE id = ?
      `).run(idempotencyKey, requestHash, backupDir, JSON.stringify(entries), startedAt, generation.id);
      this.database.db.prepare(`
        UPDATE agent_session SET state = 'WRITING_ARTIFACTS', row_version = row_version + 1,
          updated_at = ? WHERE id = ?
      `).run(startedAt, generation.session_id);
    });

    let written = 0;
    try {
      for (const entry of entries) {
        mkdirSync(dirname(entry.targetPath), { recursive: true });
        entry.status = "APPLYING";
        this.persistJournal(writeId, generation.id, entries);
        if (existsSync(entry.tempPath)) rmSync(entry.tempPath, { force: true });
        if (existsSync(entry.oldPath)) rmSync(entry.oldPath, { force: true });
        writeFileSync(entry.tempPath, readFileSync(entry.stagedPath));
        if (entry.existed) renameSync(entry.targetPath, entry.oldPath);
        try {
          renameSync(entry.tempPath, entry.targetPath);
          if (existsSync(entry.oldPath)) rmSync(entry.oldPath, { force: true });
        } catch (error) {
          if (existsSync(entry.oldPath) && !existsSync(entry.targetPath)) renameSync(entry.oldPath, entry.targetPath);
          if (existsSync(entry.tempPath)) rmSync(entry.tempPath, { force: true });
          throw error;
        }
        entry.status = "WRITTEN";
        written += 1;
        this.persistJournal(writeId, generation.id, entries);
        if (fault && written >= fault.failAfterWrites) throw new Error("Injected artifact write failure");
      }
      const writtenAt = new Date().toISOString();
      this.database.transaction(() => {
        this.database.db.prepare("UPDATE agent_artifact_write SET status = 'COMPLETED', journal_json = ?, completed_at = ? WHERE id = ?")
          .run(JSON.stringify(entries), writtenAt, writeId);
        this.database.db.prepare(`
          UPDATE agent_code_generation SET status = 'CONFIGURING_ENTRY', write_status = 'FILES_WRITTEN',
            write_journal_json = ?, written_at = ?, confirmed_by = created_by,
            confirmed_at = ?, updated_at = ? WHERE id = ?
        `).run(JSON.stringify(entries), writtenAt, writtenAt, writtenAt, generation.id);
        this.database.db.prepare(`
          UPDATE agent_session SET state = 'BUSINESS_ENTRY_CONFIGURING', row_version = row_version + 1,
            last_error_code = NULL, last_error_message = NULL, updated_at = ? WHERE id = ?
        `).run(writtenAt, generation.session_id);
      });
      return { completed: true, generationId: generation.id, revision: generation.generation_revision, writtenAt };
    } catch (error) {
      const restored = this.rollback(entries);
      const now = new Date().toISOString();
      const code = restored ? "AGENT_ARTIFACT_WRITE_ROLLED_BACK" : "AGENT_ARTIFACT_ROLLBACK_FAILED";
      this.database.transaction(() => {
        this.database.db.prepare(`
          UPDATE agent_artifact_write SET status = ?, journal_json = ?,
            error_code = ?, error_message = ?, completed_at = ? WHERE id = ?
        `).run(restored ? "ROLLED_BACK" : "ROLLBACK_FAILED", JSON.stringify(entries), code, error instanceof Error ? error.message : String(error), now, writeId);
        this.database.db.prepare(`
          UPDATE agent_code_generation SET status = 'WRITE_FAILED', write_status = ?,
            can_write = ?, write_journal_json = ?, last_error_code = ?,
            last_error_message = ?, updated_at = ? WHERE id = ?
        `).run(restored ? "ROLLED_BACK" : "ROLLBACK_FAILED", restored ? 1 : 0, JSON.stringify(entries), code, error instanceof Error ? error.message : String(error), now, generation.id);
        this.database.db.prepare(`
          UPDATE agent_session SET state = 'ARTIFACT_WRITE_FAILED', row_version = row_version + 1,
            last_error_code = ?, last_error_message = ?, updated_at = ? WHERE id = ?
        `).run(code, error instanceof Error ? error.message : String(error), now, generation.session_id);
      });
      throw new AgentError(HttpStatus.INTERNAL_SERVER_ERROR, code, restored
        ? "Artifact write failed and all target files were restored."
        : "Artifact write failed and rollback was incomplete.", generation.session_id);
    }
  }

  resumeEntryConfiguration(
    generation: GenerationRow,
    request: ConfirmArtifactWriteRequest,
  ): ArtifactWriteResult {
    if (generation.status !== "ENTRY_CONFIG_FAILED" || generation.write_status !== "ENTRY_CONFIG_FAILED") {
      throw new AgentError(
        HttpStatus.CONFLICT,
        "AGENT_GENERATION_STATE_CONFLICT",
        "Generation is not waiting for business entry registration.",
        generation.session_id,
      );
    }
    this.targets.validate(generation.target_root, generation.session_id);
    this.validateConfirmation(generation, request);
    const completedWrite = this.database.db.prepare(`
      SELECT completed_at FROM agent_artifact_write
      WHERE generation_id = ? AND status = 'COMPLETED'
      ORDER BY started_at DESC LIMIT 1
    `).get(generation.id) as { completed_at: string | null } | undefined;
    const writtenAt = generation.written_at || completedWrite?.completed_at;
    if (!completedWrite || !writtenAt) {
      throw new AgentError(
        HttpStatus.CONFLICT,
        "AGENT_ARTIFACT_WRITE_NOT_COMPLETED",
        "Business entry registration cannot be retried before the artifact write completes.",
        generation.session_id,
      );
    }
    const now = new Date().toISOString();
    this.database.transaction(() => {
      this.database.db.prepare(`
        UPDATE agent_code_generation SET status = 'CONFIGURING_ENTRY', write_status = 'FILES_WRITTEN',
          last_error_code = NULL, last_error_message = NULL, updated_at = ? WHERE id = ?
      `).run(now, generation.id);
      this.database.db.prepare(`
        UPDATE agent_session SET state = 'BUSINESS_ENTRY_CONFIGURING', row_version = row_version + 1,
          last_error_code = NULL, last_error_message = NULL, updated_at = ? WHERE id = ?
      `).run(now, generation.session_id);
    });
    return {
      completed: true,
      generationId: generation.id,
      revision: generation.generation_revision,
      writtenAt,
    };
  }

  completeEntryConfiguration(generationId: string, sessionId: string): void {
    const now = new Date().toISOString();
    this.database.transaction(() => {
      this.database.db.prepare(`
        UPDATE agent_code_generation SET status = 'COMPLETED', write_status = 'COMPLETED',
          last_error_code = NULL, last_error_message = NULL, updated_at = ?
        WHERE id = ? AND status = 'CONFIGURING_ENTRY'
      `).run(now, generationId);
      this.database.db.prepare(`
        UPDATE agent_session SET state = 'COMPLETED', row_version = row_version + 1,
          last_error_code = NULL, last_error_message = NULL, updated_at = ?
        WHERE id = ? AND state = 'BUSINESS_ENTRY_CONFIGURING'
      `).run(now, sessionId);
    });
  }

  failEntryConfiguration(
    generationId: string,
    sessionId: string,
    errorCode: string,
    errorMessage: string,
  ): void {
    const now = new Date().toISOString();
    this.database.transaction(() => {
      this.database.db.prepare(`
        UPDATE agent_code_generation SET status = 'ENTRY_CONFIG_FAILED', write_status = 'ENTRY_CONFIG_FAILED',
          can_write = 1, last_error_code = ?, last_error_message = ?, updated_at = ?
        WHERE id = ? AND status = 'CONFIGURING_ENTRY'
      `).run(errorCode, errorMessage, now, generationId);
      this.database.db.prepare(`
        UPDATE agent_session SET state = 'BUSINESS_ENTRY_CONFIG_FAILED', row_version = row_version + 1,
          last_error_code = ?, last_error_message = ?, updated_at = ?
        WHERE id = ? AND state = 'BUSINESS_ENTRY_CONFIGURING'
      `).run(errorCode, errorMessage, now, sessionId);
    });
  }

  private validate(generation: GenerationRow, request: ConfirmArtifactWriteRequest): ArtifactManifest {
    this.targets.validate(generation.target_root, generation.session_id);
    if (generation.status !== "REVIEW" && !(generation.status === "WRITE_FAILED" && generation.write_status === "ROLLED_BACK")) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_GENERATION_STATE_CONFLICT", "Generation is not ready to write.", generation.session_id);
    }
    return this.validateConfirmation(generation, request);
  }

  private validateConfirmation(generation: GenerationRow, request: ConfirmArtifactWriteRequest): ArtifactManifest {
    if (generation.generation_revision !== request.generationRevision
      || generation.quality_revision !== request.generationRevision
      || !generation.hard_gate_passed || !generation.can_write) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_WRITE_GATE_CLOSED", "Current revision has not passed all required quality gates.", generation.session_id);
    }
    const manifest = parseManifest(generation);
    const requested = [...request.files].sort((a, b) => a.relativePath.localeCompare(b.relativePath));
    const expected = manifest.files.map(({ relativePath, stagedSha256 }) => ({ relativePath, stagedSha256 }))
      .sort((a, b) => a.relativePath.localeCompare(b.relativePath));
    if (JSON.stringify(requested) !== JSON.stringify(expected)) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_MANIFEST_CONFIRMATION_MISMATCH", "Write confirmation must include the complete current Manifest.", generation.session_id);
    }
    return manifest;
  }

  private persistJournal(writeId: string, generationId: string, entries: WriteEntry[]): void {
    const value = JSON.stringify(entries);
    this.database.db.prepare("UPDATE agent_artifact_write SET status = 'WRITING', journal_json = ? WHERE id = ?")
      .run(value, writeId);
    this.database.db.prepare("UPDATE agent_code_generation SET write_journal_json = ? WHERE id = ?")
      .run(value, generationId);
  }

  private rollback(entries: WriteEntry[]): boolean {
    let restored = true;
    for (const entry of [...entries].reverse()) {
      if (!["APPLYING", "WRITTEN"].includes(entry.status)) continue;
      try {
        if (entry.existed) {
          const restorePath = `${entry.tempPath}.restore`;
          copyFileSync(entry.backupPath, restorePath);
          if (existsSync(entry.targetPath)) rmSync(entry.targetPath, { force: true });
          renameSync(restorePath, entry.targetPath);
        } else if (existsSync(entry.targetPath)) {
          rmSync(entry.targetPath, { force: true });
        }
        if (existsSync(entry.tempPath)) rmSync(entry.tempPath, { force: true });
        if (existsSync(entry.oldPath)) rmSync(entry.oldPath, { force: true });
        entry.status = "ROLLED_BACK";
      } catch {
        restored = false;
      }
    }
    return restored;
  }

  private recoverInterruptedWrites(): void {
    const rows = this.database.db.prepare(`
      SELECT w.id, w.generation_id, w.journal_json, g.session_id
      FROM agent_artifact_write w JOIN agent_code_generation g ON g.id = w.generation_id
      WHERE w.status IN ('PREPARED', 'WRITING')
    `).all() as Array<{ id: string; generation_id: string; journal_json: string; session_id: string }>;
    for (const row of rows) {
      let entries: WriteEntry[] = [];
      try { entries = JSON.parse(row.journal_json) as WriteEntry[]; } catch { /* handled as failed recovery */ }
      const restored = entries.length > 0 && this.rollback(entries);
      const now = new Date().toISOString();
      this.database.transaction(() => {
        this.database.db.prepare("UPDATE agent_artifact_write SET status = ?, journal_json = ?, completed_at = ? WHERE id = ?")
          .run(restored ? "ROLLED_BACK" : "ROLLBACK_FAILED", JSON.stringify(entries), now, row.id);
        this.database.db.prepare(`
          UPDATE agent_code_generation SET status = 'WRITE_FAILED', write_status = ?,
            can_write = ?, write_journal_json = ?, last_error_code = 'AGENT_INTERRUPTED',
            updated_at = ? WHERE id = ?
        `).run(restored ? "ROLLED_BACK" : "ROLLBACK_FAILED", restored ? 1 : 0, JSON.stringify(entries), now, row.generation_id);
        this.database.db.prepare(`
          UPDATE agent_session SET state = 'ARTIFACT_WRITE_FAILED', row_version = row_version + 1,
            last_error_code = 'AGENT_INTERRUPTED', updated_at = ? WHERE id = ?
        `).run(now, row.session_id);
      });
    }
  }

  private conflict(generation: GenerationRow, relativePath: string): never {
    throw new AgentError(HttpStatus.CONFLICT, "AGENT_TARGET_CONFLICT", `Target file changed after generation: ${relativePath}`, generation.session_id);
  }
}

function hashJson(value: unknown): string {
  return createHash("sha256").update(JSON.stringify(value)).digest("hex");
}
