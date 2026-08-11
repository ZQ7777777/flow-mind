import { beforeEach, describe, expect, it, vi } from "vitest";
import { shallowMount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import type { CodeGenerationSummary, GenerationQualityReport, WorkflowSnapshot } from "@flowmind/agent-contracts";
import { useWorkflowStore } from "../stores/workflow";
import QualityPanel from "./QualityPanel.vue";

const quality: GenerationQualityReport = {
  generationId: "acg-quality",
  revision: 2,
  pipelineState: "PASSED",
  repairRound: 1,
  maxRepairRounds: 3,
  stages: [{
    stage: "FRONTEND_TYPECHECK",
    status: "FAILED",
    hardGate: true,
    summary: "failed",
    diagnostics: [{
      code: "TS2345",
      message: "Invalid input.",
      severity: "ERROR",
      hardGate: true,
      relativePath: "frontend/src/modules/generated/example.ts",
      line: 7,
      column: 9,
      actual: "No numeric payload property was recognized.",
      expected: "The payload property must be numeric.",
      evidence: "Inspected the generated API payload syntax tree.",
      repairHint: "Change the payload property type to number.",
      acceptedForms: ["amount: number"],
      unsupportedForms: ["A comment that only mentions amount"],
    }],
  }],
  repairAttempts: [{
    round: 3,
    verificationRunId: "verification-quality",
    changedFiles: [
      "frontend/src/modules/generated/example.ts",
      "backend/src/test/java/example/ExampleControllerTest.java",
      "backend/src/test/java/example/ExampleServiceTest.java",
    ],
    resolutions: [],
    diagnosticIds: ["diagnostic-quality"],
    outcome: "NO_EFFECT",
    failureCode: "REPAIR_PROTOCOL_INVALID",
    createdAt: "2026-08-05T00:00:00.500Z",
  }],
  review: {
    reviewId: "review-quality",
    status: "PASSED",
    verdict: "APPROVE",
    summary: "approved",
    issues: [],
    createdAt: "2026-08-05T00:00:00.000Z",
    completedAt: "2026-08-05T00:00:01.000Z",
  },
  hardGatePassed: true,
  overrideRequired: false,
  canWrite: true,
  updatedAt: "2026-08-05T00:00:01.000Z",
};

function generation(status: CodeGenerationSummary["status"]): CodeGenerationSummary {
  return {
    generationId: "acg-quality",
    status,
    generationRevision: 2,
    targetRoot: "D:\\business-base",
    contractVersion: "1.0",
    quality,
    createdAt: "2026-08-05T00:00:00.000Z",
    updatedAt: "2026-08-05T00:00:01.000Z",
  };
}

const stubs = {
  ElTag: { template: "<span><slot /></span>" },
  ElButton: { name: "ElButton", props: ["disabled"], template: "<button :disabled=\"disabled\"><slot /></button>" },
  ElPopconfirm: { template: "<div><slot name=\"reference\" /></div>" },
  ElCheckboxGroup: true,
  ElCheckbox: true,
  ElInput: true,
};

describe("QualityPanel", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("opens a diagnostic in the existing code editor", async () => {
    const store = useWorkflowStore();
    store.qualityReport = quality;
    const wrapper = shallowMount(QualityPanel, {
      props: { generation: generation("REVIEW") },
      global: { stubs },
    });
    await wrapper.find(".diagnostic-target").trigger("click");
    expect(wrapper.emitted("selectDiagnostic")).toEqual([["frontend/src/modules/generated/example.ts"]]);
  });

  it("shows the same detailed diagnostic evidence and repair forms sent to the repair agent", async () => {
    const store = useWorkflowStore();
    store.qualityReport = quality;
    const wrapper = shallowMount(QualityPanel, {
      props: { generation: generation("FAILED") },
      global: { stubs },
    });

    expect(wrapper.find(".diagnostic-details").exists()).toBe(true);
    expect(wrapper.text()).toContain("No numeric payload property was recognized.");
    expect(wrapper.text()).toContain("The payload property must be numeric.");
    expect(wrapper.text()).toContain("Inspected the generated API payload syntax tree.");
    expect(wrapper.text()).toContain("Change the payload property type to number.");
    expect(wrapper.text()).toContain("amount: number");
    expect(wrapper.text()).toContain("A comment that only mentions amount");
  });

  it("shows when repair changes were rolled back by protocol validation", () => {
    const store = useWorkflowStore();
    store.qualityReport = quality;
    const wrapper = shallowMount(QualityPanel, {
      props: { generation: generation("REVIEW") },
      global: { stubs },
    });
    expect(wrapper.text()).toContain("REPAIR_PROTOCOL_INVALID");
    expect(wrapper.text()).toContain("3 个文件");
    expect(wrapper.text()).toContain("已回滚");
  });

  it("disables reverify and write after the generation is completed", () => {
    const store = useWorkflowStore();
    store.qualityReport = quality;
    const wrapper = shallowMount(QualityPanel, {
      props: { generation: generation("COMPLETED") },
      global: { stubs },
    });
    const buttons = wrapper.findAll("button");
    expect(buttons.find((button) => button.text().includes("重新验证"))?.attributes("disabled")).toBeDefined();
    expect(buttons.find((button) => button.text().includes("写入工程"))?.attributes("disabled")).toBeDefined();
  });

  it("allows reverify after a failed revision is edited and its stale quality report is cleared", async () => {
    const store = useWorkflowStore();
    const failedGeneration = { ...generation("FAILED"), quality: undefined };
    store.snapshot = {
      sessionId: "session-quality",
      ownerUserId: "user-quality",
      state: "CODE_PIPELINE_FAILED",
      rowVersion: 3,
      messages: [],
      activeGeneration: failedGeneration,
      allowedActions: ["EDIT_GENERATED_FILE", "REGENERATE", "REVERIFY"],
    } satisfies WorkflowSnapshot;
    const reverify = vi.spyOn(store, "reverifyGeneration").mockResolvedValue();
    const wrapper = shallowMount(QualityPanel, {
      props: { generation: failedGeneration },
      global: { stubs },
    });

    const button = wrapper.findAll("button").find((item) => item.text().includes("重新验证"));
    expect(button?.attributes("disabled")).toBeUndefined();
    await button!.trigger("click");

    expect(reverify).toHaveBeenCalledWith(false);
  });

  it("keeps reverify disabled when the backend does not allow it", () => {
    const store = useWorkflowStore();
    store.snapshot = {
      sessionId: "session-quality",
      ownerUserId: "user-quality",
      state: "COMPLETED",
      rowVersion: 4,
      messages: [],
      activeGeneration: generation("COMPLETED"),
      allowedActions: [],
    } satisfies WorkflowSnapshot;
    const wrapper = shallowMount(QualityPanel, {
      props: { generation: generation("COMPLETED") },
      global: { stubs },
    });

    const button = wrapper.findAll("button").find((item) => item.text().includes("重新验证"));
    expect(button?.attributes("disabled")).toBeDefined();
  });
});
