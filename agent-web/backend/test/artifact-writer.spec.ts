import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DatabaseService } from "../src/persistence/database.service.js";
import { EventBusService } from "../src/workflow/event-bus.service.js";
import { GenerationService } from "../src/generation/generation.service.js";
import { StagingService, parseManifest } from "../src/generation/staging.service.js";
import { TargetContractService } from "../src/generation/target-contract.service.js";
import { PiAdapterService } from "../src/pi/pi-adapter.service.js";
import { ArtifactWriterService } from "../src/artifact/artifact-writer.service.js";
import { createGenerationTarget, seedActiveWorkflow } from "./generation-fixture.js";

describe("safe artifact writer", () => {
  let root: string;
  let database: DatabaseService;
  let target: string;
  let generation: GenerationService;
  let writer: ArtifactWriterService;
  const user = { userId: "user_sales", userName: "Sales User" };

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), "flowmind-writer-"));
    target = createGenerationTarget(root);
    process.env.AGENT_DATA_DIR = join(root, "data");
    process.env.AGENT_DB_PATH = join(root, "agent.db");
    process.env.AGENT_ALLOWED_TARGET_ROOTS = root;
    process.env.AGENT_FAKE_PI = "true";
    database = new DatabaseService();
    const targets = new TargetContractService();
    const staging = new StagingService(database);
    writer = new ArtifactWriterService(database, targets);
    generation = new GenerationService(
      database,
      targets,
      staging,
      new PiAdapterService(database),
      new EventBusService(),
      undefined,
      writer,
    );
    seedActiveWorkflow(database, "session-writer", target);
  });

  afterEach(() => {
    database.onModuleDestroy();
    rmSync(root, { recursive: true, force: true });
    delete process.env.AGENT_DATA_DIR;
    delete process.env.AGENT_DB_PATH;
    delete process.env.AGENT_ALLOWED_TARGET_ROOTS;
    delete process.env.AGENT_FAKE_PI;
  });

  it("writes the complete confirmed Manifest and completes the workflow", async () => {
    const row = await generatedRow();
    const manifest = parseManifest(row);
    const request = {
      generationRevision: row.generation_revision,
      files: manifest.files.map(({ relativePath, stagedSha256 }) => ({ relativePath, stagedSha256 })),
    };
    const result = writer.confirm(row, request, "write-success");
    expect(result.completed).toBe(true);
    expect(database.getGeneration(row.id)?.status).toBe("COMPLETED");
    expect(database.getSession(row.session_id)?.state).toBe("COMPLETED");
    for (const file of manifest.files) expect(existsSync(join(target, ...file.relativePath.split("/")))).toBe(true);
  });

  it("replays a completed write before checking a stale row version", async () => {
    const row = await generatedRow();
    const manifest = parseManifest(row);
    const request = {
      generationRevision: row.generation_revision,
      files: manifest.files.map(({ relativePath, stagedSha256 }) => ({ relativePath, stagedSha256 })),
    };
    const rowVersion = database.getSession(row.session_id)!.row_version;
    const first = generation.confirmWrite(
      row.session_id,
      row.id,
      user,
      rowVersion,
      request,
      "write-replay",
    );
    const replay = generation.confirmWrite(
      row.session_id,
      row.id,
      user,
      rowVersion,
      request,
      "write-replay",
    );
    expect(replay).toEqual(first);
  });

  it("rolls target files back in reverse order after a partial failure", async () => {
    const row = await generatedRow();
    const manifest = parseManifest(row);
    const first = manifest.files[0];
    const firstTarget = join(target, ...first.relativePath.split("/"));
    const route = join(target, "frontend/src/router/generated-routes.ts");
    const originalRoute = readFileSync(route, "utf8");
    expect(() => writer.confirm(row, {
      generationRevision: row.generation_revision,
      files: manifest.files.map(({ relativePath, stagedSha256 }) => ({ relativePath, stagedSha256 })),
    }, "write-failure", { failAfterWrites: 1 })).toThrow(/restored/);
    expect(existsSync(firstTarget)).toBe(first.changeType === "MODIFY");
    expect(readFileSync(route, "utf8")).toBe(originalRoute);
    expect(database.getGeneration(row.id)).toEqual(expect.objectContaining({
      status: "WRITE_FAILED",
      write_status: "ROLLED_BACK",
      can_write: 1,
    }));
    expect(database.getSession(row.session_id)?.state).toBe("ARTIFACT_WRITE_FAILED");
  });

  it("restores an applying file after interruption before the written journal update", async () => {
    const row = await generatedRow();
    const manifest = parseManifest(row);
    const file = manifest.files.find(({ changeType }) => changeType === "MODIFY")!;
    const writeId = "write-interrupted";
    const targetPath = join(target, ...file.relativePath.split("/"));
    const stagedPath = join(row.staging_dir, ...file.relativePath.split("/"));
    const backupDir = join(database.dataDir, "backups", row.id, writeId);
    const backupPath = join(backupDir, ...file.relativePath.split("/"));
    const original = readFileSync(targetPath, "utf8");
    mkdirSync(join(backupPath, ".."), { recursive: true });
    copyFileSync(targetPath, backupPath);
    writeFileSync(targetPath, readFileSync(stagedPath));
    const journal = [{
      relativePath: file.relativePath,
      targetPath,
      stagedPath,
      backupPath,
      tempPath: join(target, ".interrupted.flowmind.tmp"),
      oldPath: join(target, ".interrupted.flowmind.old"),
      existed: true,
      status: "APPLYING",
    }];
    const now = new Date().toISOString();
    database.db.prepare(`
      INSERT INTO agent_artifact_write (
        id, generation_id, revision, idempotency_key, request_hash, status,
        manifest_json, backup_dir, journal_json, started_at
      ) VALUES (?, ?, ?, ?, ?, 'WRITING', ?, ?, ?, ?)
    `).run(writeId, row.id, row.generation_revision, "interrupted", "hash", JSON.stringify(manifest), backupDir, JSON.stringify(journal), now);
    database.db.prepare(`
      UPDATE agent_code_generation SET status = 'WRITING', write_status = 'WRITING',
        write_journal_json = ? WHERE id = ?
    `).run(JSON.stringify(journal), row.id);
    database.db.prepare("UPDATE agent_session SET state = 'WRITING_ARTIFACTS' WHERE id = ?").run(row.session_id);

    writer.onModuleInit();

    expect(readFileSync(targetPath, "utf8")).toBe(original);
    expect(database.getGeneration(row.id)).toEqual(expect.objectContaining({
      status: "WRITE_FAILED",
      write_status: "ROLLED_BACK",
      can_write: 1,
    }));
  });

  async function generatedRow() {
    const started = generation.start("session-writer", user, 0, "generate", target);
    const deadline = Date.now() + 3000;
    while (Date.now() < deadline) {
      const row = database.getGeneration(started.generationId)!;
      if (row.status === "REVIEW") {
        database.db.prepare(`
          UPDATE agent_code_generation SET quality_revision = generation_revision,
            hard_gate_passed = 1, override_required = 0, can_write = 1 WHERE id = ?
        `).run(row.id);
        return database.getGeneration(row.id)!;
      }
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    throw new Error("generation did not reach review");
  }
});

