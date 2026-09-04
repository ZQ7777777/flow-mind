import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DatabaseService } from "../src/persistence/database.service.js";

describe("M4-M5 quality storage", () => {
  let root: string;
  let database: DatabaseService | undefined;

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), "flowmind-quality-db-"));
    process.env.AGENT_DATA_DIR = root;
    process.env.AGENT_DB_PATH = join(root, "agent.db");
    database = new DatabaseService();
  });

  afterEach(() => {
    database?.onModuleDestroy();
    rmSync(root, { recursive: true, force: true });
    delete process.env.AGENT_DB_PATH;
    delete process.env.AGENT_DATA_DIR;
  });

  it("creates quality history and artifact write-gate storage", () => {
    const columns = database!.db
      .prepare("PRAGMA table_info(agent_code_generation)")
      .all() as Array<{ name: string }>;
    expect(columns.map(({ name }) => name)).toEqual(
      expect.arrayContaining([
        "repair_round",
        "latest_verification_run_id",
        "latest_review_id",
        "hard_gate_passed",
        "override_required",
        "quality_override_id",
        "can_write",
        "write_status",
        "write_journal_json",
        "generation_strategy",
        "context_read_evidence_json",
      ]),
    );

    const tables = database!.db
      .prepare("SELECT name FROM sqlite_master WHERE type = 'table'")
      .all() as Array<{ name: string }>;
    expect(tables.map(({ name }) => name)).toEqual(
      expect.arrayContaining([
        "agent_verification_run",
        "agent_repair_attempt",
        "agent_code_review",
        "agent_quality_override",
        "agent_generation_action",
        "agent_rag_document",
        "agent_rag_retrieval",
        "agent_rag_read",
      ]),
    );
    const repairColumns = database!.db
      .prepare("PRAGMA table_info(agent_repair_attempt)")
      .all() as Array<{ name: string }>;
    expect(repairColumns.map(({ name }) => name)).toContain("failure_code");
  });
});
