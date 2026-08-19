import { describe, expect, it, vi } from "vitest";
import { flushPromises, shallowMount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import type { CodeGenerationSummary } from "@flowmind/agent-contracts";
import { useWorkflowStore } from "../stores/workflow";
import CodeGenerationPanel from "./CodeGenerationPanel.vue";

vi.mock("monaco-editor/esm/vs/editor/editor.api.js", () => ({
  editor: {
    create: () => ({ dispose: vi.fn(), getModel: vi.fn(), layout: vi.fn(), onDidChangeModelContent: vi.fn(), setValue: vi.fn() }),
    createDiffEditor: () => ({ dispose: vi.fn(), getModel: vi.fn(), layout: vi.fn(), setModel: vi.fn() }),
  },
}));

function generationWith(changeType: "ADD" | "MODIFY"): CodeGenerationSummary {
  return {
    generationId: "acg_1", status: "REVIEW", generationRevision: 1, backendRestartRequired: false,
    targetRoot: "E:\\business-base", contractVersion: "1.0",
    manifest: {
      generationId: "acg_1", targetRoot: "E:\\business-base", contractVersion: "1.0", revision: 1,
      files: [{ relativePath: "frontend/src/router/generated-routes.ts", changeType, stagedSha256: "a", baseSha256: "b", sizeBytes: 10, validationStatus: "VALID", editedByUser: false }],
    },
    createdAt: "2026-08-03T00:00:00Z", updatedAt: "2026-08-03T00:00:00Z",
  };
}

function generationWithPreview(): CodeGenerationSummary {
  const generation = generationWith("MODIFY");
  generation.manifest!.files.unshift(
    {
      relativePath: "frontend/src/modules/generated/entry/Apply.vue",
      changeType: "ADD", stagedSha256: "apply", sizeBytes: 100,
      validationStatus: "VALID", editedByUser: false,
    },
    {
      relativePath: "frontend/src/modules/generated/entry/BusinessForm.vue",
      changeType: "ADD", stagedSha256: "form", sizeBytes: 100,
      validationStatus: "VALID", editedByUser: false,
    },
  );
  return generation;
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
    expect(wrapper.findAll("[role='separator']")).toHaveLength(2);
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

  it("shows the generated Vue page as a sandboxed static preview by default", async () => {
    setActivePinia(createPinia());
    const store = useWorkflowStore();
    const loadPreview = vi.spyOn(store, "loadGeneratedPreviewFile").mockResolvedValue({
      generationId: "acg_1",
      generationRevision: 1,
      relativePath: "frontend/src/modules/generated/entry/BusinessForm.vue",
      content: '<template><section><h1>入金申请</h1><el-input placeholder="申请单号" /></section></template>',
      sha256: "vue",
    });
    const wrapper = shallowMount(CodeGenerationPanel, {
      props: { generation: generationWithPreview() },
      global: { stubs: {
        ElTree: { name: "ElTree", props: ["data"], template: "<div />" },
        ElTag: { template: "<span><slot /></span>" },
        ElRadioGroup: { template: "<span><slot /></span>" },
        ElRadioButton: { template: "<button><slot /></button>" },
        ElButton: { template: "<button><slot /></button>" },
      } },
    });
    await flushPromises();

    expect(loadPreview).toHaveBeenCalledWith("frontend/src/modules/generated/entry/BusinessForm.vue");
    expect(wrapper.text()).toContain("静态预览");
    expect(wrapper.text()).toContain("仅展示界面，不执行脚本或提交请求");
    const frame = wrapper.find('iframe[title="Agent 生成前端界面静态预览"]');
    expect(frame.exists()).toBe(true);
    expect(frame.attributes("sandbox")).toBe("allow-scripts");
    expect(frame.attributes("sandbox")).not.toContain("allow-same-origin");
    expect(frame.attributes("sandbox")).not.toContain("allow-forms");
    expect(frame.attributes("srcdoc")).toContain("入金申请");
    expect(frame.attributes("srcdoc")).toContain("default-src 'none'");
  });

  it("switches from interface preview to code editing when a tree file is selected", async () => {
    setActivePinia(createPinia());
    const store = useWorkflowStore();
    vi.spyOn(store, "loadGeneratedPreviewFile").mockResolvedValue({
      generationId: "acg_1", generationRevision: 1,
      relativePath: "frontend/src/modules/generated/entry/BusinessForm.vue",
      content: "<template><form /></template>", sha256: "vue",
    });
    vi.spyOn(store, "loadGeneratedFile").mockResolvedValue();
    const wrapper = shallowMount(CodeGenerationPanel, {
      props: { generation: generationWithPreview() },
      global: { stubs: {
        ElTree: { name: "ElTree", props: ["data"], template: "<div />" },
        ElTag: { template: "<span><slot /></span>" },
        ElRadioGroup: { template: "<span><slot /></span>" },
        ElRadioButton: { template: "<button><slot /></button>" },
        ElButton: { template: "<button><slot /></button>" },
      } },
    });
    await flushPromises();
    wrapper.findComponent({ name: "ElTree" }).vm.$emit("node-click", {
      label: "generated-routes.ts",
      path: "frontend/src/router/generated-routes.ts",
    });
    await flushPromises();

    expect(wrapper.find("iframe").exists()).toBe(false);
    expect(wrapper.find(".editor-file-path").text()).toBe("frontend/src/router/generated-routes.ts");
  });

  it("keeps code browsing available when static preview parsing fails", async () => {
    setActivePinia(createPinia());
    const store = useWorkflowStore();
    vi.spyOn(store, "loadGeneratedPreviewFile").mockResolvedValue({
      generationId: "acg_1", generationRevision: 1,
      relativePath: "frontend/src/modules/generated/entry/BusinessForm.vue",
      content: "<script setup>const invalid = true</script>", sha256: "vue",
    });
    const wrapper = shallowMount(CodeGenerationPanel, {
      props: { generation: generationWithPreview() },
      global: { stubs: {
        ElTree: { name: "ElTree", props: ["data"], template: "<div />" },
        ElTag: { template: "<span><slot /></span>" },
        ElRadioGroup: { template: "<span><slot /></span>" },
        ElRadioButton: { template: "<button><slot /></button>" },
        ElButton: { template: "<button><slot /></button>" },
      } },
    });
    await flushPromises();

    expect(wrapper.find("[role='alert']").text()).toContain("缺少 template");
    expect(wrapper.findComponent({ name: "ElTree" }).exists()).toBe(true);
  });
});
