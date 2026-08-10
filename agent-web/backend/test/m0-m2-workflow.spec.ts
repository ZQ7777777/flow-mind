import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { existsSync, mkdtempSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import type { MockUser, WorkflowSnapshot } from "@flowmind/agent-contracts";
import { DatabaseService } from "../src/persistence/database.service.js";
import { PiAdapterService } from "../src/pi/pi-adapter.service.js";
import { PlatformClientService } from "../src/platform/platform-client.service.js";
import { EventBusService } from "../src/workflow/event-bus.service.js";
import { WorkflowService } from "../src/workflow/workflow.service.js";

const user: MockUser = {
  userId: "user_sales",
  userName: "Sales User",
  departmentId: "sales_dept",
  departmentName: "Sales Department",
};

function taskActionRules(node: { listenerConfig?: Record<string, unknown> } | undefined): Record<string, unknown> {
  const rules = node?.listenerConfig?.taskActionRules;
  return rules && typeof rules === "object" && !Array.isArray(rules)
    ? rules as Record<string, unknown>
    : {};
}

describe("M0-M2 workflow", () => {
  let root: string;
  let database: DatabaseService;
  let workflow: WorkflowService;
  let definitionStatus = "DRAFT";
  let activationStatus = "INACTIVE";
  const calls: Array<{ method: string; path: string; body?: any; headers: Headers }> = [];

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), "flowmind-agent-"));
    process.env.AGENT_DATA_DIR = root;
    process.env.AGENT_DB_PATH = join(root, "agent.db");
    process.env.AGENT_FAKE_PI = "true";
    process.env.FLOW_PLATFORM_BASE_URL = "http://platform.test";
    definitionStatus = "DRAFT";
    activationStatus = "INACTIVE";
    calls.length = 0;
    vi.stubGlobal("fetch", async (input: string | URL | Request, init?: RequestInit) => {
      const url = new URL(String(input));
      const method = init?.method || "GET";
      const body = init?.body ? JSON.parse(String(init.body)) : undefined;
      calls.push({ method, path: `${url.pathname}${url.search}`, body, headers: new Headers(init?.headers) });
      let payload: any = {};
      if (url.pathname === "/api/platform/definitions" && method === "POST") {
        payload = { id: "definition-entry", version: 1 };
      } else if (url.pathname === "/api/platform/attachment-templates" && method === "GET") {
        payload = [];
      } else if (url.pathname === "/api/platform/attachment-templates" && method === "POST") {
        payload = { attachmentTemplateId: "tpl-bank-v1", templateVersion: 1, ...body };
      } else if (url.pathname.endsWith("/publish-validation")) {
        payload = { valid: true, issues: [] };
      } else if (url.pathname === "/api/platform/definitions/publish") {
        definitionStatus = "PUBLISHED";
        payload = { id: "definition-entry", definitionStatus };
      } else if (url.pathname === "/api/platform/definitions/activate") {
        activationStatus = "ACTIVE";
        payload = { id: "definition-entry", definitionStatus, activationStatus };
      } else if (url.pathname === "/api/platform/definitions/definition-entry") {
        payload = {
          id: "definition-entry",
          processCode: "entry_application",
          processName: "入金申请",
          version: 1,
          definitionStatus,
          activationStatus,
          nodes: [
            { nodeCode: "start", nodeName: "开始", nodeType: "START", positionX: 80, positionY: 120 },
            { nodeCode: "apply", nodeName: "申请", nodeType: "USER_TASK", positionX: 260, positionY: 120 },
            { nodeCode: "manager_approve", nodeName: "部门经理审批", nodeType: "USER_TASK", positionX: 460, positionY: 120 },
            { nodeCode: "finance_confirm", nodeName: "财务确认", nodeType: "USER_TASK", positionX: 680, positionY: 120 },
            { nodeCode: "end", nodeName: "结束", nodeType: "END", positionX: 880, positionY: 120 },
          ],
          edges: [
            { edgeCode: "e1", sourceNodeCode: "start", targetNodeCode: "apply" },
            { edgeCode: "e2", sourceNodeCode: "apply", targetNodeCode: "manager_approve" },
            { edgeCode: "e3", sourceNodeCode: "manager_approve", targetNodeCode: "finance_confirm" },
            { edgeCode: "e4", sourceNodeCode: "finance_confirm", targetNodeCode: "end" },
          ],
          formFields: [
            { fieldCode: "applicantName" },
            { fieldCode: "amount" },
            { fieldCode: "accountNo" },
          ],
          attachmentTemplates: [{ attachmentTemplateId: "tpl-bank-v1", attachmentCode: "bankReceipt", required: true }],
        };
      } else if (url.pathname === "/api/platform/definitions" && method === "GET") {
        payload = { items: [], total: 0 };
      }
      return new Response(JSON.stringify(payload), { status: 200, headers: { "Content-Type": "application/json" } });
    });
    database = new DatabaseService();
    const events = new EventBusService();
    const pi = new PiAdapterService(database);
    const platform = new PlatformClientService();
    workflow = new WorkflowService(database, pi, events, platform);
  });

  afterEach(() => {
    database.onModuleDestroy();
    vi.unstubAllGlobals();
    rmSync(root, { recursive: true, force: true });
    delete process.env.AGENT_DB_PATH;
    delete process.env.AGENT_DATA_DIR;
  });

  it("runs the entry application through process activation", async () => {
    let snapshot = await workflow.createSession(user);
    expect(snapshot.state).toBe("COLLECTING");

    await workflow.queueMessage(snapshot.sessionId, user, snapshot.rowVersion, "我要做一个入金申请流程");
    await waitFor(async () => (await workflow.getSnapshot(snapshot.sessionId, user)).messages.length >= 2);
    snapshot = await workflow.getSnapshot(snapshot.sessionId, user);
    await workflow.queueMessage(
      snapshot.sessionId,
      user,
      snapshot.rowVersion,
      "业务员提交，字段为姓名、金额、账号，上传银行回单，经理审批后财务确认。",
    );
    snapshot = await waitForState(snapshot.sessionId, "REQUIREMENT_REVIEW");
    expect(snapshot.requirement?.requirement.formFields).toHaveLength(3);
    expect(snapshot.requirement?.requirement.systemCode).toBe("FINANCE_SYS_001");
    expect(snapshot.requirement?.requirement.nodes.find((node) => node.nodeCode === "apply")?.timeoutConfig)
      .toMatchObject({ enabled: true, durationMinutes: 1440, action: "REMIND", severity: "MEDIUM" });

    const editedRequirement = structuredClone(snapshot.requirement!.requirement);
    editedRequirement.systemCode = "";
    for (const node of editedRequirement.nodes.filter((item) => item.nodeType === "USER_TASK")) {
      delete node.listenerConfig;
      delete node.timeoutConfig;
      delete node.reminderConfig;
    }
    await workflow.updateRequirement(snapshot.sessionId, user, snapshot.rowVersion, editedRequirement);
    snapshot = await workflow.getSnapshot(snapshot.sessionId, user);
    const normalizedApplyNode = snapshot.requirement!.requirement.nodes.find((node) => node.nodeCode === "apply");
    const normalizedManagerNode = snapshot.requirement!.requirement.nodes.find((node) => node.nodeCode === "manager_approve");
    const normalizedFinanceNode = snapshot.requirement!.requirement.nodes.find((node) => node.nodeCode === "finance_confirm");
    expect(snapshot.requirement?.requirement.systemCode).toBe("FINANCE_SYS_001");
    expect(normalizedApplyNode?.listenerConfig).toEqual({
      taskActionRules: {
        directSend: { enabled: true, targetMode: "REJECT_SOURCE" },
      },
    });
    expect(taskActionRules(normalizedManagerNode).reject).toEqual({
      enabled: true,
      targetNodeCodes: ["apply", "manager_approve", "finance_confirm"],
    });
    expect(taskActionRules(normalizedFinanceNode).reject).toEqual({
      enabled: true,
      targetNodeCodes: ["apply", "manager_approve", "finance_confirm"],
    });
    expect(normalizedApplyNode?.reminderConfig).toMatchObject({ enabled: true, maxCount: 2 });

    await workflow.confirmRequirement(
      snapshot.sessionId,
      user,
      snapshot.rowVersion,
      snapshot.requirement!.revision,
      "gate-one",
    );
    snapshot = await waitForState(snapshot.sessionId, "PROCESS_REVIEW");
    expect(snapshot.processPreview?.validation.valid).toBe(true);
    expect(snapshot.processPreview?.nodes).toHaveLength(5);

    await workflow.confirmProcess(
      snapshot.sessionId,
      user,
      snapshot.rowVersion,
      {
        platformDefinitionId: snapshot.processPreview!.platformDefinitionId,
        requirementRevision: snapshot.requirement!.revision,
      },
      "gate-two",
    );
    snapshot = await waitForState(snapshot.sessionId, "PROCESS_ACTIVE");
    expect(snapshot.processPreview?.definitionStatus).toBe("PUBLISHED");
    expect(snapshot.processPreview?.activationStatus).toBe("ACTIVE");

    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual(expect.arrayContaining([
      "POST /api/platform/definitions",
      "GET /api/platform/attachment-templates?attachmentCode=bankReceipt&templateStatus=ENABLED",
      "POST /api/platform/attachment-templates",
      "PUT /api/platform/definitions/definition-entry/graph",
      "POST /api/platform/definitions/publish",
      "POST /api/platform/definitions/activate",
    ]));
    const graphSave = calls.find((call) => call.method === "PUT");
    expect(graphSave?.body.operationId).toMatch(/^op_save_/);
    const applyNode = graphSave?.body.nodes.find((node: any) => node.nodeCode === "apply");
    const managerNode = graphSave?.body.nodes.find((node: any) => node.nodeCode === "manager_approve");
    const financeNode = graphSave?.body.nodes.find((node: any) => node.nodeCode === "finance_confirm");
    expect(JSON.parse(applyNode.listenerConfig)).toEqual({
      taskActionRules: {
        directSend: { enabled: true, targetMode: "REJECT_SOURCE" },
      },
    });
    expect(JSON.parse(managerNode.listenerConfig).taskActionRules.reject).toEqual({
      enabled: true,
      targetNodeCodes: ["apply", "manager_approve", "finance_confirm"],
    });
    expect(JSON.parse(financeNode.listenerConfig).taskActionRules.reject).toEqual({
      enabled: true,
      targetNodeCodes: ["apply", "manager_approve", "finance_confirm"],
    });
    expect(JSON.parse(applyNode.timeoutConfig)).toEqual({
      action: "REMIND", durationMinutes: 1440, enabled: true, severity: "MEDIUM",
    });
    expect(JSON.parse(applyNode.reminderConfig)).toEqual({
      enabled: true, maxCount: 2, messageTemplate: "您有待办，请及时处理。",
    });
    expect(calls.every((call) => call.headers.get("X-Flow-User-Id") === "user_sales")).toBe(true);
  });

  it("resets an active workflow into a fresh Pi conversation", async () => {
    const created = await workflow.createSession(user);
    const before = database.getSession(created.sessionId)!;
    const previousSessionFile = before.pi_session_file!;
    database.db.prepare("UPDATE agent_session SET state = 'PROCESS_ACTIVE' WHERE id = ?").run(created.sessionId);

    const reset = await workflow.resetSession(created.sessionId, user, created.rowVersion);
    const after = database.getSession(created.sessionId)!;

    expect(reset.state).toBe("COLLECTING");
    expect(reset.messages).toEqual([]);
    expect(reset.requirement).toBeUndefined();
    expect(reset.processPreview).toBeUndefined();
    expect(after.pi_session_id).not.toBe(before.pi_session_id);
    expect(after.pi_session_file).not.toBe(previousSessionFile);
    expect(existsSync(previousSessionFile)).toBe(false);
    await workflow.queueMessage(reset.sessionId, user, reset.rowVersion, "重新开始收集需求");
    await waitFor(async () => existsSync(after.pi_session_file!));
  });

  it("resets a completed workflow while preserving its published artifacts as history", async () => {
    const created = await workflow.createSession(user);
    const before = database.getSession(created.sessionId)!;
    const previousSessionFile = before.pi_session_file!;
    const now = new Date().toISOString();
    database.db.prepare(`
      INSERT INTO agent_process_definition (
        id, session_id, requirement_revision, platform_definition_id,
        process_code, process_name, status, saga_step, requirement_snapshot_json,
        create_operation_id, save_operation_id, publish_operation_id, activate_operation_id,
        created_by, created_at, activated_at, updated_at
      ) VALUES (?, ?, 1, 'definition-completed', 'completed_flow', 'Completed Flow',
        'ACTIVE', 'ACTIVE', '{}', 'op-create', 'op-save', 'op-publish', 'op-activate', ?, ?, ?, ?)
    `).run("apd_completed", created.sessionId, user.userId, now, now, now);
    database.db.prepare(`
      INSERT INTO agent_code_generation (
        id, session_id, process_definition_record_id, requirement_revision,
        requirement_snapshot_json, business_code, business_name, status,
        artifact_manifest_json, generation_revision, created_by, created_at, updated_at,
        process_snapshot_json, target_contract_json, hard_gate_passed, can_write, write_status
      ) VALUES (?, ?, ?, 1, '{}', 'completed_flow', 'Completed Flow', 'COMPLETED',
        '{}', 1, ?, ?, ?, '{}', '{}', 1, 1, 'COMPLETED')
    `).run("acg_completed", created.sessionId, "apd_completed", user.userId, now, now);
    database.db.prepare("UPDATE agent_session SET state = 'COMPLETED' WHERE id = ?").run(created.sessionId);

    const completed = await workflow.getSnapshot(created.sessionId, user);
    expect(completed.allowedActions).toContain("RESET_SESSION");

    const reset = await workflow.resetSession(created.sessionId, user, created.rowVersion);
    const after = database.getSession(created.sessionId)!;

    expect(reset).toMatchObject({ state: "COLLECTING", messages: [] });
    expect(reset.requirement).toBeUndefined();
    expect(reset.processPreview).toBeUndefined();
    expect(reset.activeGeneration).toBeUndefined();
    expect(after.pi_session_id).not.toBe(before.pi_session_id);
    expect(after.pi_session_file).not.toBe(previousSessionFile);
    expect(existsSync(previousSessionFile)).toBe(false);
    expect(database.db.prepare("SELECT status FROM agent_process_definition WHERE id = ?").get("apd_completed"))
      .toEqual({ status: "ACTIVE" });
    expect(database.db.prepare("SELECT status FROM agent_code_generation WHERE id = ?").get("acg_completed"))
      .toEqual({ status: "COMPLETED" });
  });

  async function waitForState(sessionId: string, state: string): Promise<WorkflowSnapshot> {
    return waitFor(async () => {
      const current = await workflow.getSnapshot(sessionId, user);
      return current.state === state ? current : undefined;
    });
  }
});

async function waitFor<T>(operation: () => Promise<T | undefined | false>, timeoutMs = 3000): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const result = await operation();
    if (result) return result;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error("timed out waiting for workflow state");
}
