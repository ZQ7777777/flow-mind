import { describe, expect, it } from "vitest";
import { shallowMount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import type { CodeGenerationSummary } from "@flowmind/agent-contracts";
import CodeGenerationPanel from "./CodeGenerationPanel.vue";

describe("CodeGenerationPanel", () => {
  it("builds a code tree from the artifact manifest", () => {
    setActivePinia(createPinia());
    const generation: CodeGenerationSummary = {
      generationId: "acg_1", status: "REVIEW", generationRevision: 1,
      targetRoot: "E:\\business-base", contractVersion: "1.0",
      manifest: {
        generationId: "acg_1", targetRoot: "E:\\business-base", contractVersion: "1.0", revision: 1,
        files: [{ relativePath: "frontend/src/router/generated-routes.ts", changeType: "MODIFY", stagedSha256: "a", baseSha256: "b", sizeBytes: 10, validationStatus: "VALID", editedByUser: false }],
      },
      createdAt: "2026-08-03T00:00:00Z", updatedAt: "2026-08-03T00:00:00Z",
    };
    const wrapper = shallowMount(CodeGenerationPanel, {
      props: { generation },
      global: { stubs: {
        ElTree: { name: "ElTree", props: ["data"], template: "<div />" },
        ElTag: { template: "<span><slot /></span>" }, ElRadioGroup: true, ElRadioButton: true, ElButton: true,
      } },
    });
    const data = wrapper.findComponent({ name: "ElTree" }).props("data") as Array<{ label: string; children: unknown[] }>;
    expect(data[0].label).toBe("frontend");
    expect(JSON.stringify(data)).toContain("generated-routes.ts");
    expect(wrapper.text()).toContain("revision 1");
  });
});
