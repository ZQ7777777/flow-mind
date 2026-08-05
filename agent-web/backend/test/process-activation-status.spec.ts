import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { mkdtempSync, rmSync } from "node:fs";
import type { MockUser } from "@flowmind/agent-contracts";
import { DatabaseService } from "../src/persistence/database.service.js";
import { PiAdapterService } from "../src/pi/pi-adapter.service.js";
import { PlatformClientService } from "../src/platform/platform-client.service.js";
import { EventBusService } from "../src/workflow/event-bus.service.js";
import { WorkflowService } from "../src/workflow/workflow.service.js";
import { GenerationService } from "../src/generation/generation.service.js";

const user: MockUser = {
  userId: "user_sales",
  userName: "Sales User",
  departmentId: "sales_dept",
  departmentName: "Sales Department",
};

describe("process activation status", () => {
  let root: string;
  let database: DatabaseService;
  let workflow: WorkflowService;
  let platform: PlatformClientService;

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), "flowmind-agent-"));
    process.env.AGENT_DATA_DIR = root;
    process.env.AGENT_DB_PATH = join(root, "agent.db");
    process.env.FLOW_PLATFORM_BASE_URL = "http://platform.test";
    vi.stubGlobal("fetch", async (input: string | URL | Request) => {
      const url = new URL(String(input));
      if (url.pathname.endsWith("/publish-validation")) {
        return json({ valid: true, issues: [] });
      }
      if (url.pathname === "/api/platform/definitions/activate") return json({});
      const definitionId = url.pathname.split("/").at(-1);
      return json({
        id: definitionId,
        processCode: "entry_application",
        processName: "入金申请",
        version: definitionId === "definition-current" ? 2 : 1,
        definitionStatus: "PUBLISHED",
        activationStatus: definitionId === "definition-current" ? "ACTIVE" : "INACTIVE",
      });
    });
    database = new DatabaseService();
    platform = new PlatformClientService();
    workflow = new WorkflowService(database, new PiAdapterService(database), new EventBusService(), platform);
  });

  afterEach(() => {
    database.onModuleDestroy();
    vi.unstubAllGlobals();
    rmSync(root, { recursive: true, force: true });
    delete process.env.AGENT_DB_PATH;
    delete process.env.AGENT_DATA_DIR;
  });

  it("supersedes the old local record and displays the platform activation status", async () => {
    const now = new Date().toISOString();
    const old = new Date(Date.now() - 60_000).toISOString();
    database.db.prepare(`
      INSERT INTO agent_session (
        id, owner_user_id, owner_user_name, state, row_version, requirement_revision, created_at, updated_at
      ) VALUES ('session-1', ?, ?, 'PROCESS_ACTIVE', 0, 1, ?, ?)
    `).run(user.userId, user.userName, now, now);
    insertProcess(database, "previous-active-definition", "definition-previous", "ACTIVE", old, old);
    insertProcess(database, "current-definition", "definition-current", "PUBLISHED", now, null);

    await (workflow as any).activate("session-1", user);

    expect(database.db.prepare("SELECT status, saga_step FROM agent_process_definition WHERE id = ?")
      .get("previous-active-definition")).toEqual({ status: "SUPERSEDED", saga_step: "SUPERSEDED" });
    expect(database.db.prepare("SELECT status FROM agent_process_definition WHERE id = ?")
      .get("current-definition")).toEqual({ status: "ACTIVE" });

    const definitions = await new GenerationService(
      database, undefined as any, undefined as any, undefined as any, undefined as any,
      undefined, undefined, platform,
    ).listDefinitions(user);
    expect(definitions.find((definition) => definition.id === "previous-active-definition"))
      .toMatchObject({ status: "SUPERSEDED", activationStatus: "INACTIVE" });
    expect(definitions.find((definition) => definition.id === "current-definition"))
      .toMatchObject({ status: "ACTIVE", activationStatus: "ACTIVE", definitionVersion: 2 });
  });
});

function insertProcess(
  database: DatabaseService,
  id: string,
  platformDefinitionId: string,
  status: string,
  createdAt: string,
  activatedAt: string | null,
): void {
  database.db.prepare(`
    INSERT INTO agent_process_definition (
      id, session_id, requirement_revision, platform_definition_id, process_code, process_name,
      status, saga_step, requirement_snapshot_json, create_operation_id, save_operation_id,
      publish_operation_id, activate_operation_id, created_by, created_at, activated_at, updated_at
    ) VALUES (?, 'session-1', 1, ?, 'entry_application', '入金申请', ?, ?, '{}', ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    id, platformDefinitionId, status, status, `create-${id}`, `save-${id}`, `publish-${id}`, `activate-${id}`,
    user.userId, createdAt, activatedAt, createdAt,
  );
}

function json(payload: unknown): Response {
  return new Response(JSON.stringify(payload), { status: 200, headers: { "Content-Type": "application/json" } });
}
