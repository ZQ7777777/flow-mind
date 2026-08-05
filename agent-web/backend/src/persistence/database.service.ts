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

export interface GenerationRow {
  id: string;
  session_id: string;
  process_definition_record_id: string;
  requirement_revision: number;
  requirement_snapshot_json: string;
  process_snapshot_json: string;
  business_code: string;
  business_name: string;
  status: string;
  target_root: string;
  target_contract_version: string;
  target_contract_json: string;
  staging_dir: string;
  backup_dir: string | null;
  artifact_manifest_json: string;
  pi_session_id: string | null;
  pi_session_file: string | null;
  generation_revision: number;
  quality_revision: number | null;
  repair_round: number;
  max_repair_rounds: number;
  latest_verification_run_id: string | null;
  latest_review_id: string | null;
  quality_report_json: string | null;
  hard_gate_passed: number;
  override_required: number;
  quality_override_id: string | null;
  skip_ai_review: number;
  can_write: number;
  write_status: string;
  write_journal_json: string | null;
  start_key: string | null;
  start_hash: string | null;
  start_result_json: string | null;
  superseded_by: string | null;
  created_by: string;
  confirmed_by: string | null;
  confirmed_at: string | null;
  write_confirm_key: string | null;
  write_confirm_hash: string | null;
  written_at: string | null;
  last_error_code: string | null;
  last_error_message: string | null;
  created_at: string;
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
    if (!applied.has(2)) {
      this.transaction(() => {
        const columns = this.db.prepare("PRAGMA table_info(agent_code_generation)").all() as Array<{ name: string }>;
        const names = new Set(columns.map(({ name }) => name));
        const additions: Array<[string, string]> = [
          ["process_snapshot_json", "TEXT NOT NULL DEFAULT '{}'"],
          ["target_contract_json", "TEXT NOT NULL DEFAULT '{}'"],
          ["start_key", "TEXT"],
          ["start_hash", "TEXT"],
          ["start_result_json", "TEXT"],
          ["superseded_by", "TEXT"],
        ];
        for (const [name, definition] of additions) {
          if (!names.has(name)) this.db.exec(`ALTER TABLE agent_code_generation ADD COLUMN ${name} ${definition}`);
        }
        this.db.exec(`
          CREATE INDEX IF NOT EXISTS idx_agent_generation_owner_created
            ON agent_code_generation(created_by, created_at);
          CREATE INDEX IF NOT EXISTS idx_agent_generation_business
            ON agent_code_generation(business_code, created_at);
          CREATE INDEX IF NOT EXISTS idx_agent_generation_session
            ON agent_code_generation(session_id, created_at);
          CREATE UNIQUE INDEX IF NOT EXISTS idx_agent_generation_start_key
            ON agent_code_generation(session_id, start_key) WHERE start_key IS NOT NULL;
          CREATE TABLE IF NOT EXISTS agent_compaction_stat (
            id TEXT PRIMARY KEY,
            pi_session_id TEXT NOT NULL,
            entry_id TEXT,
            reason TEXT NOT NULL,
            tokens_before INTEGER NOT NULL,
            summary_tokens INTEGER,
            duration_ms INTEGER NOT NULL,
            status TEXT NOT NULL,
            error_code TEXT,
            created_at TEXT NOT NULL
          );
          CREATE INDEX IF NOT EXISTS idx_agent_compaction_session_created
            ON agent_compaction_stat(pi_session_id, created_at);
        `);
        this.recordMigration(2);
      });
    }
    if (!applied.has(3)) {
      this.transaction(() => {
        const columns = this.db.prepare("PRAGMA table_info(agent_code_generation)").all() as Array<{ name: string }>;
        const names = new Set(columns.map(({ name }) => name));
        const additions: Array<[string, string]> = [
          ["process_snapshot_json", "TEXT NOT NULL DEFAULT '{}'"],
          ["target_contract_json", "TEXT NOT NULL DEFAULT '{}'"],
          ["start_key", "TEXT"],
          ["start_hash", "TEXT"],
          ["start_result_json", "TEXT"],
          ["superseded_by", "TEXT"],
        ];
        for (const [name, definition] of additions) {
          if (!names.has(name)) this.db.exec(`ALTER TABLE agent_code_generation ADD COLUMN ${name} ${definition}`);
        }

        // Early M3 builds used request_* and generator_pi_session_* for the same data.
        if (names.has("request_key")) {
          this.db.exec("UPDATE agent_code_generation SET start_key = request_key WHERE start_key IS NULL");
        }
        if (names.has("request_hash")) {
          this.db.exec("UPDATE agent_code_generation SET start_hash = request_hash WHERE start_hash IS NULL");
        }
        if (names.has("request_result_json")) {
          this.db.exec("UPDATE agent_code_generation SET start_result_json = request_result_json WHERE start_result_json IS NULL");
        }
        if (names.has("generator_pi_session_id")) {
          this.db.exec("UPDATE agent_code_generation SET pi_session_id = generator_pi_session_id WHERE pi_session_id IS NULL");
        }
        if (names.has("generator_pi_session_file")) {
          this.db.exec("UPDATE agent_code_generation SET pi_session_file = generator_pi_session_file WHERE pi_session_file IS NULL");
        }

        this.db.exec(`
          CREATE INDEX IF NOT EXISTS idx_agent_generation_owner_created
            ON agent_code_generation(created_by, created_at);
          CREATE INDEX IF NOT EXISTS idx_agent_generation_business
            ON agent_code_generation(business_code, created_at);
          CREATE INDEX IF NOT EXISTS idx_agent_generation_session
            ON agent_code_generation(session_id, created_at);
          CREATE UNIQUE INDEX IF NOT EXISTS idx_agent_generation_start_key
            ON agent_code_generation(session_id, start_key) WHERE start_key IS NOT NULL;
          CREATE TABLE IF NOT EXISTS agent_compaction_stat (
            id TEXT PRIMARY KEY,
            pi_session_id TEXT NOT NULL,
            entry_id TEXT,
            reason TEXT NOT NULL,
            tokens_before INTEGER NOT NULL,
            summary_tokens INTEGER,
            duration_ms INTEGER NOT NULL,
            status TEXT NOT NULL,
            error_code TEXT,
            created_at TEXT NOT NULL
          );
          CREATE INDEX IF NOT EXISTS idx_agent_compaction_session_created
            ON agent_compaction_stat(pi_session_id, created_at);
        `);
        this.recordMigration(3);
      });
    }
    if (!applied.has(4)) {
      this.transaction(() => {
        const compactionColumns = this.db.prepare("PRAGMA table_info(agent_compaction_stat)").all() as Array<{ name: string }>;
        const compactionNames = new Set(compactionColumns.map(({ name }) => name));
        const tokensBeforeSource = compactionNames.has("tokens_before")
          ? "tokens_before"
          : compactionNames.has("before_tokens") ? "before_tokens" : "NULL";

        this.db.exec("DROP INDEX IF EXISTS idx_agent_compaction_session_created");
        this.db.exec("ALTER TABLE agent_compaction_stat RENAME TO agent_compaction_stat_legacy");
        this.db.exec(`
          CREATE TABLE agent_compaction_stat (
            id TEXT PRIMARY KEY,
            pi_session_id TEXT NOT NULL,
            entry_id TEXT,
            reason TEXT NOT NULL,
            tokens_before INTEGER NOT NULL,
            summary_tokens INTEGER,
            duration_ms INTEGER NOT NULL,
            status TEXT NOT NULL,
            error_code TEXT,
            created_at TEXT NOT NULL
          );
          INSERT INTO agent_compaction_stat (
            id, pi_session_id, entry_id, reason, tokens_before, summary_tokens,
            duration_ms, status, error_code, created_at
          )
          SELECT id, pi_session_id, entry_id, reason, COALESCE(${tokensBeforeSource}, 0),
            summary_tokens, COALESCE(duration_ms, 0), status, error_code, created_at
          FROM agent_compaction_stat_legacy;
          DROP TABLE agent_compaction_stat_legacy;
          CREATE INDEX idx_agent_compaction_session_created
            ON agent_compaction_stat(pi_session_id, created_at);
        `);
        this.recordMigration(4);
      });
    }
    if (!applied.has(5)) {
      this.transaction(() => {
        const columns = this.db.prepare("PRAGMA table_info(agent_code_generation)").all() as Array<{ name: string }>;
        const names = new Set(columns.map(({ name }) => name));
        const additions: Array<[string, string]> = [
          ["quality_revision", "INTEGER"],
          ["repair_round", "INTEGER NOT NULL DEFAULT 0"],
          ["max_repair_rounds", "INTEGER NOT NULL DEFAULT 3"],
          ["latest_verification_run_id", "TEXT"],
          ["latest_review_id", "TEXT"],
          ["quality_report_json", "TEXT"],
          ["hard_gate_passed", "INTEGER NOT NULL DEFAULT 0"],
          ["override_required", "INTEGER NOT NULL DEFAULT 0"],
          ["quality_override_id", "TEXT"],
          ["can_write", "INTEGER NOT NULL DEFAULT 0"],
          ["write_status", "TEXT NOT NULL DEFAULT 'NOT_STARTED'"],
          ["write_journal_json", "TEXT"],
        ];
        for (const [name, definition] of additions) {
          if (!names.has(name)) {
            this.db.exec("ALTER TABLE agent_code_generation ADD COLUMN " + name + " " + definition);
          }
        }

        this.db.exec(`
          CREATE TABLE IF NOT EXISTS agent_verification_run (
            id TEXT PRIMARY KEY,
            generation_id TEXT NOT NULL REFERENCES agent_code_generation(id),
            revision INTEGER NOT NULL,
            repair_round INTEGER NOT NULL,
            trigger TEXT NOT NULL,
            status TEXT NOT NULL,
            hard_gate_passed INTEGER NOT NULL DEFAULT 0,
            soft_gate_passed INTEGER NOT NULL DEFAULT 0,
            stage_results_json TEXT NOT NULL DEFAULT '[]',
            log_dir TEXT,
            error_code TEXT,
            error_message TEXT,
            started_at TEXT NOT NULL,
            completed_at TEXT,
            created_at TEXT NOT NULL
          );
          CREATE INDEX IF NOT EXISTS idx_agent_verification_generation_revision
            ON agent_verification_run(generation_id, revision, created_at);

          CREATE TABLE IF NOT EXISTS agent_code_review (
            id TEXT PRIMARY KEY,
            generation_id TEXT NOT NULL REFERENCES agent_code_generation(id),
            verification_run_id TEXT REFERENCES agent_verification_run(id),
            revision INTEGER NOT NULL,
            status TEXT NOT NULL,
            verdict TEXT NOT NULL,
            summary TEXT NOT NULL DEFAULT '',
            issues_json TEXT NOT NULL DEFAULT '[]',
            pi_session_id TEXT,
            pi_session_file TEXT,
            error_code TEXT,
            error_message TEXT,
            created_at TEXT NOT NULL,
            completed_at TEXT
          );
          CREATE INDEX IF NOT EXISTS idx_agent_review_generation_revision
            ON agent_code_review(generation_id, revision, created_at);

          CREATE TABLE IF NOT EXISTS agent_quality_override (
            id TEXT PRIMARY KEY,
            generation_id TEXT NOT NULL REFERENCES agent_code_generation(id),
            revision INTEGER NOT NULL,
            scopes_json TEXT NOT NULL,
            reason TEXT NOT NULL CHECK(length(trim(reason)) >= 10),
            created_by TEXT NOT NULL,
            created_at TEXT NOT NULL,
            invalidated_at TEXT
          );
          CREATE INDEX IF NOT EXISTS idx_agent_override_generation_revision
            ON agent_quality_override(generation_id, revision, created_at);

          CREATE TABLE IF NOT EXISTS agent_artifact_write (
            id TEXT PRIMARY KEY,
            generation_id TEXT NOT NULL REFERENCES agent_code_generation(id),
            revision INTEGER NOT NULL,
            idempotency_key TEXT NOT NULL,
            request_hash TEXT NOT NULL,
            status TEXT NOT NULL,
            manifest_json TEXT NOT NULL,
            backup_dir TEXT NOT NULL,
            journal_json TEXT NOT NULL DEFAULT '[]',
            error_code TEXT,
            error_message TEXT,
            started_at TEXT NOT NULL,
            completed_at TEXT,
            UNIQUE(generation_id, idempotency_key)
          );
          CREATE INDEX IF NOT EXISTS idx_agent_artifact_write_generation
            ON agent_artifact_write(generation_id, started_at);
        `);
        this.recordMigration(5);
      });
    }
    if (!applied.has(6)) {
      this.transaction(() => {
        this.db.exec(`
          CREATE TABLE IF NOT EXISTS agent_generation_action (
            id TEXT PRIMARY KEY,
            generation_id TEXT NOT NULL REFERENCES agent_code_generation(id),
            action TEXT NOT NULL,
            idempotency_key TEXT NOT NULL,
            request_hash TEXT NOT NULL,
            result_json TEXT NOT NULL,
            created_at TEXT NOT NULL,
            UNIQUE(generation_id, action, idempotency_key)
          );
          CREATE INDEX IF NOT EXISTS idx_agent_generation_action
            ON agent_generation_action(generation_id, action, created_at);
        `);
        this.recordMigration(6);
      });
    }
    if (!applied.has(7)) {
      this.transaction(() => {
        const columns = this.db.prepare("PRAGMA table_info(agent_code_generation)").all() as Array<{ name: string }>;
        if (!columns.some(({ name }) => name === "skip_ai_review")) {
          this.db.exec("ALTER TABLE agent_code_generation ADD COLUMN skip_ai_review INTEGER NOT NULL DEFAULT 0");
        }
        this.recordMigration(7);
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

  getGeneration(id: string): GenerationRow | undefined {
    return this.db.prepare("SELECT * FROM agent_code_generation WHERE id = ?").get(id) as GenerationRow | undefined;
  }

  getLatestGenerationBySession(sessionId: string): GenerationRow | undefined {
    return this.db.prepare(
      "SELECT * FROM agent_code_generation WHERE session_id = ? ORDER BY created_at DESC LIMIT 1",
    ).get(sessionId) as GenerationRow | undefined;
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
    this.db.prepare(`
      UPDATE agent_code_generation SET
        status = CASE
          WHEN status = 'WRITING' THEN 'WRITE_FAILED'
          WHEN status IN ('VERIFYING', 'REVIEWING', 'REPAIRING') THEN 'REVIEW'
          ELSE 'FAILED'
        END,
        can_write = 0,
        write_status = CASE WHEN status = 'WRITING' THEN 'RECOVERY_REQUIRED' ELSE write_status END,
        last_error_code = 'AGENT_INTERRUPTED',
        last_error_message = 'Agent server restarted during an in-progress code operation.',
        updated_at = ?
      WHERE status IN ('GENERATING', 'VERIFYING', 'REVIEWING', 'REPAIRING', 'WRITING')
    `).run(now);
    this.db.prepare(`
      UPDATE agent_session SET
        state = CASE WHEN state = 'WRITING_ARTIFACTS' THEN 'ARTIFACT_WRITE_FAILED' ELSE 'CODE_PIPELINE_FAILED' END,
        row_version = row_version + 1,
        last_error_code = 'AGENT_INTERRUPTED',
        last_error_message = 'Agent server restarted during an in-progress code operation.',
        updated_at = ?
      WHERE state IN (
        'CODE_GENERATING', 'CODE_VERIFYING', 'CODE_REVIEWING', 'CODE_REPAIRING', 'WRITING_ARTIFACTS'
      )
    `).run(now);
    this.db.prepare(`
      UPDATE agent_verification_run
      SET status = 'INFRASTRUCTURE_FAILED', error_code = 'AGENT_INTERRUPTED',
        error_message = 'Agent server restarted during verification.', completed_at = ?
      WHERE status IN ('PENDING', 'RUNNING')
    `).run(now);
    this.db.prepare(`
      UPDATE agent_code_review
      SET status = 'INFRASTRUCTURE_FAILED', verdict = 'UNAVAILABLE',
        error_code = 'AGENT_INTERRUPTED',
        error_message = 'Agent server restarted during review.', completed_at = ?
      WHERE status IN ('PENDING', 'RUNNING')
    `).run(now);
  }

  onModuleDestroy(): void {
    this.db.close();
  }
}
