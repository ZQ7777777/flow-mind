import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdirSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Database from "better-sqlite3";
import { DatabaseService } from "../src/persistence/database.service.js";

describe("agent database", () => {
  let root: string;
  let database: DatabaseService | undefined;

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), "flowmind-agent-db-"));
    process.env.AGENT_DATA_DIR = root;
    process.env.AGENT_DB_PATH = join(root, "agent.db");
    database = new DatabaseService();
  });

  afterEach(() => {
    database?.onModuleDestroy();
    database = undefined;
    rmSync(root, { recursive: true, force: true });
    delete process.env.AGENT_DB_PATH;
    delete process.env.AGENT_DATA_DIR;
  });

  it("applies versioned migrations and required SQLite pragmas", () => {
    const versions = database!.db
      .prepare("SELECT version FROM agent_schema_migration ORDER BY version")
      .all() as Array<{ version: number }>;
    expect(versions.map(({ version }) => version)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]);
    expect((database!.db.prepare("PRAGMA table_info(agent_code_generation)").all() as Array<{ name: string }>).map(({ name }) => name))
      .toContain("generation_context_snapshot_json");
    expect(database!.db.pragma("journal_mode", { simple: true })).toBe("wal");
    expect(database!.db.pragma("foreign_keys", { simple: true })).toBe(1);
    expect(database!.db.pragma("busy_timeout", { simple: true })).toBe(5000);
  });

  it("moves interrupted work to an explicit retry state on restart", () => {
    const now = new Date().toISOString();
    database!.db.prepare(`
      INSERT INTO agent_session (
        id, owner_user_id, owner_user_name, state, row_version, created_at, updated_at
      ) VALUES ('ags_interrupted', 'user_sales', 'Sales User', 'PROCESS_PROVISIONING', 4, ?, ?)
    `).run(now, now);
    database!.onModuleDestroy();

    database = new DatabaseService();
    const recovered = database.getSession("ags_interrupted");
    expect(recovered?.state).toBe("PROCESS_PROVISION_FAILED");
    expect(recovered?.row_version).toBe(5);
    expect(recovered?.last_error_code).toBe("AGENT_INTERRUPTED");
  });

  it("repairs an early M3 database that already recorded migration 2", () => {
    database!.onModuleDestroy();
    database = undefined;
    rmSync(root, { recursive: true, force: true });
    mkdirSync(root, { recursive: true });

    const legacy = new Database(join(root, "agent.db"));
    legacy.exec(`
      CREATE TABLE agent_schema_migration (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL);
      INSERT INTO agent_schema_migration VALUES (1, '2026-08-01'), (2, '2026-08-02');
      CREATE TABLE agent_session (
        id TEXT PRIMARY KEY, state TEXT NOT NULL, row_version INTEGER NOT NULL,
        last_error_code TEXT, last_error_message TEXT, updated_at TEXT NOT NULL
      );
      CREATE TABLE agent_code_generation (
        id TEXT PRIMARY KEY, session_id TEXT NOT NULL, business_code TEXT NOT NULL,
        created_by TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
        status TEXT NOT NULL, pi_session_id TEXT, pi_session_file TEXT,
        last_error_code TEXT, last_error_message TEXT,
        request_key TEXT, request_hash TEXT, request_result_json TEXT,
        generator_pi_session_id TEXT, generator_pi_session_file TEXT
      );
      CREATE TABLE agent_compaction_stat (
        id TEXT PRIMARY KEY, workflow_session_id TEXT NOT NULL,
        pi_session_id TEXT NOT NULL, pi_session_kind TEXT NOT NULL,
        entry_id TEXT, reason TEXT NOT NULL, before_tokens INTEGER,
        summary_tokens INTEGER, duration_ms INTEGER, status TEXT NOT NULL,
        error_code TEXT, created_at TEXT NOT NULL
      );
      INSERT INTO agent_compaction_stat VALUES (
        'acs_legacy', 'ags_legacy', 'pi_legacy', 'GENERATOR', NULL,
        'threshold', 12345, 800, 50, 'SUCCESS', NULL, '2026-08-02'
      );
      INSERT INTO agent_code_generation (
        id, session_id, business_code, created_by, created_at, updated_at, status,
        request_key, request_hash, request_result_json,
        generator_pi_session_id, generator_pi_session_file
      ) VALUES (
        'gen_legacy', 'ags_legacy', 'entry_application', 'user_sales',
        '2026-08-02', '2026-08-02', 'REVIEW', 'legacy-key', 'legacy-hash', '{}',
        'pi_legacy', 'legacy.jsonl'
      );
    `);
    legacy.close();

    database = new DatabaseService();
    const columns = database.db.prepare("PRAGMA table_info(agent_code_generation)").all() as Array<{ name: string }>;
    expect(columns.map(({ name }) => name)).toContain("start_key");
    expect(database.db.prepare(`
      SELECT start_key, start_hash, pi_session_id, pi_session_file
      FROM agent_code_generation WHERE id = 'gen_legacy'
    `).get()).toEqual({
      start_key: "legacy-key",
      start_hash: "legacy-hash",
      pi_session_id: "pi_legacy",
      pi_session_file: "legacy.jsonl",
    });
    const versions = database.db.prepare("SELECT version FROM agent_schema_migration ORDER BY version").all() as Array<{ version: number }>;
    expect(versions.map(({ version }) => version)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]);
    expect(database.db.prepare("SELECT tokens_before, summary_tokens FROM agent_compaction_stat WHERE id = 'acs_legacy'").get())
      .toEqual({ tokens_before: 12345, summary_tokens: 800 });
  });

  it("adds repair-attempt history to an existing version seven database", () => {
    database!.db.exec("DROP TABLE agent_repair_attempt; DELETE FROM agent_schema_migration WHERE version = 8;");
    database!.onModuleDestroy();
    database = new DatabaseService();

    expect(database.db.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'agent_repair_attempt'").get())
      .toEqual({ name: "agent_repair_attempt" });
    expect(database.db.prepare("SELECT version FROM agent_schema_migration WHERE version = 8").get())
      .toEqual({ version: 8 });
  });

  it("adds repair failure codes to an existing repair-attempt table", () => {
    database!.db.exec("ALTER TABLE agent_repair_attempt RENAME TO agent_repair_attempt_v8;");
    database!.db.exec(`
      CREATE TABLE agent_repair_attempt AS
      SELECT id, generation_id, verification_run_id, round, diagnostic_ids_json,
        changed_files_json, resolutions_json, outcome, created_at
      FROM agent_repair_attempt_v8;
      DROP TABLE agent_repair_attempt_v8;
      DELETE FROM agent_schema_migration WHERE version = 9;
    `);
    database!.onModuleDestroy();
    database = new DatabaseService();

    const columns = database.db.prepare("PRAGMA table_info(agent_repair_attempt)").all() as Array<{ name: string }>;
    expect(columns.map(({ name }) => name)).toContain("failure_code");
  });
});
