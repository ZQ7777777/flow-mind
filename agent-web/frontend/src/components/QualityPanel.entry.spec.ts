import { describe, expect, it, vi } from "vitest";
import { shallowMount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import type { CodeGenerationSummary } from "@flowmind/agent-contracts";
import { useWorkflowStore } from "../stores/workflow";
import QualityPanel from "./QualityPanel.vue";

const generation: CodeGenerationSummary = {
  generationId: "acg-ready",
  status: "REVIEW",
  generationRevision: 1,
  backendRestartRequired: false,
  targetRoot: "D:\\business-base",
  contractVersion: "1.0",
  manifest: { generationId: "acg-ready", targetRoot: "D:\\business-base", contractVersion: "1.0", revision: 1, files: [] },
  createdAt: "2026-08-05T00:00:00.000Z",
  updatedAt: "2026-08-05T00:00:00.000Z",
};

describe("QualityPanel gate entry", () => {
  it("offers normal and skipped-AI gate entry before the first run", async () => {
    setActivePinia(createPinia());
    const store = useWorkflowStore();
    const startQuality = vi.spyOn(store, "startGenerationQuality").mockResolvedValue();
    const wrapper = shallowMount(QualityPanel, {
      props: { generation },
      global: {
        stubs: {
          ElTag: { template: "<span><slot /></span>" },
          ElButton: { name: "ElButton", props: ["disabled"], template: "<button :disabled=\"disabled\"><slot /></button>" },
          ElPopconfirm: { template: "<div><slot name=\"reference\" /></div>" },
          ElCheckboxGroup: true,
          ElCheckbox: true,
          ElInput: true,
        },
      },
    });

    const buttons = wrapper.findAll("button");
    await buttons.find((button) => button.text().includes("进入质量门禁"))!.trigger("click");
    await buttons.find((button) => button.text().includes("跳过 AI 审核"))!.trigger("click");

    expect(startQuality).toHaveBeenNthCalledWith(1, false);
    expect(startQuality).toHaveBeenNthCalledWith(2, true);
  });
});
