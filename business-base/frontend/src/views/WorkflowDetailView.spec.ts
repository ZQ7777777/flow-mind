import { flushPromises, mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { defineComponent } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import WorkflowDetailView from "./WorkflowDetailView.vue";

describe("WorkflowDetailView", () => {
  afterEach(() => vi.restoreAllMocks());

  it("renders a detail response using backend DTO field names", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      instance: {
        instanceId: "instance-1",
        processName: "入金流程",
        instanceTitle: "入金申请",
        starterUserName: "张三",
        instanceStatus: "RUNNING",
        currentNodeCodes: ["manager"],
        variables: { amount: 1200 },
        startedAt: "2026-08-10T09:00:00",
      },
      formFields: [{ fieldCode: "amount", fieldName: "金额", fieldType: "number" }],
      nodes: [
        { nodeCode: "apply", nodeName: "提交申请", positionX: 20, positionY: 20 },
        { nodeCode: "manager", nodeName: "经理审批", positionX: 120, positionY: 20 },
      ],
      edges: [],
      currentTask: {
        taskId: "task-1",
        instanceId: "instance-1",
        instanceTitle: "入金申请",
        nodeCode: "manager",
        taskVersion: 3,
        candidateUserIds: [],
      },
      activeTasks: [],
      historyTasks: [],
      comments: [],
      attachments: [],
      rejectTargetNodes: [{ nodeCode: "apply", nodeName: "提交申请", nodeType: "USER_TASK" }],
      allowedActions: ["APPROVE", "REJECT"],
      disabledActions: [],
    }), { status: 200, headers: { "Content-Type": "application/json" } })));

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/workflow/tasks/:taskId", component: WorkflowDetailView, props: { mode: "task" } },
        { path: "/workflow/instances/:instanceId", name: "workflow-instance-detail",
          component: defineComponent({ template: "<div />" }) },
      ],
    });
    await router.push("/workflow/tasks/task-1");
    await router.isReady();

    const wrapper = mount(WorkflowDetailView, {
      props: { mode: "task" },
      global: { plugins: [createPinia(), router] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain("入金申请");
    expect(wrapper.text()).toContain("经理审批");
    expect(wrapper.text()).toContain("金额");
    expect(wrapper.text()).toContain("1,200");
    expect(wrapper.text()).toContain("通过");
    expect(wrapper.get('[data-test="reject-target-node"]').text()).toContain("提交申请");
  });

  it("saves staged attachment replacements before submitting edited apply variables", async () => {
    const detail = {
      instance: {
        instanceId: "instance-1", processName: "入金", instanceTitle: "申请",
        instanceStatus: "RUNNING", currentNodeCodes: ["apply"],
        variables: { amount: 100, internalRoutingValue: "must-not-be-sent" },
      },
      formFields: [{
        fieldCode: "amount", fieldName: "金额", fieldType: "number", controlType: "number", required: true,
      }],
      nodes: [{ nodeCode: "apply", nodeName: "申请" }], edges: [],
      currentTask: {
        taskId: "apply-task", instanceId: "instance-1", instanceTitle: "申请",
        nodeCode: "apply", taskVersion: 5, candidateUserIds: ["sales01"],
      },
      activeTasks: [], historyTasks: [], comments: [],
      attachments: [{ attachmentId: "old-att", ownerType: "INSTANCE", fileName: "old.pdf" }],
      rejectTargetNodes: [], allowedActions: ["SUBMIT"], disabledActions: [],
    };
    const fetchMock = vi.fn(async (input: string | URL | Request, _init?: RequestInit) => {
      const url = String(input);
      if (url.includes("instance-attachments/old-att")) {
        return new Response(JSON.stringify({
          attachmentId: "new-att", ownerType: "INSTANCE", fileName: "new.pdf",
        }), { status: 200, headers: { "Content-Type": "application/json" } });
      }
      if (url.endsWith("/submit")) {
        return new Response(JSON.stringify({ replayed: false, archivedTasks: [], createdTasks: [], updatedTasks: [] }),
          { status: 200, headers: { "Content-Type": "application/json" } });
      }
      return new Response(JSON.stringify(detail), {
        status: 200, headers: { "Content-Type": "application/json" },
      });
    });
    vi.stubGlobal("fetch", fetchMock);
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/workflow/tasks/:taskId", component: WorkflowDetailView, props: { mode: "task" } },
        { path: "/workflow/instances/:instanceId", name: "workflow-instance-detail",
          component: defineComponent({ template: "<div />" }) },
      ],
    });
    await router.push("/workflow/tasks/apply-task");
    await router.isReady();
    const wrapper = mount(WorkflowDetailView, {
      props: { mode: "task" }, global: { plugins: [createPinia(), router] },
    });
    await flushPromises();

    await wrapper.get('input[type="number"]').setValue(250);
    const replacement = new File(["new"], "new.pdf", { type: "application/pdf" });
    const replacementInput = wrapper.get('input[aria-label="替换 old.pdf"]');
    Object.defineProperty(replacementInput.element, "files", { value: [replacement] });
    await replacementInput.trigger("change");
    await wrapper.get('[data-test="action-submit"]').trigger("click");
    await flushPromises();

    const urls = fetchMock.mock.calls.map((call) => String(call[0]));
    const replaceIndex = urls.findIndex((url) => url.includes("instance-attachments/old-att"));
    const submitIndex = urls.findIndex((url) => url.endsWith("/submit"));
    expect(replaceIndex).toBeGreaterThan(0);
    expect(submitIndex).toBeGreaterThan(replaceIndex);
    const actionBody = JSON.parse(fetchMock.mock.calls[submitIndex][1]?.body as string);
    expect(actionBody.variables).toEqual({ amount: 250 });
    expect(router.currentRoute.value.fullPath).toBe("/workflow/instances/instance-1");
  });

  it("blocks submit when a staged attachment replacement fails", async () => {
    const detail = {
      instance: {
        instanceId: "instance-1", instanceTitle: "申请", instanceStatus: "RUNNING",
        currentNodeCodes: ["apply"], variables: { amount: 100 },
      },
      formFields: [{
        fieldCode: "amount", fieldName: "金额", fieldType: "number", controlType: "number", required: true,
      }],
      nodes: [{ nodeCode: "apply", nodeName: "申请" }], edges: [],
      currentTask: {
        taskId: "apply-task", instanceId: "instance-1", instanceTitle: "申请",
        nodeCode: "apply", taskVersion: 5, candidateUserIds: ["sales01"],
      },
      activeTasks: [], historyTasks: [], comments: [],
      attachments: [{ attachmentId: "old-att", ownerType: "INSTANCE", fileName: "old.pdf" }],
      rejectTargetNodes: [], allowedActions: ["SUBMIT"], disabledActions: [],
    };
    const fetchMock = vi.fn(async (input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("instance-attachments/old-att")) {
        return new Response(JSON.stringify({ code: "ATTACHMENT_INVALID", message: "附件替换失败" }), {
          status: 422, headers: { "Content-Type": "application/json" },
        });
      }
      return new Response(JSON.stringify(detail), {
        status: 200, headers: { "Content-Type": "application/json" },
      });
    });
    vi.stubGlobal("fetch", fetchMock);
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/workflow/tasks/:taskId", component: WorkflowDetailView, props: { mode: "task" } }],
    });
    await router.push("/workflow/tasks/apply-task");
    await router.isReady();
    const wrapper = mount(WorkflowDetailView, {
      props: { mode: "task" }, global: { plugins: [createPinia(), router] },
    });
    await flushPromises();

    const replacementInput = wrapper.get('input[aria-label="替换 old.pdf"]');
    Object.defineProperty(replacementInput.element, "files", {
      value: [new File(["new"], "new.pdf", { type: "application/pdf" })],
    });
    await replacementInput.trigger("change");
    await wrapper.get('[data-test="action-submit"]').trigger("click");
    await flushPromises();

    expect(fetchMock.mock.calls.some((call) => String(call[0]).endsWith("/submit"))).toBe(false);
    expect(wrapper.text()).toContain("附件替换失败");
  });
});
