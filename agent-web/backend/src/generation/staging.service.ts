import { HttpStatus, Inject, Injectable } from "@nestjs/common";
import { createTwoFilesPatch } from "diff";
import { minimatch } from "minimatch";
import {
  existsSync,
  lstatSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { dirname, resolve } from "node:path";
import type {
  ArtifactFile,
  ArtifactManifest,
  BusinessRequirement,
  GeneratedFileContent,
  GeneratedFileDiff,
  GenerationTargetContract,
} from "@flowmind/agent-contracts";
import { AgentError } from "../common/agent-error.js";
import { DatabaseService, type GenerationRow } from "../persistence/database.service.js";
import { MAX_GENERATED_FILE_BYTES } from "./generation.constants.js";
import { deriveGenerationSpec } from "./generation-spec.js";
import { assertInside, assertNoLinkInExistingPath, normalizeRelativePath } from "./path-safety.js";
import { sha256 } from "./target-contract.service.js";

@Injectable()
export class StagingService {
  constructor(@Inject(DatabaseService) private readonly database: DatabaseService) {}

  prepare(stagingDir: string): void {
    mkdirSync(stagingDir, { recursive: true });
  }

  list(generation: GenerationRow): string[] {
    if (!existsSync(generation.staging_dir)) return [];
    return walk(generation.staging_dir).sort();
  }

  read(generation: GenerationRow, relativePathInput: string): GeneratedFileContent {
    const relativePath = this.allowedPath(generation, relativePathInput);
    const content = readUtf8(this.stagedPath(generation, relativePath), generation.session_id);
    return {
      generationId: generation.id,
      generationRevision: generation.generation_revision,
      relativePath,
      content,
      sha256: sha256(content),
    };
  }

  writeDuringGeneration(generation: GenerationRow, relativePathInput: string, content: string): void {
    if (generation.status !== "GENERATING") throw stateError(generation);
    this.write(generation, relativePathInput, content);
  }

  deleteDuringGeneration(generation: GenerationRow, relativePathInput: string): void {
    if (generation.status !== "GENERATING") throw stateError(generation);
    const relativePath = this.allowedPath(generation, relativePathInput);
    const path = this.stagedPath(generation, relativePath);
    if (existsSync(path)) rmSync(path, { force: true });
  }

  complete(generation: GenerationRow, contract: GenerationTargetContract, reportedFiles: string[]): ArtifactManifest {
    if (generation.status !== "GENERATING") throw stateError(generation);
    const actual = this.list(generation);
    const reported = [...new Set(reportedFiles.map((path) => this.allowedPath(generation, path)))].sort();
    const expected = expectedFiles(generation).sort();
    if (!sameFiles(actual, expected) || !sameFiles(reported, expected)) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_INCOMPLETE", "generated file set must exactly match the current business generation boundary", generation.session_id, {
        expected,
        actual,
        reported,
      });
    }
    const files = actual.map((relativePath) => this.describe(generation, relativePath));
    const manifest: ArtifactManifest = {
      generationId: generation.id,
      targetRoot: generation.target_root,
      contractVersion: contract.contractVersion,
      revision: 1,
      files,
    };
    const now = new Date().toISOString();
    this.database.transaction(() => {
      this.database.db.prepare(`
        UPDATE agent_code_generation SET status = 'REVIEW', generation_revision = 1,
          artifact_manifest_json = ?, last_error_code = NULL, last_error_message = NULL, updated_at = ?
        WHERE id = ? AND status = 'GENERATING'
      `).run(JSON.stringify(manifest), now, generation.id);
      this.database.db.prepare(`
        UPDATE agent_session SET state = 'CODE_REVIEW', row_version = row_version + 1,
          last_error_code = NULL, last_error_message = NULL, updated_at = ?
        WHERE id = ? AND state = 'CODE_GENERATING'
      `).run(now, generation.session_id);
    });
    return manifest;
  }

  edit(generation: GenerationRow, relativePathInput: string, content: string, expectedRevision: number): ArtifactManifest {
    if (generation.status !== "REVIEW") throw stateError(generation);
    if (generation.generation_revision !== expectedRevision) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_GENERATION_REVISION_CONFLICT", "generation revision is stale", generation.session_id, {
        expected: generation.generation_revision,
      });
    }
    const relativePath = this.allowedPath(generation, relativePathInput);
    this.write(generation, relativePath, content);
    const previous = parseManifest(generation);
    const nextRevision = generation.generation_revision + 1;
    const files = previous.files.map((file) => relativePath === file.relativePath
      ? { ...this.describe(generation, relativePath), editedByUser: true }
      : file);
    const manifest: ArtifactManifest = { ...previous, revision: nextRevision, files };
    this.database.db.prepare(`
      UPDATE agent_code_generation SET generation_revision = ?, artifact_manifest_json = ?, updated_at = ?
      WHERE id = ? AND generation_revision = ? AND status = 'REVIEW'
    `).run(nextRevision, JSON.stringify(manifest), new Date().toISOString(), generation.id, expectedRevision);
    return manifest;
  }

  diff(generation: GenerationRow, relativePathInput: string): GeneratedFileDiff {
    const relativePath = this.allowedPath(generation, relativePathInput);
    const stagedContent = readUtf8(this.stagedPath(generation, relativePath), generation.session_id);
    const targetPath = resolve(generation.target_root, relativePath);
    assertInside(generation.target_root, targetPath, generation.session_id);
    assertNoLinkInExistingPath(generation.target_root, targetPath, generation.session_id);
    const originalContent = existsSync(targetPath) ? readUtf8(targetPath, generation.session_id) : "";
    const manifestFile = parseManifest(generation).files.find((file) => file.relativePath === relativePath);
    if (!manifestFile) throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_GENERATED_FILE_NOT_FOUND", "generated file is not in the manifest", generation.session_id);
    const currentBase = existsSync(targetPath) ? sha256(readFileSync(targetPath)) : undefined;
    const stale = manifestFile.changeType === "ADD" ? existsSync(targetPath) : currentBase !== manifestFile.baseSha256;
    return {
      generationId: generation.id,
      generationRevision: generation.generation_revision,
      relativePath,
      changeType: manifestFile.changeType,
      baseSha256: manifestFile.baseSha256,
      stagedSha256: sha256(stagedContent),
      stale,
      originalContent,
      stagedContent,
      unifiedDiff: createTwoFilesPatch(`a/${relativePath}`, `b/${relativePath}`, originalContent, stagedContent, "base", "staged"),
    };
  }

  private write(generation: GenerationRow, relativePathInput: string, content: string): void {
    const relativePath = this.allowedPath(generation, relativePathInput);
    const bytes = Buffer.byteLength(content, "utf8");
    if (bytes > MAX_GENERATED_FILE_BYTES || content.includes("\u0000")) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_FILE_INVALID", "generated files must be UTF-8 text no larger than 1 MiB", generation.session_id);
    }
    const path = this.stagedPath(generation, relativePath);
    mkdirSync(dirname(path), { recursive: true });
    assertNoLinkInExistingPath(generation.staging_dir, path, generation.session_id);
    writeFileSync(path, content, "utf8");
  }

  private describe(generation: GenerationRow, relativePath: string): ArtifactFile {
    const stagedPath = this.stagedPath(generation, relativePath);
    const staged = readFileSync(stagedPath);
    decodeUtf8(staged, generation.session_id);
    const targetPath = resolve(generation.target_root, relativePath);
    assertInside(generation.target_root, targetPath, generation.session_id);
    assertNoLinkInExistingPath(generation.target_root, targetPath, generation.session_id);
    const exists = existsSync(targetPath);
    return {
      relativePath,
      changeType: exists ? "MODIFY" : "ADD",
      stagedSha256: sha256(staged),
      baseSha256: exists ? sha256(readFileSync(targetPath)) : undefined,
      sizeBytes: staged.length,
      validationStatus: "VALID",
      editedByUser: false,
    };
  }

  private allowedPath(generation: GenerationRow, input: string): string {
    const relativePath = normalizeRelativePath(input, generation.session_id);
    const contract = generationContract(generation);
    if (!contract.allowedOutputPatterns.some((pattern) => minimatch(relativePath, pattern, { dot: false, nocase: process.platform === "win32" }))) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_GENERATION_PATH_FORBIDDEN", "path is outside allowedOutputPatterns", generation.session_id);
    }
    if (!expectedFiles(generation).includes(relativePath)) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_GENERATION_PATH_FORBIDDEN", "path is outside the current business generation boundary", generation.session_id);
    }
    return relativePath;
  }

  private stagedPath(generation: GenerationRow, relativePath: string): string {
    const candidate = resolve(generation.staging_dir, relativePath);
    assertInside(generation.staging_dir, candidate, generation.session_id);
    return candidate;
  }
}

export function parseManifest(generation: GenerationRow): ArtifactManifest {
  try {
    const value = JSON.parse(generation.artifact_manifest_json) as ArtifactManifest;
    if (Array.isArray(value.files)) return value;
  } catch { /* return the empty manifest below */ }
  return { generationId: generation.id, targetRoot: generation.target_root, contractVersion: generation.target_contract_version, revision: 0, files: [] };
}

export function generationContract(generation: GenerationRow): GenerationTargetContract {
  return JSON.parse(generation.target_contract_json) as GenerationTargetContract;
}

function expectedFiles(generation: GenerationRow): string[] {
  const requirement = JSON.parse(generation.requirement_snapshot_json) as BusinessRequirement;
  return deriveGenerationSpec(requirement, generationContract(generation)).files;
}

function readUtf8(path: string, sessionId?: string): string {
  if (!existsSync(path) || !lstatSync(path).isFile()) {
    throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_GENERATED_FILE_NOT_FOUND", "generated file was not found", sessionId);
  }
  return decodeUtf8(readFileSync(path), sessionId);
}

function decodeUtf8(content: Buffer, sessionId?: string): string {
  try { return new TextDecoder("utf-8", { fatal: true }).decode(content); } catch {
    throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_FILE_INVALID", "generated file is not valid UTF-8", sessionId);
  }
}

function walk(root: string, current = root): string[] {
  const files: string[] = [];
  for (const entry of readdirSync(current, { withFileTypes: true })) {
    const path = resolve(current, entry.name);
    if (entry.isSymbolicLink()) continue;
    if (entry.isDirectory()) files.push(...walk(root, path));
    else if (entry.isFile()) files.push(path.slice(root.length + 1).replace(/\\/g, "/"));
  }
  return files;
}

function sameFiles(left: string[], right: string[]): boolean {
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

function stateError(generation: GenerationRow): AgentError {
  return new AgentError(HttpStatus.CONFLICT, "AGENT_GENERATION_STATE_CONFLICT", "generation state does not allow this operation", generation.session_id, { status: generation.status });
}
