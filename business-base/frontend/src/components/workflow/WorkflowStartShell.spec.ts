import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h, nextTick } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import WorkflowStartShell from "./WorkflowStartShell.vue";
import { fetchWorkflowStartContext, startWorkflowProcess } from "../../api/workflow";

vi.mock("../../api/workflow", () => ({
  fetchWorkflowStartContext: vi.fn(),
  startWorkflowProcess: vi.fn(),
}));

const BusinessForm = defineComponent({
  props: ["modelValue", "fields", "fieldPermissions", "mode", "disabled"],
  emits: ["update:modelValue"],
  setup(_props, { expose }) {
    expose({ validate: vi.fn().mockResolvedValue(true) });
    return () => h("div", { "data-test": "business-form" }, "form");
  },
});

const InvalidBusinessForm = defineComponent({
  setup(_props, { expose }) {
    expose({ validate: vi.fn().mockResolvedValue(false) });
    return () => h("div", { "data-test": "invalid-form" });
  },
});

const UploadStub = defineComponent({
  name: "ElUpload",
  emits: ["change"],
  setup(_props, { slots }) {
    return () => h("button", { "data-test": "upload-stub", type: "button" }, slots.default?.());
  },
});

describe("WorkflowStartShell", () => {
  beforeEach(() => {
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: vi.fn((file: File) => `blob:${file.name}`),
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: vi.fn(),
    });
    vi.mocked(fetchWorkflowStartContext).mockReset().mockResolvedValue({
      definitionId: "definition-1",
      definitionVersion: 5,
      processCode: "travel_expense",
      processName: "差旅报销",
      pageTitle: "发起差旅报销",
      startable: true,
      formFields: [],
      fieldPermissions: [],
      attachmentTemplates: [],
      defaultVariables: { currency: "CNY" },
    });
    vi.mocked(startWorkflowProcess).mockReset().mockResolvedValue({
      instanceId: "instance-1", createdTasks: [],
    });
  });

  it("loads context and delegates one generic start-submit request", async () => {
    const wrapper = mount(WorkflowStartShell, {
      props: { processCode: "travel_expense", businessForm: BusinessForm },
    });
    await flushPromises();
    expect(fetchWorkflowStartContext).toHaveBeenCalledWith("travel_expense");
    expect(wrapper.get("[data-test='business-form']").text()).toBe("form");

    await wrapper.get("[data-test='submit-button']").trigger("click");
    await flushPromises();

    expect(startWorkflowProcess).toHaveBeenCalledTimes(1);
    expect(startWorkflowProcess).toHaveBeenCalledWith("travel_expense", expect.objectContaining({
      definitionId: "definition-1",
      definitionVersion: 5,
      variables: { currency: "CNY" },
      attachments: {},
      idempotencyKey: expect.any(String),
    }));
    expect(wrapper.text()).toContain("instance-1");
  });

  it("does not submit when the backend says the process is unavailable", async () => {
    vi.mocked(fetchWorkflowStartContext).mockResolvedValueOnce({
      definitionId: "definition-1", definitionVersion: 5, processCode: "travel_expense",
      processName: "差旅报销", startable: false, disabledReason: "无发起权限",
      formFields: [], fieldPermissions: [], attachmentTemplates: [], defaultVariables: {},
    });
    const wrapper = mount(WorkflowStartShell, {
      props: { processCode: "travel_expense", businessForm: BusinessForm },
    });
    await flushPromises();
    expect(wrapper.text()).toContain("无发起权限");
    expect(wrapper.get("[data-test='submit-button']").attributes("disabled")).toBeDefined();
    await wrapper.get("[data-test='submit-button']").trigger("click");
    expect(startWorkflowProcess).not.toHaveBeenCalled();
  });

  it("normalizes missing optional start context arrays before rendering the business form", async () => {
    const InspectingBusinessForm = defineComponent({
      props: ["modelValue", "fields", "fieldPermissions", "mode", "disabled"],
      setup(componentProps, { expose }) {
        expose({ validate: vi.fn().mockResolvedValue(true) });
        return () => h("div", { "data-test": "business-form" }, [
          `fields:${componentProps.fields.length}`,
          ` permissions:${componentProps.fieldPermissions.length}`,
        ]);
      },
    });
    vi.mocked(fetchWorkflowStartContext).mockResolvedValueOnce({
      definitionId: "definition-1",
      definitionVersion: 5,
      processCode: "travel_expense",
      processName: "差旅报销",
      startable: true,
    } as Awaited<ReturnType<typeof fetchWorkflowStartContext>>);

    const wrapper = mount(WorkflowStartShell, {
      props: { processCode: "travel_expense", businessForm: InspectingBusinessForm },
    });
    await flushPromises();

    expect(wrapper.get("[data-test='business-form']").text()).toContain("fields:0 permissions:0");
  });

  it("does not submit when business-field validation fails", async () => {
    const wrapper = mount(WorkflowStartShell, {
      props: { processCode: "travel_expense", businessForm: InvalidBusinessForm },
    });
    await flushPromises();
    await wrapper.get("[data-test='submit-button']").trigger("click");
    await flushPromises();
    expect(startWorkflowProcess).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("请修正表单字段后再提交");
  });

  it("validates required attachments before calling start-submit", async () => {
    vi.mocked(fetchWorkflowStartContext).mockResolvedValueOnce({
      definitionId: "definition-1", definitionVersion: 5, processCode: "travel_expense",
      processName: "差旅报销", startable: true,
      formFields: [], fieldPermissions: [], defaultVariables: {},
      attachmentTemplates: [{
        attachmentCode: "receipt", attachmentName: "报销凭证", required: true,
        minCount: 1, maxCount: 2, maxSizeMb: 5, allowedExtensions: ["pdf"],
      }],
    });
    const wrapper = mount(WorkflowStartShell, {
      props: { processCode: "travel_expense", businessForm: BusinessForm },
    });
    await flushPromises();
    await wrapper.get("[data-test='submit-button']").trigger("click");
    await flushPromises();
    expect(startWorkflowProcess).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("报销凭证需上传 1-2 个文件");
  });

  it("renders backend start attachments returned as attachments", async () => {
    vi.mocked(fetchWorkflowStartContext).mockResolvedValueOnce({
      definitionId: "definition-1", definitionVersion: 5, processCode: "travel_expense",
      processName: "差旅报销", startable: true,
      formFields: [], fieldPermissions: [], defaultVariables: {},
      attachmentTemplates: [],
      attachments: [{
        attachmentCode: "receipt", attachmentName: "报销凭证", required: true,
        minCount: 1, maxCount: 2, maxSizeBytes: 5242880, allowedExtensions: ["pdf"],
      }],
    });
    const wrapper = mount(WorkflowStartShell, {
      props: { processCode: "travel_expense", businessForm: BusinessForm },
    });
    await flushPromises();

    expect(wrapper.text()).toContain("影像资料上传");
    expect(wrapper.text()).toContain("报销凭证");
    expect(wrapper.find("el-upload").exists()).toBe(true);
  });

  it("renders the archive workbench with list columns and upload area", async () => {
    vi.mocked(fetchWorkflowStartContext).mockResolvedValueOnce({
      definitionId: "definition-1", definitionVersion: 5, processCode: "travel_expense",
      processName: "差旅报销", startable: true,
      formFields: [], fieldPermissions: [], defaultVariables: {},
      attachmentTemplates: [{
        attachmentCode: "receipt", attachmentName: "报销凭证", required: true,
        minCount: 1, maxCount: 2, maxSizeBytes: 5242880, allowedExtensions: ["pdf", "txt"],
      }],
    });
    const wrapper = mount(WorkflowStartShell, {
      props: { processCode: "travel_expense", businessForm: BusinessForm },
      global: { stubs: { "el-upload": UploadStub } },
    });
    await flushPromises();

    expect(wrapper.get("[data-test='attachment-workbench']").text()).toContain("档案列表");
    expect(wrapper.text()).toContain("档案上传");
    expect(wrapper.text()).toContain("序号");
    expect(wrapper.text()).toContain("档案类型");
    expect(wrapper.text()).toContain("文件名称");
    expect(wrapper.text()).toContain("文件格式");
    expect(wrapper.text()).toContain("下载链接");
  });

  it("lists selected files and previews text content", async () => {
    vi.mocked(fetchWorkflowStartContext).mockResolvedValueOnce({
      definitionId: "definition-1", definitionVersion: 5, processCode: "travel_expense",
      processName: "差旅报销", startable: true,
      formFields: [], fieldPermissions: [], defaultVariables: {},
      attachmentTemplates: [{
        attachmentCode: "receipt", attachmentName: "报销凭证", required: true,
        minCount: 1, maxCount: 2, maxSizeBytes: 5242880, allowedExtensions: ["txt"],
      }],
    });
    const wrapper = mount(WorkflowStartShell, {
      props: { processCode: "travel_expense", businessForm: BusinessForm },
      global: { stubs: { "el-upload": UploadStub } },
    });
    await flushPromises();

    const file = new File(["hello attachment"], "receipt.txt", { type: "text/plain" });
    Object.defineProperty(file, "text", {
      value: vi.fn().mockResolvedValue("hello attachment"),
    });
    const upload = wrapper.findComponent({ name: "ElUpload" });
    upload.vm.$emit("change", { raw: file }, [{
      name: file.name,
      uid: 1,
      status: "ready",
      size: file.size,
      raw: file,
    }]);
    await flushPromises();

    expect(wrapper.text()).toContain("报销凭证");
    expect(wrapper.text()).toContain("receipt.txt");
    expect(wrapper.text()).toContain("TXT");
    expect(wrapper.get("a.download-link").attributes("download")).toBe("receipt.txt");
    expect(wrapper.text()).toContain("hello attachment");
  });

  it("clears the selected preview after deleting the last selected file", async () => {
    vi.mocked(fetchWorkflowStartContext).mockResolvedValueOnce({
      definitionId: "definition-1", definitionVersion: 5, processCode: "travel_expense",
      processName: "差旅报销", startable: true,
      formFields: [], fieldPermissions: [], defaultVariables: {},
      attachmentTemplates: [{
        attachmentCode: "receipt", attachmentName: "报销凭证", required: true,
        minCount: 1, maxCount: 2, maxSizeBytes: 5242880, allowedExtensions: ["txt"],
      }],
    });
    const wrapper = mount(WorkflowStartShell, {
      props: { processCode: "travel_expense", businessForm: BusinessForm },
      global: { stubs: { "el-upload": UploadStub } },
    });
    await flushPromises();

    const file = new File(["hello attachment"], "receipt.txt", { type: "text/plain" });
    Object.defineProperty(file, "text", {
      value: vi.fn().mockResolvedValue("hello attachment"),
    });
    wrapper.findComponent({ name: "ElUpload" }).vm.$emit("change", { raw: file }, [{
      name: file.name,
      uid: 1,
      status: "ready",
      size: file.size,
      raw: file,
    }]);
    await flushPromises();
    expect(wrapper.text()).toContain("hello attachment");

    const deleteButton = wrapper.findAll("el-button").find((button) => button.text() === "删除");
    expect(deleteButton).toBeTruthy();
    await deleteButton!.trigger("click");
    await flushPromises();

    expect(wrapper.text()).not.toContain("receipt.txt");
    expect(wrapper.text()).toContain("暂无已上传附件");
    expect(wrapper.text()).toContain("请选择或上传附件后查看内容");
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:receipt.txt");
  });

  it("suppresses duplicate submits while the first request is in flight", async () => {
    let resolveSubmit!: (value: { instanceId: string; createdTasks: [] }) => void;
    vi.mocked(startWorkflowProcess).mockReturnValueOnce(new Promise((resolve) => {
      resolveSubmit = resolve;
    }));
    const wrapper = mount(WorkflowStartShell, {
      props: { processCode: "travel_expense", businessForm: BusinessForm },
    });
    await flushPromises();

    const firstClick = wrapper.get("[data-test='submit-button']").trigger("click");
    await nextTick();
    await wrapper.get("[data-test='submit-button']").trigger("click");
    expect(startWorkflowProcess).toHaveBeenCalledTimes(1);

    resolveSubmit({ instanceId: "instance-1", createdTasks: [] });
    await firstClick;
    await flushPromises();
  });
});
