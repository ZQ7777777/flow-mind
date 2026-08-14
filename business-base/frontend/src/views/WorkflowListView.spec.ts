import { flushPromises, mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ElMessage } from "element-plus";
import WorkflowListView from "./WorkflowListView.vue";

function emptyPage() {
  return new Response(
    JSON.stringify({
      records: [],
      pageNo: 1,
      pageSize: 20,
      total: 0,
      totalPages: 0,
    }),
    { status: 200, headers: { "Content-Type": "application/json" } },
  );
}

function mountList(type = "todo") {
  return mount(WorkflowListView, {
    props: { type: type as "todo", title: "我的待办" },
    global: {
      plugins: [createPinia()],
      stubs: {
        RouterLink: {
          props: ["to"],
          template: '<a :href="to"><slot /></a>',
        },
      },
    },
  });
}

function entryProcess(name = "客户入金") {
  return new Response(
    JSON.stringify({ processCode: "entry_application", processName: name }),
    { status: 200, headers: { "Content-Type": "application/json" } },
  );
}

function workflowFetch(page: Response | undefined = emptyPage(), name = "客户入金") {
  return vi.fn((input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes("/api/workflow/startable-processes/entry-application")) {
      return Promise.resolve(entryProcess(name));
    }
    return Promise.resolve((page ?? emptyPage()).clone());
  });
}

const baseStubs = {
  RouterLink: { props: ["to"], template: '<a :href="to"><slot /></a>' },
  ElDrawer: { props: ["modelValue"], template: '<div v-if="modelValue"><slot /></div>' },
};

describe("WorkflowListView", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("shows the entry application launcher only on the started list", async () => {
    vi.stubGlobal("fetch", workflowFetch());

    const started = mount(WorkflowListView, {
      props: { type: "started", title: "我发起的" },
      global: {
        plugins: [createPinia()],
        stubs: {
          ...baseStubs,
          EntryApplicationApply: true,
        },
      },
    });
    await flushPromises();
    expect(started.find('[data-test="open-entry-application"]').exists()).toBe(true);
    expect(started.get('[data-test="open-entry-application"]').text()).toBe("发起客户入金");

    const todo = mount(WorkflowListView, {
      props: { type: "todo", title: "我的待办" },
      global: {
        plugins: [createPinia()],
        stubs: {
          ...baseStubs,
          EntryApplicationApply: true,
        },
      },
    });
    await flushPromises();
    expect(todo.find('[data-test="open-entry-application"]').exists()).toBe(false);
  });

  it("opens the entry application drawer and refreshes after generated form success", async () => {
    const fetchMock = workflowFetch(undefined, "客户入金");
    vi.stubGlobal("fetch", fetchMock);
    const successMessage = vi.spyOn(ElMessage, "success").mockImplementation(() => undefined as never);

    const wrapper = mount(WorkflowListView, {
      props: { type: "started", title: "我发起的" },
      global: {
        plugins: [createPinia()],
        stubs: {
          RouterLink: baseStubs.RouterLink,
          EntryApplicationApply: {
            data: () => ({ submitted: false }),
            template: '<form data-test="entry-application-form"><button data-test="submit-entry" type="button" @click="submitted = true">submit</button><p v-if="submitted" data-test="success-text">ok</p></form>',
          },
          ElDrawer: {
            props: ["modelValue"],
            emits: ["closed"],
            template: '<div v-if="modelValue" data-test="drawer"><slot /><button data-test="close-drawer" @click="$emit(\'closed\')">close</button></div>',
          },
        },
      },
    });
    await flushPromises();

    await wrapper.get('[data-test="open-entry-application"]').trigger("click");
    expect(wrapper.find('[data-test="entry-application-form"]').exists()).toBe(true);

    await wrapper.get('[data-test="submit-entry"]').trigger("click");
    await flushPromises();
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(successMessage).toHaveBeenCalledWith("客户入金已提交");
    expect(wrapper.text()).not.toContain("客户入金已提交");
  });

  it("separates own todo tasks from delegated todo tasks", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(emptyPage())
      .mockResolvedValueOnce(emptyPage());
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountList();
    await flushPromises();

    expect(fetchMock.mock.calls[0][0]).toContain("source=OWN");
    expect(wrapper.find('[data-test="todo-scope-own"]').classes()).toContain("active");

    await wrapper.find('[data-test="todo-scope-delegated"]').trigger("click");
    await flushPromises();

    expect(fetchMock.mock.calls[1][0]).toContain("source=DELEGATED");
    expect(wrapper.find('[data-test="todo-scope-delegated"]').classes()).toContain("active");
  });

  it("uses a todo-specific empty state", async () => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation(() => Promise.resolve(emptyPage())));

    const todo = mount(WorkflowListView, {
      props: { type: "todo", title: "我的待办" },
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: baseStubs.RouterLink, ElDrawer: baseStubs.ElDrawer },
      },
    });
    await flushPromises();
    expect(todo.find(".empty-cell").text()).toBe("暂无代办");

    const completed = mount(WorkflowListView, {
      props: { type: "completed", title: "我的已办" },
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: { props: ["to"], template: '<a :href="to"><slot /></a>' } },
      },
    });
    await flushPromises();
    expect(completed.find(".empty-cell").text()).toBe("暂无记录");
  });

  it("shows deadline status badges for due-soon and overdue todo tasks", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            records: [
              {
                taskId: "task-soon",
                instanceId: "instance-1",
                instanceTitle: "合同审批",
                nodeCode: "approve",
                taskVersion: 1,
                candidateUserIds: [],
                deadlineStatus: "DUE_SOON",
                dueAt: "2026-08-12T11:10:00",
              },
              {
                taskId: "task-overdue",
                instanceId: "instance-2",
                instanceTitle: "费用审批",
                nodeCode: "approve",
                taskVersion: 2,
                candidateUserIds: [],
                deadlineStatus: "OVERDUE",
                dueAt: "2026-08-12T09:10:00",
              },
            ],
            pageNo: 1,
            pageSize: 20,
            total: 2,
            totalPages: 1,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );

    const wrapper = mountList();
    await flushPromises();

    expect(wrapper.find('[data-test="deadline-task-soon"]').text()).toBe("即将超时");
    expect(wrapper.find('[data-test="deadline-task-overdue"]').text()).toBe("已超时");
  });

  it("keeps the list headers visible while data is loading", async () => {
    vi.stubGlobal("fetch", vi.fn(() => new Promise(() => undefined)));

    const wrapper = mount(WorkflowListView, {
      props: { type: "started", title: "我发起的" },
      global: {
        plugins: [createPinia()],
        stubs: {
          RouterLink: {
            props: ["to"],
            template: '<a :href="to"><slot /></a>',
          },
          ElDrawer: baseStubs.ElDrawer,
        },
      },
    });
    await wrapper.vm.$nextTick();

    expect(wrapper.find("thead").text()).toContain("标题");
    expect(wrapper.find("thead").text()).toContain("状态");
    expect(wrapper.find("tbody").text()).toContain("加载中");
  });

  it("withdraws only an eligible completed row and refreshes in place", async () => {
    const eligiblePage = new Response(JSON.stringify({
      records: [{
        historyTaskId: "history-1",
        instanceId: "instance-1",
        instanceTitle: "申请单",
        processName: "入金",
        actionType: "SEND",
        withdrawContext: {
          taskId: "manager-task",
          expectedTaskVersion: 3,
          targetNodeCode: "apply",
          targetNodeName: "申请",
        },
      }],
      pageNo: 1, pageSize: 20, total: 1, totalPages: 1,
    }), { status: 200, headers: { "Content-Type": "application/json" } });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(eligiblePage)
      .mockResolvedValueOnce(new Response(JSON.stringify({ replayed: false }), {
        status: 200, headers: { "Content-Type": "application/json" },
      }))
      .mockResolvedValueOnce(emptyPage());
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("confirm", vi.fn(() => true));
    const wrapper = mount(WorkflowListView, {
      props: { type: "completed", title: "我的已办" },
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: { props: ["to"], template: '<a :href="to"><slot /></a>' } },
      },
    });
    await flushPromises();

    await wrapper.get('[data-test="withdraw-completed-task"]').trigger("click");
    await flushPromises();

    expect(fetchMock.mock.calls[1][0]).toContain("/api/workflow/tasks/manager-task/withdraw");
    expect(JSON.parse(fetchMock.mock.calls[1][1].body as string)).toMatchObject({
      expectedTaskVersion: 3,
      comment: "",
    });
    expect(fetchMock.mock.calls[2][0]).toContain("/api/workflow/tasks/completed");
    expect(wrapper.text()).toContain("已撤回至 申请");
  });

  it("does not request withdrawal when confirmation is cancelled", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      records: [{
        historyTaskId: "history-1", instanceId: "instance-1", instanceTitle: "申请单",
        withdrawContext: { taskId: "manager-task", expectedTaskVersion: 3, targetNodeCode: "apply" },
      }],
      pageNo: 1, pageSize: 20, total: 1, totalPages: 1,
    }), { status: 200, headers: { "Content-Type": "application/json" } })));
    vi.stubGlobal("confirm", vi.fn(() => false));
    const wrapper = mount(WorkflowListView, {
      props: { type: "completed", title: "我的已办" },
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: { props: ["to"], template: '<a :href="to"><slot /></a>' } },
      },
    });
    await flushPromises();

    await wrapper.get('[data-test="withdraw-completed-task"]').trigger("click");
    await flushPromises();

    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(1);
  });
});
