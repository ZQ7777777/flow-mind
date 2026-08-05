import { beforeEach, describe, expect, it, vi } from "vitest";
import { shallowMount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import type { CodeGenerationSummary, GenerationQualityReport } from "@flowmind/agent-contracts";
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
    }],
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
    await wrapper.find(".diagnostic").trigger("click");
    expect(wrapper.emitted("selectDiagnostic")).toEqual([["frontend/src/modules/generated/example.ts"]]);
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
});
