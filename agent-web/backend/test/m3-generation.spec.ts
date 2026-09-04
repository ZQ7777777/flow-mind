import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { ENTRY_APPLICATION_REQUIREMENT, type BusinessRequirement, type GenerationTargetContract } from "@flowmind/agent-contracts";
import { DatabaseService } from "../src/persistence/database.service.js";
import { AgentError } from "../src/common/agent-error.js";
import { EventBusService } from "../src/workflow/event-bus.service.js";
import { PiAdapterService } from "../src/pi/pi-adapter.service.js";
import { deriveGenerationSpec } from "../src/generation/generation-spec.js";
import { TargetContractService } from "../src/generation/target-contract.service.js";
import { StagingService } from "../src/generation/staging.service.js";
import { GenerationService } from "../src/generation/generation.service.js";
import { createGenerationTarget, seedActiveWorkflow } from "./generation-fixture.js";

describe("M3 user-defined business generation", () => {
  let parent: string;
  let database: DatabaseService;
  let generation: GenerationService;
  const user = { userId: "user_sales", userName: "Sales User", departmentId: "sales_dept" };

  beforeEach(() => {
    parent = mkdtempSync(join(tmpdir(), "flowmind-m3-"));
    process.env.AGENT_DATA_DIR = join(parent, "data");
    process.env.AGENT_DB_PATH = join(parent, "agent.db");
    process.env.AGENT_ALLOWED_TARGET_ROOTS = parent;
    process.env.AGENT_FAKE_PI = "true";
    database = new DatabaseService();
    const events = new EventBusService();
    const pi = new PiAdapterService(database);
    const targets = new TargetContractService();
    const staging = new StagingService(database);
    generation = new GenerationService(database, targets, staging, pi, events);
  });

  afterEach(() => {
    database.onModuleDestroy();
    rmSync(parent, { recursive: true, force: true });
    delete process.env.AGENT_DATA_DIR; delete process.env.AGENT_DB_PATH;
    delete process.env.AGENT_ALLOWED_TARGET_ROOTS; delete process.env.AGENT_FAKE_PI;
  });

  it("derives files, fields and apply attachments from a confirmed custom process", async () => {
    const target = createGenerationTarget(parent);
    const requirement = travelExpenseRequirement();
    seedActiveWorkflow(database, "session-m3", null, "user_sales", requirement);
    const spec = deriveGenerationSpec(requirement, readContract(target));

    const started = generation.start("session-m3", user, 0, "start-m3", target);
    const first = await waitForReview(started.generationId);
    expect(first.manifest?.files.map((file) => file.relativePath).sort()).toEqual([...spec.files].sort());
    expect(first.manifest?.files).toHaveLength(5);
    expect(first.generationRevision).toBe(1);
    expect(first.backendRestartRequired).toBe(false);
    expect(first.context?.sha256).toMatch(/^[a-f0-9]{64}$/);
    expect(first.context?.routing?.capabilities).toContain("BASE_FORM");
    const requiredContextKeys = first.context?.routing?.items.filter(({ required }) => required).map(({ key }) => key) || [];
    expect(first.context?.reads?.map(({ key }) => key)).toEqual(expect.arrayContaining(requiredContextKeys));
    expect(spec.paths.businessForm).toContain("travel-expense-2026/BusinessForm.vue");
    expect(spec.paths.applyView).toContain("travel-expense-2026/Apply.vue");

    const form = generation.readFile("session-m3", started.generationId, spec.paths.businessForm, user).content;
    expect(form).toContain('"tripDays"');
    expect(form).not.toMatch(/startAndSubmit|FormData|upload|approve\(/);
    expect(() => generation.readFile("session-m3", started.generationId, "../pom.xml", user)).toThrow(/generation boundary/);

    const view = generation.readFile("session-m3", started.generationId, spec.paths.applyView, user).content;
    expect(view).toContain("WorkflowStartShell");
    expect(view).toContain('process-code="travel_expense_2026"');
    expect(view).not.toMatch(/附件|FormData|fetch\(/);
    const routeDiff = generation.readDiff("session-m3", started.generationId, spec.paths.routeRegistry, user);
    expect(routeDiff.changeType).toBe("MODIFY");
    expect(routeDiff.stagedContent).toContain("existing-route");
    expect(routeDiff.unifiedDiff).toContain("generated-travel-expense-2026-apply");

    const edited = generation.editFile(
      "session-m3", started.generationId, spec.paths.businessForm,
      `${form}\n<!-- reviewed -->\n`, 1, user,
    );
    expect(edited.revision).toBe(2);
    expect(edited.files.find((file) => file.relativePath === spec.paths.businessForm)?.editedByUser).toBe(true);

    const session = database.getSession("session-m3")!;
    const regenerated = generation.regenerate("session-m3", started.generationId, user, session.row_version, 2, "regen-m3");
    const second = await waitForReview(regenerated.generationId);
    expect(second.generationId).not.toBe(first.generationId);
    expect(second.context?.sha256).toBe(first.context?.sha256);
    expect(database.getGeneration(started.generationId)?.status).toBe("SUPERSEDED");
    expect(existsSync(database.getGeneration(regenerated.generationId)!.staging_dir)).toBe(true);
  });

  it("generates a different process without inventing attachments", async () => {
    const target = createGenerationTarget(parent);
    const requirement = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    requirement.businessCode = "leave-request";
    requirement.businessName = "请假申请";
    requirement.formFields = [
      { fieldCode: "reason", fieldName: "请假原因", fieldType: "string", controlType: "textarea", required: true, validation: {}, sortOrder: 1 },
      { fieldCode: "startDate", fieldName: "开始日期", fieldType: "date", controlType: "datePicker", required: true, validation: {}, sortOrder: 2 },
    ];
    requirement.attachments = [];
    seedActiveWorkflow(database, "session-no-files", null, "user_sales", requirement);
    const spec = deriveGenerationSpec(requirement, readContract(target));

    const started = generation.start("session-no-files", user, 0, "start-no-files", target);
    await waitForReview(started.generationId, "session-no-files");
    const form = generation.readFile("session-no-files", started.generationId, spec.paths.businessForm, user).content;
    const view = generation.readFile("session-no-files", started.generationId, spec.paths.applyView, user).content;
    expect(form).toContain('"reason"');
    expect(form).not.toContain("bankReceipt");
    expect(view).toContain('process-code="leave-request"');
    expect(view).not.toContain('type="file"');
  });

  it("keeps application numbers inside the delegated frontend form", async () => {
    const target = createGenerationTarget(parent);
    const requirement = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    requirement.businessCode = "numbered_request";
    requirement.businessName = "编号申请";
    requirement.formFields = [
      { fieldCode: "description", fieldName: "说明", fieldType: "string", controlType: "textarea", required: true, validation: {}, sortOrder: 1 },
      { fieldCode: "applicationNo", fieldName: "申请单号", fieldType: "string", controlType: "input", required: true, validation: {}, sortOrder: 2 },
    ];
    requirement.attachments = [];
    seedActiveWorkflow(database, "session-numbered", null, "user_sales", requirement);

    const started = generation.start("session-numbered", user, 0, "start-numbered", target);
    await waitForReview(started.generationId, "session-numbered");

    const spec = deriveGenerationSpec(requirement, readContract(target));
    const form = generation.readFile("session-numbered", started.generationId, spec.paths.businessForm, user).content;
    const apply = generation.readFile("session-numbered", started.generationId, spec.paths.applyView, user).content;
    expect(form).toContain('"applicationNo"');
    expect(apply).toContain('process-code="numbered_request"');
    expect(apply).not.toContain("setInstanceTitle");
  });

  it("generates initiation code when apply enters a parallel split gateway", async () => {
    const target = createGenerationTarget(parent);
    const requirement = parallelExpenseRequirement();
    seedActiveWorkflow(database, "session-parallel", null, "user_sales", requirement);

    const started = generation.start("session-parallel", user, 0, "start-parallel", target);
    await waitForReview(started.generationId, "session-parallel");

    const spec = deriveGenerationSpec(requirement, readContract(target));
    const apply = generation.readFile("session-parallel", started.generationId, spec.paths.applyView, user).content;
    expect(apply).toContain("WorkflowStartShell");
    expect(apply).not.toMatch(/startAndSubmit|approve\(|submitTask\(/);
  });

  it("preserves the frozen target baseline when a reviewed file is edited", async () => {
    const target = createGenerationTarget(parent);
    const requirement = travelExpenseRequirement();
    seedActiveWorkflow(database, "session-m3", null, "user_sales", requirement);
    const spec = deriveGenerationSpec(requirement, readContract(target));
    const started = generation.start("session-m3", user, 0, "start-baseline", target);
    const reviewed = await waitForReview(started.generationId);
    const original = reviewed.manifest!.files.find(
      ({ relativePath }) => relativePath === spec.paths.routeRegistry,
    )!;
    expect(original.changeType).toBe("MODIFY");

    writeFileSync(
      join(target, ...spec.paths.routeRegistry.split("/")),
      "export default [{ path: '/external-change' }];\\n",
      "utf8",
    );
    const staged = generation.readFile(
      "session-m3",
      started.generationId,
      spec.paths.routeRegistry,
      user,
    ).content;
    const edited = generation.editFile(
      "session-m3",
      started.generationId,
      spec.paths.routeRegistry,
      staged + "\\n// reviewed\\n",
      1,
      user,
    );
    const current = edited.files.find(
      ({ relativePath }) => relativePath === spec.paths.routeRegistry,
    )!;

    expect(current.changeType).toBe(original.changeType);
    expect(current.baseSha256).toBe(original.baseSha256);
    expect(generation.readDiff(
      "session-m3",
      started.generationId,
      spec.paths.routeRegistry,
      user,
    ).stale).toBe(true);
  });

  it("requires legacy unfinished generations without a context snapshot to restart", async () => {
    const target = createGenerationTarget(parent);
    const requirement = travelExpenseRequirement();
    seedActiveWorkflow(database, "session-legacy-context", null, "user_sales", requirement);
    const started = generation.start("session-legacy-context", user, 0, "start-legacy-context", target);
    await waitForReview(started.generationId, "session-legacy-context");
    database.db.prepare("UPDATE agent_code_generation SET generation_context_snapshot_json = '{}' WHERE id = ?")
      .run(started.generationId);
    const session = database.getSession("session-legacy-context")!;
    expect(() => generation.regenerate(
      "session-legacy-context", started.generationId, user, session.row_version, 1, "regen-legacy-context",
    )).toThrow(/context snapshot is missing; start a new generation/);
  });

  it("allows a hard-gate-failed revision to be edited before reverify", async () => {
    const target = createGenerationTarget(parent);
    const requirement = travelExpenseRequirement();
    seedActiveWorkflow(database, "session-m3", null, "user_sales", requirement);
    const spec = deriveGenerationSpec(requirement, readContract(target));
    const started = generation.start("session-m3", user, 0, "start-failed-edit", target);
    await waitForReview(started.generationId);
    database.db.prepare("UPDATE agent_code_generation SET status = 'FAILED' WHERE id = ?").run(started.generationId);
    database.db.prepare("UPDATE agent_session SET state = 'CODE_PIPELINE_FAILED' WHERE id = ?").run("session-m3");

    const content = generation.readFile("session-m3", started.generationId, spec.paths.businessForm, user).content;
    const edited = generation.editFile("session-m3", started.generationId, spec.paths.businessForm, content + "\\n<!-- fixed -->\\n", 1, user);
    expect(edited.revision).toBe(2);
  });

  it("rejects unsafe identifiers and an activated process-code mismatch", () => {
    const target = createGenerationTarget(parent);
    const invalid = travelExpenseRequirement();
    invalid.formFields[0].fieldCode = "default";
    seedActiveWorkflow(database, "session-invalid", null, "user_sales", invalid);
    let invalidError: AgentError | undefined;
    try { generation.start("session-invalid", user, 0, "invalid", target); } catch (error) { invalidError = error as AgentError; }
    expect(invalidError?.getResponse()).toEqual(expect.objectContaining({
      code: "AGENT_GENERATION_REQUIREMENT_INVALID",
      details: expect.objectContaining({ issues: expect.arrayContaining([expect.stringContaining("保留字")]) }),
    }));

    const mismatch = travelExpenseRequirement();
    seedActiveWorkflow(database, "session-mismatch", null, "user_sales", mismatch);
    database.db.prepare("UPDATE agent_process_definition SET process_code = ? WHERE session_id = ?")
      .run("another_process", "session-mismatch");
    expect(() => generation.start("session-mismatch", user, 0, "mismatch", target)).toThrow(/safely generated/);
  });

  async function waitForReview(id: string, sessionId = "session-m3") {
    const deadline = Date.now() + 3000;
    while (Date.now() < deadline) {
      const result = generation.get(sessionId, id, user);
      if (result.status === "REVIEW") return result;
      if (result.status === "FAILED") throw new Error(result.lastError?.message);
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    throw new Error("timed out waiting for M3 generation");
  }
});

function travelExpenseRequirement(): BusinessRequirement {
  const requirement = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
  requirement.businessCode = "travel_expense_2026";
  requirement.businessName = "差旅报销";
  requirement.goal = "员工提交差旅报销，由主管和财务依次处理。";
  requirement.formFields = [
    { fieldCode: "employeeName", fieldName: "员工姓名", fieldType: "string", controlType: "input", required: true, validation: {}, sortOrder: 1 },
    { fieldCode: "tripDays", fieldName: "出差天数", fieldType: "number", controlType: "number", required: true, validation: { minimum: 1 }, sortOrder: 2 },
    { fieldCode: "startDate", fieldName: "出发日期", fieldType: "date", controlType: "datePicker", required: true, validation: {}, sortOrder: 3 },
    { fieldCode: "urgent", fieldName: "紧急", fieldType: "boolean", controlType: "checkbox", required: false, validation: {}, sortOrder: 4 },
    { fieldCode: "expenseType", fieldName: "费用类型", fieldType: "select", controlType: "select", required: true, validation: {}, options: [{ label: "交通", value: "transport" }], sortOrder: 5 },
  ];
  requirement.attachments = [
    { attachmentCode: "receipts", attachmentName: "报销凭证", allowedExtensions: ["pdf", "jpg"], maxSizeBytes: 5_000_000, required: true, minCount: 1, maxCount: 5, applicableNodeCodes: ["apply"], sortOrder: 1 },
    { attachmentCode: "itinerary", attachmentName: "行程单", allowedExtensions: ["pdf"], maxSizeBytes: 2_000_000, required: false, minCount: 0, maxCount: 2, applicableNodeCodes: ["apply"], sortOrder: 2 },
    { attachmentCode: "financeProof", attachmentName: "财务补充材料", allowedExtensions: ["pdf"], maxSizeBytes: 2_000_000, required: false, minCount: 0, maxCount: 1, applicableNodeCodes: ["finance_confirm"], sortOrder: 3 },
  ];
  return requirement;
}

function parallelExpenseRequirement(): BusinessRequirement {
  const requirement = travelExpenseRequirement();
  const start = requirement.nodes.find((node) => node.nodeCode === "start")!;
  const apply = requirement.nodes.find((node) => node.nodeCode === "apply")!;
  const manager = requirement.nodes.find((node) => node.nodeCode === "manager_approve")!;
  const finance = requirement.nodes.find((node) => node.nodeCode === "finance_confirm")!;
  const end = requirement.nodes.find((node) => node.nodeCode === "end")!;
  requirement.nodes = [
    start,
    apply,
    { nodeCode: "parallel_split", nodeName: "并行分支", nodeType: "PARALLEL_SPLIT_GATEWAY", pairedGatewayCode: "parallel_join", positionX: 360, positionY: 120, sortOrder: 3 },
    { ...manager, sortOrder: 4 },
    { ...finance, sortOrder: 5 },
    { nodeCode: "parallel_join", nodeName: "并行汇聚", nodeType: "PARALLEL_JOIN_GATEWAY", pairedGatewayCode: "parallel_split", positionX: 780, positionY: 120, sortOrder: 6 },
    { ...end, sortOrder: 7 },
  ];
  requirement.edges = [
    { edgeCode: "e1", sourceNodeCode: "start", targetNodeCode: "apply", defaultEdge: false, sortOrder: 1 },
    { edgeCode: "e2", sourceNodeCode: "apply", targetNodeCode: "parallel_split", defaultEdge: false, sortOrder: 2 },
    { edgeCode: "e3", sourceNodeCode: "parallel_split", targetNodeCode: "manager_approve", defaultEdge: false, sortOrder: 3 },
    { edgeCode: "e4", sourceNodeCode: "parallel_split", targetNodeCode: "finance_confirm", defaultEdge: false, sortOrder: 4 },
    { edgeCode: "e5", sourceNodeCode: "manager_approve", targetNodeCode: "parallel_join", defaultEdge: false, sortOrder: 5 },
    { edgeCode: "e6", sourceNodeCode: "finance_confirm", targetNodeCode: "parallel_join", defaultEdge: false, sortOrder: 6 },
    { edgeCode: "e7", sourceNodeCode: "parallel_join", targetNodeCode: "end", defaultEdge: false, sortOrder: 7 },
  ];
  return requirement;
}

function readContract(target: string): GenerationTargetContract {
  return JSON.parse(readFileSync(join(target, ".flowmind", "generation-target.json"), "utf8")) as GenerationTargetContract;
}
