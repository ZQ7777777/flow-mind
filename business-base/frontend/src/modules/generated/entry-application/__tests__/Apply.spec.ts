import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import { nextTick } from "vue";
import WorkflowStartShell from "../../../../components/workflow/WorkflowStartShell.vue";
import Apply from "../Apply.vue";
import BusinessForm from "../BusinessForm.vue";

vi.mock("../../../../api/workflow", () => ({
  fetchWorkflowStartContext: vi.fn().mockResolvedValue({
    definitionId: "e45c38c8-2b20-4d4c-9777-d9dfa8299f29",
    definitionVersion: 9,
    processCode: "entry_application",
    processName: "入金申请",
    pageTitle: "发起入金申请",
    startable: true,
    formFields: [],
    fieldPermissions: [],
    attachmentTemplates: [],
    defaultVariables: {},
  }),
  startWorkflowProcess: vi.fn(),
}));

vi.mock("../../../../utils/idempotency", () => ({
  createIdempotencyKey: vi.fn(() => "test-idempotency-key"),
}));

describe("Apply.vue", () => {
  it("delegates to the shared WorkflowStartShell with the confirmed processCode and generated form", async () => {
    const wrapper = mount(Apply, {
      global: {
        stubs: {
          WorkflowStartShell: true,
        },
      },
    });
    await vi.dynamicImportSettled?.();
    const shell = wrapper.findComponent(WorkflowStartShell);
    expect(shell.exists()).toBe(true);
    expect(shell.props("processCode")).toBe("entry_application");
    expect(shell.props("businessForm")).toBe(BusinessForm);
  });

  it("renders the real shared shell as the page owner instead of duplicating it", async () => {
    const wrapper = mount(Apply);
    expect(wrapper.findComponent(WorkflowStartShell).exists()).toBe(true);
    // 外壳通过 onMounted 异步加载 start-context，需要等待 promise 解析和 DOM 更新。
    await new Promise((resolve) => setTimeout(resolve, 0));
    await nextTick();
    const heading = wrapper.find("h1");
    expect(heading.exists()).toBe(true);
    expect(heading.text()).toBe("发起入金申请");
    // 发起附件与提交按钮由共享外壳持有，Apply 只做委托。
    expect(wrapper.find(".form-actions").exists()).toBe(true);
  });

  it("contains no submit implementation or business-specific workflow API call", async () => {
    const wrapper = mount(Apply);
    await vi.dynamicImportSettled?.();
    const { fetchWorkflowStartContext, startWorkflowProcess } = await import("../../../../api/workflow");
    expect(fetchWorkflowStartContext).toHaveBeenCalledWith("entry_application");
    // Apply 自身不触发提交；只有用户点击外壳的提交按钮才会调用 start-submit。
    expect(startWorkflowProcess).not.toHaveBeenCalled();
  });

  it("does not define its own loading, error or success submission state", () => {
    const wrapper = mount(Apply, {
      global: {
        stubs: {
          WorkflowStartShell: true,
        },
      },
    });
    expect(wrapper.vm).not.toHaveProperty("submitting");
    expect(wrapper.vm).not.toHaveProperty("submit");
    expect(wrapper.vm).not.toHaveProperty("error");
    expect(wrapper.vm).not.toHaveProperty("success");
  });
});
