import { describe, expect, it, vi } from "vitest";
import { flushPromises, shallowMount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import type { CodeGenerationSummary } from "@flowmind/agent-contracts";
import CodeGenerationPanel from "./CodeGenerationPanel.vue";

vi.mock("monaco-editor/esm/vs/editor/editor.api.js", () => ({
  editor: {
    create: () => ({ dispose: vi.fn(), getModel: vi.fn(), layout: vi.fn(), onDidChangeModelContent: vi.fn(), setValue: vi.fn() }),
    createDiffEditor: () => ({ dispose: vi.fn(), getModel: vi.fn(), layout: vi.fn(), setModel: vi.fn() }),
  },
}));

function generationWith(changeType: "ADD" | "MODIFY"): CodeGenerationSummary {
  return {
    generationId: "acg_1", status: "REVIEW", generationRevision: 1,
    targetRoot: "E:\\business-base", contractVersion: "1.0",
    manifest: {
      generationId: "acg_1", targetRoot: "E:\\business-base", contractVersion: "1.0", revision: 1,
      files: [{ relativePath: "frontend/src/router/generated-routes.ts", changeType, stagedSha256: "a", baseSha256: "b", sizeBytes: 10, validationStatus: "VALID", editedByUser: false }],
    },
    createdAt: "2026-08-03T00:00:00Z", updatedAt: "2026-08-03T00:00:00Z",
  };
}

describe("CodeGenerationPanel", () => {
  it("builds a code tree from the artifact manifest", () => {
    setActivePinia(createPinia());
    const generation = generationWith("MODIFY");
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
    expect(wrapper.find(".code-tree-scroll").exists()).toBe(true);
  });

  it.each([
    ["ADD", "success"],
    ["MODIFY", "warning"],
  ] as const)("renders the %s label and toolbar controls before the file path", async (changeType, tagType) => {
    setActivePinia(createPinia());
    const wrapper = shallowMount(CodeGenerationPanel, {
      props: { generation: generationWith(changeType) },
      global: { stubs: {
        ElTree: { name: "ElTree", props: ["data"], template: "<div />" },
        ElTag: { name: "ElTag", props: ["type"], template: "<span :data-type=\"type\"><slot /></span>" },
        ElRadioGroup: { name: "ElRadioGroup", template: "<span><slot /></span>" },
        ElRadioButton: { template: "<button><slot /></button>" },
        ElButton: { name: "ElButton", template: "<button><slot /></button>" },
      } },
    });

    wrapper.findComponent({ name: "ElTree" }).vm.$emit("node-click", {
      label: "generated-routes.ts",
      path: "frontend/src/router/generated-routes.ts",
    });
    await flushPromises();

    const toolbarItems = wrapper.find(".editor-toolbar").findAll(
      ".file-change-type, .editor-mode-switch, .editor-file-path, .editor-save",
    );
    expect(toolbarItems.map((item) => item.classes().find((name) => name.startsWith("editor-") || name === "file-change-type"))).toEqual([
      "file-change-type", "editor-mode-switch", "editor-file-path", "editor-save",
    ]);
    const changeTypeTag = wrapper.find(".file-change-type");
    expect(changeTypeTag.attributes("data-type")).toBe(tagType);
    expect(changeTypeTag.text()).toBe(changeType);
  });
});
