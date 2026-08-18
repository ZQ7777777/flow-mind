import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { defineComponent } from "vue";
import WorkflowDetailView from "./WorkflowDetailView.vue";
import { useAuthStore } from "../stores/auth";

function detailResponse(overrides: Record<string, unknown> = {}) {
  return {
    instance: {
      instanceId: "instance-1",
      processName: "入金流程",
      instanceTitle: "入金申请",
      starterUserId: "starter-1",
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
      deadlineStatus: "DUE_SOON",
      dueAt: "2026-08-12T11:10:00",
    },
    activeTasks: [],
    historyTasks: [],
    comments: [],
    attachments: [],
    rejectTargetNodes: [{ nodeCode: "apply", nodeName: "提交申请", nodeType: "USER_TASK" }],
    allowedActions: ["APPROVE", "REJECT"],
    disabledActions: [],
    ...overrides,
  };
}

const leavingActions = [
  "APPROVE",
  "SUBMIT",
  "REJECT",
  "RETURN",
  "WITHDRAW",
  "DIRECT_SEND",
  "TRANSFER",
  "DELEGATE",
  "ADD_SIGN",
] as const;

function authenticatedPinia() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const auth = useAuthStore();
  auth.user = {
    userId: "user-1",
    username: "user01",
    realName: "测试用户",
    departmentId: "dept-1",
    departmentName: "测试部门",
    userType: "USER",
    administrator: false,
  };
  auth.initialized = true;
  return pinia;
}

describe("WorkflowDetailView", () => {
  afterEach(() => vi.restoreAllMocks());

    async function mountDetail(fetchMock: ReturnType<typeof vi.fn>) {
      vi.stubGlobal("fetch", fetchMock);
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/workflow/tasks/:taskId", component: WorkflowDetailView, props: { mode: "task" } },
        { path: "/workflow/instances/:instanceId", name: "workflow-instance-detail",
          component: defineComponent({ template: "<div />" }) },
        { path: "/workflow/todo", name: "workflow-todo",
          component: defineComponent({ template: "<div />" }) },
      ],
    });
    await router.push("/workflow/tasks/task-1");
    await router.isReady();

  const wrapper = mount(WorkflowDetailView, {
    props: { mode: "task" },
    global: { plugins: [authenticatedPinia(), router] },
  });
  await flushPromises();
  return wrapper;
}

describe("WorkflowDetailView", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => vi.restoreAllMocks());

  it("renders a detail response using backend DTO field names", async () => {
    const wrapper = await mountDetail(
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(detailResponse()), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );

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
      attachments: [{ attachmentId: "old-att", ownerType: "INSTANCE", fileName: "old.pdf", uploadedBy: "user-1" }],
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
        { path: "/workflow/todo", name: "workflow-todo",
          component: defineComponent({ template: "<div />" }) },
      ],
    });
    await router.push("/workflow/tasks/apply-task");
    await router.isReady();
    const wrapper = mount(WorkflowDetailView, {
      props: { mode: "task" }, global: { plugins: [authenticatedPinia(), router] },
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
    expect(router.currentRoute.value.fullPath).toBe("/workflow/todo");
  });

  it("immediately displays and downloads the latest staged replacement", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(detailResponse({
        currentTask: {
          taskId: "task-1", instanceId: "instance-1", instanceTitle: "入金申请",
          nodeCode: "apply", taskVersion: 3, candidateUserIds: [],
        },
        attachments: [{
          attachmentId: "old-att", ownerType: "INSTANCE", fileName: "1.pdf", sizeBytes: 3,
          uploadedBy: "user-1",
        }],
        allowedActions: ["SUBMIT"],
      })), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    const createObjectURL = vi.fn().mockReturnValue("blob:replacement");
    const revokeObjectURL = vi.fn();
    vi.stubGlobal("URL", { ...URL, createObjectURL, revokeObjectURL });
    let downloadedFileName = "";
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
      downloadedFileName = this.download;
    });
    const wrapper = await mountDetail(fetchMock);

    const secondFile = new File(["second"], "2.pdf", { type: "application/pdf" });
    const firstInput = wrapper.get('input[aria-label="替换 1.pdf"]');
    Object.defineProperty(firstInput.element, "files", { value: [secondFile], configurable: true });
    await firstInput.trigger("change");

    expect(wrapper.text()).not.toContain("1.pdf");
    expect(wrapper.text()).toContain("2.pdf");
    expect(wrapper.text()).toContain("6 B");
    expect(wrapper.get('input[aria-label="替换 2.pdf"]')).toBeTruthy();

    const thirdFile = new File(["third-file"], "3.pdf", { type: "application/pdf" });
    const secondInput = wrapper.get('input[aria-label="替换 2.pdf"]');
    Object.defineProperty(secondInput.element, "files", { value: [thirdFile], configurable: true });
    await secondInput.trigger("change");
    await wrapper.get(".attachment-list button").trigger("click");

    expect(wrapper.text()).not.toContain("2.pdf");
    expect(wrapper.text()).toContain("3.pdf");
    expect(createObjectURL).toHaveBeenCalledWith(thirdFile);
    expect(click).toHaveBeenCalledOnce();
    expect(downloadedFileName).toBe("3.pdf");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:replacement");
  });

  it("continues downloading unstaged attachments from the backend", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(detailResponse({
        attachments: [{ attachmentId: "saved-att", ownerType: "INSTANCE", fileName: "saved.pdf" }],
      })), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }))
      .mockResolvedValueOnce(new Response("saved-content", {
        status: 200,
        headers: { "Content-Type": "application/pdf" },
      }));
    const createObjectURL = vi.fn().mockReturnValue("blob:saved");
    vi.stubGlobal("URL", { ...URL, createObjectURL, revokeObjectURL: vi.fn() });
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
    const wrapper = await mountDetail(fetchMock);

    await wrapper.get(".attachment-list button").trigger("click");
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(String(fetchMock.mock.calls[1][0])).toContain("/api/workflow/attachments/saved-att/content");
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
  });

  it("does not delete an attachment when confirmation is cancelled", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(detailResponse({
      attachments: [{
        attachmentId: "att-current",
        taskId: "task-1",
        ownerType: "INSTANCE",
        fileName: "receipt.pdf",
        uploadedBy: "user-1",
      }],
    })), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    const wrapper = await mountDetail(fetchMock);

    await wrapper.get('button[aria-label="删除 receipt.pdf"]').trigger("click");
    await flushPromises();

    expect(confirm).toHaveBeenCalledWith("确认删除附件“receipt.pdf”？");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("receipt.pdf");
  });

  it("deletes a staged attachment and clears its local replacement", async () => {
    const detail = detailResponse({
      attachments: [{
        attachmentId: "att-current",
        taskId: "task-1",
        ownerType: "INSTANCE",
        fileName: "old.pdf",
        sizeBytes: 3,
        uploadedBy: "user-1",
      }],
      allowedActions: ["SUBMIT"],
    });
    let resolveDelete: ((response: Response) => void) | undefined;
    const fetchMock = vi.fn(async (input: string | URL | Request) => {
      if (String(input).endsWith("/attachments/att-current")) {
        return new Promise<Response>((resolve) => {
          resolveDelete = resolve;
        });
      }
      return new Response(JSON.stringify(detail), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const wrapper = await mountDetail(fetchMock);

    const replacement = new File(["new"], "new.pdf", { type: "application/pdf" });
    const replacementInput = wrapper.get('input[aria-label="替换 old.pdf"]');
    Object.defineProperty(replacementInput.element, "files", { value: [replacement] });
    await replacementInput.trigger("change");
    await wrapper.get('button[aria-label="删除 new.pdf"]').trigger("click");
    await wrapper.vm.$nextTick();

    expect(wrapper.get('button[aria-label="删除 new.pdf"]').text()).toBe("删除中…");
    expect(wrapper.get('button[aria-label="删除 new.pdf"]').attributes("disabled")).toBeDefined();

    resolveDelete?.(new Response(null, { status: 204 }));
    await flushPromises();

    const [, deleteInit] = fetchMock.mock.calls[1] as unknown as [string, RequestInit];
    expect(deleteInit.method).toBe("DELETE");
    expect((deleteInit.headers as Record<string, string>)["Idempotency-Key"]).toContain(
      "workflow:attachment-delete",
    );
    expect(wrapper.find(".attachment-list").exists()).toBe(false);
    expect(wrapper.text()).toContain("已删除 new.pdf");
  });

  it("keeps an attachment visible when deletion fails", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(detailResponse({
        attachments: [{
          attachmentId: "att-current",
          taskId: "task-1",
          ownerType: "TASK",
          fileName: "note.txt",
          uploadedBy: "user-1",
        }],
      })), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: "ATTACHMENT_SOURCE_TASK_INVALID",
        message: "附件删除失败",
      }), {
        status: 409,
        headers: { "Content-Type": "application/json" },
      }));
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const wrapper = await mountDetail(fetchMock);

    await wrapper.get('button[aria-label="删除 note.txt"]').trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("note.txt");
    expect(wrapper.get('[role="alert"]').text()).toContain("附件删除失败");
    expect(wrapper.get('button[aria-label="删除 note.txt"]').attributes("disabled")).toBeUndefined();
  });

  it.each(leavingActions)("returns to todo after %s succeeds", async (action) => {
    const detail = {
      instance: {
        instanceId: "instance-1", instanceTitle: "申请", instanceStatus: "RUNNING",
        currentNodeCodes: ["manager"], variables: {},
      },
      formFields: [], nodes: [], edges: [],
      currentTask: {
        taskId: "task-1", instanceId: "instance-1", instanceTitle: "申请",
        nodeCode: "manager", taskVersion: 3, candidateUserIds: ["manager01"],
      },
      activeTasks: [], historyTasks: [], comments: [], attachments: [],
      rejectTargetNodes: [], allowedActions: [action], disabledActions: [],
    };
    const fetchMock = vi.fn(async (input: string | URL | Request) => {
      const url = String(input);
      if (url.includes(`/api/workflow/tasks/task-1/`) && !url.endsWith("/task-1")) {
        return new Response(JSON.stringify({ archivedTasks: [], createdTasks: [], updatedTasks: [] }), {
          status: 200, headers: { "Content-Type": "application/json" },
        });
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
        { path: "/workflow/todo", name: "workflow-todo",
          component: defineComponent({ template: "<div />" }) },
      ],
    });
    await router.push("/workflow/tasks/task-1");
    await router.isReady();
    const wrapper = mount(WorkflowDetailView, {
      props: { mode: "task" },
      global: {
        plugins: [createPinia(), router],
        stubs: {
          TaskActionPanel: defineComponent({
            props: { allowedActions: { type: Array, required: true } },
            emits: ["submit"],
            template: `<button data-test="submit-stub" @click="$emit('submit', {
              action: allowedActions[0], expectedTaskVersion: 3, comment: '',
              targetNodeCode: 'apply', targetUserId: 'user-2', targetUserName: '李四',
              addSignUserIds: ['user-2']
            })">submit</button>`,
          }),
        },
      },
    });
    await flushPromises();

    await wrapper.get('[data-test="submit-stub"]').trigger("click");
    await flushPromises();

    expect(router.currentRoute.value.fullPath).toBe("/workflow/todo");
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it.each(["CLAIM", "UNCLAIM"] as const)("refreshes the current detail after %s succeeds", async (action) => {
    const detail = {
      instance: {
        instanceId: "instance-1", instanceTitle: "申请", instanceStatus: "RUNNING",
        currentNodeCodes: ["manager"], variables: {},
      },
      formFields: [], nodes: [], edges: [],
      currentTask: {
        taskId: "task-1", instanceId: "instance-1", instanceTitle: "申请",
        nodeCode: "manager", taskVersion: 3, candidateUserIds: ["manager01"],
      },
      activeTasks: [], historyTasks: [], comments: [], attachments: [],
      rejectTargetNodes: [], allowedActions: [action], disabledActions: [],
    };
    const fetchMock = vi.fn(async (input: string | URL | Request) => {
      const url = String(input);
      if (url.endsWith(`/${action.toLowerCase()}`)) {
        return new Response(JSON.stringify({ archivedTasks: [], createdTasks: [], updatedTasks: [] }), {
          status: 200, headers: { "Content-Type": "application/json" },
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
    await router.push("/workflow/tasks/task-1");
    await router.isReady();
    const wrapper = mount(WorkflowDetailView, {
      props: { mode: "task" }, global: { plugins: [createPinia(), router] },
    });
    await flushPromises();

    await wrapper.get(`[data-test="action-${action.toLowerCase()}"]`).trigger("click");
    await flushPromises();

    expect(router.currentRoute.value.fullPath).toBe("/workflow/tasks/task-1");
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("redirects a stale task link to todo only for FLOW_TASK_NOT_FOUND", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: "FLOW_TASK_NOT_FOUND", message: "active task does not exist",
    }), { status: 404, headers: { "Content-Type": "application/json" } })));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/workflow/tasks/:taskId", component: WorkflowDetailView, props: { mode: "task" } },
        { path: "/workflow/todo", name: "workflow-todo",
          component: defineComponent({ template: "<div />" }) },
      ],
    });
    await router.push("/workflow/tasks/stale-task");
    await router.isReady();
    mount(WorkflowDetailView, {
      props: { mode: "task" }, global: { plugins: [createPinia(), router] },
    });
    await flushPromises();

    expect(router.currentRoute.value.fullPath).toBe("/workflow/todo");
  });

  it("keeps non-task-not-found detail errors visible", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: "BUSINESS_ACCESS_DENIED", message: "无权查看该任务",
    }), { status: 403, headers: { "Content-Type": "application/json" } })));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/workflow/tasks/:taskId", component: WorkflowDetailView, props: { mode: "task" } },
        { path: "/workflow/todo", name: "workflow-todo",
          component: defineComponent({ template: "<div />" }) },
      ],
    });
    await router.push("/workflow/tasks/forbidden-task");
    await router.isReady();
    const wrapper = mount(WorkflowDetailView, {
      props: { mode: "task" }, global: { plugins: [createPinia(), router] },
    });
    await flushPromises();

    expect(router.currentRoute.value.fullPath).toBe("/workflow/tasks/forbidden-task");
    expect(wrapper.get('[role="alert"]').text()).toContain("无权查看该任务");
  });


  it("renders the approval detail with a collapsed timeline card and right-side actions", async () => {
    const wrapper = await mountDetail(
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(detailResponse({
          instance: {
            ...detailResponse().instance,
            instanceId: "RK202508110001",
            processName: "入金申请",
            instanceTitle: "入金申请-111",
            instanceStatus: "RUNNING",
          },
          comments: [{ commentId: "comment-1", operatorUserName: "李四", comment: "同意" }],
          historyTasks: [{ historyTaskId: "history-1", instanceId: "instance-1", nodeName: "经理审批" }],
        })), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );

    expect(wrapper.get(".detail-breadcrumb").text()).toContain("我的待办");
    expect(wrapper.get(".summary-card").text()).toContain("入金申请-111");
    expect(wrapper.get(".summary-card").text()).toContain("运行中");
    expect(wrapper.get(".summary-card").text()).toContain("RK202508110001");
    expect(wrapper.get(".detail-layout").classes()).toContain("has-assistant");
    expect(wrapper.get(".timeline-card").text()).toContain("流程轨迹");
    expect(wrapper.get(".timeline-card").text()).toContain("当前节点：经理审批");
    expect(wrapper.find(".timeline-card .timeline").exists()).toBe(false);
    expect(wrapper.find('[data-test="open-process-comments"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="open-process-graph"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="detail-dialog"]').exists()).toBe(false);
    expect(wrapper.get(".assistant-panel .action-panel").text()).toContain("办理动作");
    expect(wrapper.find(".detail-main .action-panel").exists()).toBe(false);
  });

  it.each([
    ["/workflow/instances/instance-1", "我发起的", "/workflow/started"],
    ["/workflow/instances/instance-1?from=completed", "我的已办", "/workflow/completed"],
    ["/workflow/instances/instance-1?from=read", "我的已阅", "/workflow/read"],
  ])("uses the source list label in instance breadcrumbs for %s", async (path, label, href) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      new Response(JSON.stringify(detailResponse({ currentTask: null })), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    ));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/workflow/instances/:instanceId", component: WorkflowDetailView, props: { mode: "instance" } }],
    });
    await router.push(path);
    await router.isReady();

    const wrapper = mount(WorkflowDetailView, {
      props: { mode: "instance" },
      global: { plugins: [authenticatedPinia(), router] },
    });
    await flushPromises();

    expect(wrapper.get(".detail-breadcrumb").text()).toContain(label);
    expect(wrapper.get(".detail-breadcrumb a").attributes("href")).toBe(href);
  });
  it("expands and collapses the timeline card with reached operation rows", async () => {
    const wrapper = await mountDetail(
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(detailResponse({
          nodes: [
            { nodeCode: "start", nodeName: "开始", nodeType: "START", sortOrder: 0 },
            { nodeCode: "apply", nodeName: "发起申请", sortOrder: 1 },
            { nodeCode: "manager", nodeName: "经理审批", sortOrder: 2 },
            { nodeCode: "finance", nodeName: "财务审核", sortOrder: 3 },
          ],
          instance: {
            ...detailResponse().instance,
            currentNodeCodes: ["manager"],
          },
          historyTasks: [
            { historyTaskId: "history-0", instanceId: "instance-1", nodeCode: "start", nodeName: "开始", actionType: "SUBMIT", assigneeUserName: "系统管理员", completedAt: "2026-08-11T10:16:00" },
            { historyTaskId: "history-1", instanceId: "instance-1", nodeCode: "apply", nodeName: "apply", actionType: "SUBMIT", assigneeUserName: "系统管理员", comment: "-", completedAt: "2026-08-11T10:17:00" },
            { historyTaskId: "history-2", instanceId: "instance-1", nodeCode: "manager", nodeName: "manager", actionType: "TRANSFER", assigneeUserName: "张三", comment: "转交李四处理", completedAt: "2026-08-11T10:21:00" },
          ],
          comments: [{ commentId: "comment-1", nodeCode: "manager", operatorUserName: "李四", comment: "请复核", createdAt: "2026-08-11T10:25:00" }],
        })), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );

    expect(wrapper.find('[data-test="detail-dialog"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="open-process-graph"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="open-process-comments"]').exists()).toBe(false);
    expect(wrapper.find(".timeline-card .timeline").exists()).toBe(false);
    expect(wrapper.get(".timeline-card").text()).toContain("当前节点：经理审批");

    await wrapper.get('[data-test="timeline-toggle"]').trigger("click");
    const timelineText = wrapper.get(".timeline-card .timeline").text();
    expect(timelineText).toContain("发起申请");
    expect(timelineText).toContain("已提交");
    expect(timelineText).toContain("系统管理员");
    expect(timelineText).toContain("经理审批");
    expect(timelineText).toContain("转办");
    expect(timelineText).toContain("转交李四处理");
    expect(timelineText).toContain("请复核");
    expect(timelineText).not.toContain("开始");
    expect(timelineText).not.toContain("财务审核");
    expect(timelineText).not.toContain("apply");
    expect(timelineText).not.toContain("manager");

    await wrapper.get(".timeline-card-header").trigger("click");
    expect(wrapper.find(".timeline-card .timeline").exists()).toBe(false);
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
      attachments: [{ attachmentId: "old-att", ownerType: "INSTANCE", fileName: "old.pdf", uploadedBy: "user-1" }],
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
      props: { mode: "task" }, global: { plugins: [authenticatedPinia(), router] },
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
    expect(wrapper.text()).toContain("new.pdf");
    expect(wrapper.text()).not.toContain("old.pdf");
  });
});

  it("shows starter reminder action on the initiated instance detail at any time", async () => {
    const instanceDetail = detailResponse({
      currentTask: null,
      activeTasks: [{
        taskId: "task-1",
        instanceId: "instance-1",
        instanceTitle: "入金申请",
        nodeCode: "manager",
        taskVersion: 3,
        candidateUserIds: ["manager01"],
      }],
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify(instanceDetail), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      )
      .mockResolvedValueOnce(new Response(JSON.stringify({ reminderId: "reminder-1" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify(instanceDetail), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );

    vi.stubGlobal("fetch", fetchMock);
    const pinia = createPinia();
    setActivePinia(pinia);
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/workflow/instances/:instanceId", component: WorkflowDetailView, props: { mode: "instance" } },
      ],
    });
    await router.push("/workflow/instances/instance-1");
    await router.isReady();

    const wrapper = mount(WorkflowDetailView, {
      props: { mode: "instance" },
      global: { plugins: [pinia, router] },
    });
    useAuthStore().user = {
      userId: "starter-1",
      username: "starter01",
      realName: "张三",
      departmentId: "dept-1",
      departmentName: "研发部",
      userType: "USER",
      administrator: false,
    };
    await flushPromises();
    await wrapper.vm.$nextTick();

    // 发起人在「我发起的」详情页可随时催办，无需等待超时告警。
    expect(wrapper.find('[data-test="deadline-banner"]').exists()).toBe(false);
    await wrapper.get('[data-test="remind-task"]').trigger("click");
    await flushPromises();

    const [url, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(url).toContain("/api/workflow/tasks/task-1/remind");
    expect(JSON.parse(init.body as string)).toMatchObject({ expectedTaskVersion: 3 });
    expect(wrapper.text()).toContain("催办已发送");
  });
});
