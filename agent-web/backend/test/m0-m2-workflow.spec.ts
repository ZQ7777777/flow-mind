import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mkdtempSync, rmSync } from "node:fs";
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
    expect(calls.find((call) => call.method === "PUT")?.body.operationId).toMatch(/^op_save_/);
    expect(calls.every((call) => call.headers.get("X-Flow-User-Id") === "user_sales")).toBe(true);
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
