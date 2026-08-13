import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdtempSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { DatabaseService } from "../src/persistence/database.service.js";
import { WorkflowService } from "../src/workflow/workflow.service.js";

describe("owner session history", () => {
  let parent: string;
  let database: DatabaseService;
  let workflow: WorkflowService;

  beforeEach(() => {
    parent = mkdtempSync(join(tmpdir(), "flowmind-session-history-"));
    process.env.AGENT_DATA_DIR = join(parent, "data");
    process.env.AGENT_DB_PATH = join(parent, "agent.db");
    database = new DatabaseService();
    workflow = new WorkflowService(database, {} as never, {} as never, {} as never);
  });

  afterEach(() => {
    database.onModuleDestroy();
    rmSync(parent, { recursive: true, force: true });
    delete process.env.AGENT_DATA_DIR;
    delete process.env.AGENT_DB_PATH;
  });

  it("returns every session owned by the current user in latest-updated order", () => {
    insertSession("session-old", "owner-1", "COLLECTING", "2026-08-10T00:00:00.000Z", {
      businessName: "旧需求",
    });
    insertSession("session-completed", "owner-1", "COMPLETED", "2026-08-12T00:00:00.000Z", {
      businessName: "已完成需求",
    });
    insertSession("session-other", "owner-2", "CODE_PIPELINE_FAILED", "2026-08-13T00:00:00.000Z", {
      businessName: "其他用户需求",
    });

    const result = workflow.listSessions({ userId: "owner-1", userName: "Owner 1" });

    expect(result.map(({ sessionId }) => sessionId)).toEqual(["session-completed", "session-old"]);
    expect(result[0]).toMatchObject({ businessName: "已完成需求", state: "COMPLETED" });
  });

  it("keeps malformed or missing requirement snapshots queryable", () => {
    insertSession("session-empty", "owner-1", "COLLECTING", "2026-08-10T00:00:00.000Z");
    insertSession("session-invalid", "owner-1", "REQUIREMENT_REVIEW", "2026-08-11T00:00:00.000Z");
    database.db.prepare("UPDATE agent_session SET requirement_json = ? WHERE id = ?")
      .run("{invalid", "session-invalid");

    const result = workflow.listSessions({ userId: "owner-1", userName: "Owner 1" });

    expect(result).toHaveLength(2);
    expect(result.every(({ businessName }) => businessName === "未命名需求")).toBe(true);
  });

  function insertSession(
    id: string,
    owner: string,
    state: string,
    updatedAt: string,
    requirement?: Record<string, unknown>,
  ): void {
    database.db.prepare(`
      INSERT INTO agent_session (
        id, owner_user_id, owner_user_name, state, row_version,
        requirement_json, created_at, updated_at
      ) VALUES (?, ?, ?, ?, 0, ?, ?, ?)
    `).run(id, owner, owner, state, requirement ? JSON.stringify(requirement) : null, updatedAt, updatedAt);
  }
});
