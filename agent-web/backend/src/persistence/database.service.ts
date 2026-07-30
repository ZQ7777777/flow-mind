import { Injectable, OnModuleDestroy } from "@nestjs/common";
import Database from "better-sqlite3";
import { mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import type { WorkflowState } from "@flowmind/agent-contracts";
import { loadConfig } from "../config.js";

export interface SessionRow {
  id: string;
  owner_user_id: string;
  owner_user_name: string;
  owner_dept_id: string | null;
  owner_dept_name: string | null;
  target_root: string | null;
  state: WorkflowState;
  row_version: number;
  requirement_revision: number;
  requirement_json: string | null;
  requirement_missing_items_json: string;
  requirement_ambiguities_json: string;
  requirement_ready_for_review: number;
  requirement_source: string | null;
  requirement_confirmed_at: string | null;
  requirement_confirm_key: string | null;
  requirement_confirm_hash: string | null;
  requirement_confirm_result_json: string | null;
  pi_session_id: string | null;
  pi_session_file: string | null;
  last_error_code: string | null;
  last_error_message: string | null;
  created_at: string;
  updated_at: string;
}

export interface ProcessRow {
  id: string;
  session_id: string;
  requirement_revision: number;
  platform_definition_id: string | null;
  process_code: string;
  process_name: string;
  definition_version: number | null;
  status: string;
  saga_step: string;
  requirement_snapshot_json: string;
  validation_json: string | null;
  platform_snapshot_json: string | null;
  create_operation_id: string;
  save_operation_id: string;
  publish_operation_id: string;
  activate_operation_id: string;
  process_confirm_key: string | null;
  process_confirm_hash: string | null;
  process_confirm_result_json: string | null;
  retry_key: string | null;
  retry_hash: string | null;
  retry_result_json: string | null;
  last_error_code: string | null;
  last_error_message: string | null;
  created_by: string;
  created_at: string;
  activated_at: string | null;
  updated_at: string;
}

@Injectable()
export class DatabaseService implements OnModuleDestroy {
  readonly db: Database.Database;
  readonly dataDir: string;

  constructor() {
    const config = loadConfig();
    this.dataDir = config.dataDir;
    mkdirSync(this.dataDir, { recursive: true });
    mkdirSync(join(this.dataDir, "pi-sessions"), { recursive: true });
    mkdirSync(join(this.dataDir, "controlled-cwd"), { recursive: true });
    const dbPath = process.env.AGENT_DB_PATH || join(this.dataDir, "agent.db");
    mkdirSync(dirname(dbPath), { recursive: true });
    this.db = new Database(dbPath);
    this.db.pragma("journal_mode = WAL");
    this.db.pragma("foreign_keys = ON");
    this.db.pragma("busy_timeout = 5000");
    this.migrate();
    this.recoverInterruptedStates();
  }

  migrate(): void {
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS agent_schema_migration (
        version INTEGER PRIMARY KEY,
        applied_at TEXT NOT NULL
      );
    `);
    const applied = new Set(
      (this.db.prepare("SELECT version FROM agent_schema_migration").all() as Array<{ version: number }>)
        .map(({ version }) => version),
    );
    if (!applied.has(1)) {
      this.transaction(() => {
        this.db.exec(`
      CREATE TABLE IF NOT EXISTS agent_session (
        id TEXT PRIMARY KEY,
        owner_user_id TEXT NOT NULL,
        owner_user_name TEXT NOT NULL,
        owner_dept_id TEXT,
        owner_dept_name TEXT,
        target_root TEXT,
        state TEXT NOT NULL,
        row_version INTEGER NOT NULL DEFAULT 0,
        requirement_revision INTEGER NOT NULL DEFAULT 0,
        requirement_json TEXT,
        requirement_missing_items_json TEXT NOT NULL DEFAULT '[]',
        requirement_ambiguities_json TEXT NOT NULL DEFAULT '[]',
        requirement_ready_for_review INTEGER NOT NULL DEFAULT 0,
        requirement_source TEXT,
        requirement_confirmed_at TEXT,
        requirement_confirm_key TEXT,
        requirement_confirm_hash TEXT,
        requirement_confirm_result_json TEXT,
        pi_session_id TEXT,
        pi_session_file TEXT,
        last_error_code TEXT,
        last_error_message TEXT,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_agent_session_owner_updated
        ON agent_session(owner_user_id, updated_at);

      CREATE TABLE IF NOT EXISTS agent_process_definition (
        id TEXT PRIMARY KEY,
        session_id TEXT NOT NULL REFERENCES agent_session(id),
        requirement_revision INTEGER NOT NULL,
        platform_definition_id TEXT UNIQUE,
        process_code TEXT NOT NULL,
        process_name TEXT NOT NULL,
        definition_version INTEGER,
        status TEXT NOT NULL,
        saga_step TEXT NOT NULL,
        requirement_snapshot_json TEXT NOT NULL,
        validation_json TEXT,
        platform_snapshot_json TEXT,
        create_operation_id TEXT NOT NULL UNIQUE,
        save_operation_id TEXT NOT NULL UNIQUE,
        publish_operation_id TEXT NOT NULL,
        activate_operation_id TEXT NOT NULL,
        process_confirm_key TEXT,
        process_confirm_hash TEXT,
        process_confirm_result_json TEXT,
        retry_key TEXT,
        retry_hash TEXT,
        retry_result_json TEXT,
        last_error_code TEXT,
        last_error_message TEXT,
        created_by TEXT NOT NULL,
        created_at TEXT NOT NULL,
        activated_at TEXT,
        updated_at TEXT NOT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_agent_definition_owner_created
        ON agent_process_definition(created_by, created_at);
      CREATE INDEX IF NOT EXISTS idx_agent_definition_process
        ON agent_process_definition(process_code, definition_version);

      CREATE TABLE IF NOT EXISTS agent_code_generation (
        id TEXT PRIMARY KEY,
        session_id TEXT NOT NULL REFERENCES agent_session(id),
        process_definition_record_id TEXT NOT NULL REFERENCES agent_process_definition(id),
        requirement_revision INTEGER NOT NULL,
        requirement_snapshot_json TEXT NOT NULL,
        business_code TEXT NOT NULL,
        business_name TEXT NOT NULL,
        status TEXT NOT NULL,
        target_root TEXT,
        target_contract_version TEXT,
        staging_dir TEXT,
        backup_dir TEXT,
        artifact_manifest_json TEXT NOT NULL DEFAULT '{}',
        pi_session_id TEXT,
        pi_session_file TEXT,
        generation_revision INTEGER NOT NULL DEFAULT 0,
        created_by TEXT NOT NULL,
        confirmed_by TEXT,
        confirmed_at TEXT,
        write_confirm_key TEXT,
        write_confirm_hash TEXT,
        written_at TEXT,
        last_error_code TEXT,
        last_error_message TEXT,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL
      );
        `);
        this.recordMigration(1);
      });
    }
  }

  private recordMigration(version: number): void {
    this.db.prepare(
      "INSERT OR IGNORE INTO agent_schema_migration(version, applied_at) VALUES (?, ?)",
    ).run(version, new Date().toISOString());
  }

  transaction<T>(operation: () => T): T {
    return this.db.transaction(operation)();
  }

  getSession(id: string): SessionRow | undefined {
    return this.db.prepare("SELECT * FROM agent_session WHERE id = ?").get(id) as SessionRow | undefined;
  }

  getProcessBySession(sessionId: string): ProcessRow | undefined {
    return this.db.prepare(
      "SELECT * FROM agent_process_definition WHERE session_id = ? ORDER BY created_at DESC LIMIT 1",
    ).get(sessionId) as ProcessRow | undefined;
  }

  recoverInterruptedStates(): void {
    const now = new Date().toISOString();
    this.db.prepare(`
      UPDATE agent_session
      SET state = CASE
        WHEN state = 'PROCESS_PROVISIONING' THEN 'PROCESS_PROVISION_FAILED'
        ELSE 'PROCESS_ACTIVATION_FAILED'
      END,
      row_version = row_version + 1,
      last_error_code = 'AGENT_INTERRUPTED',
      last_error_message = 'Agent server restarted during an in-progress operation; retry is required.',
      updated_at = ?
      WHERE state IN ('PROCESS_PROVISIONING', 'PROCESS_ACTIVATING')
    `).run(now);
  }

  onModuleDestroy(): void {
    this.db.close();
  }
}
