import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
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
    expect(versions.map(({ version }) => version)).toEqual([1]);
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
});
