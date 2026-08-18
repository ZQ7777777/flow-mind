import { flushPromises, mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import { afterEach, describe, expect, it, vi } from "vitest";
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

const baseStubs = {
  RouterLink: { props: ["to"], template: '<a :href="to"><slot /></a>' },
};

describe("WorkflowListView", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("does not expose an entry application launcher from the started list", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(emptyPage()));

    const wrapper = mount(WorkflowListView, {
      props: { type: "started", title: "我发起的" },
      global: {
        plugins: [createPinia()],
        stubs: baseStubs,
      },
    });
    await flushPromises();

    expect(wrapper.find('[data-test="open-entry-application"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="entry-application-drawer"]').exists()).toBe(false);
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
        stubs: baseStubs,
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


  it("hides the todo task source column while preserving the detail link", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            records: [{
              taskId: "task-1",
              instanceId: "instance-1",
              instanceTitle: "合同审批",
              processName: "合同流程",
              starterUserName: "张三",
              nodeCode: "manager_approve",
              nodeName: "部门经理审批",
              taskVersion: 1,
              candidateUserIds: [],
            }],
            pageNo: 1,
            pageSize: 20,
            total: 1,
            totalPages: 1,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );

    const wrapper = mountList();
    await flushPromises();

    expect(wrapper.find("thead").text()).not.toContain("任务来源");
    expect(wrapper.find(".table-wrap").text()).not.toContain("自己的任务");
    expect(wrapper.get("a").attributes("href")).toBe("/workflow/tasks/task-1");
  });

  it("renders started list status, node and empty end time as user-facing text", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            records: [{
              instanceId: "instance-1",
              instanceTitle: "入金申请",
              processName: "入金流程",
              instanceStatus: "RUNNING",
              currentNodeCodes: ["manager_approve"],
              variables: {},
              startedAt: "2026-08-12T11:10:00",
              endedAt: null,
            }],
            pageNo: 1,
            pageSize: 20,
            total: 1,
            totalPages: 1,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );

    const wrapper = mount(WorkflowListView, {
      props: { type: "started", title: "我发起的" },
      global: {
        plugins: [createPinia()],
        stubs: baseStubs,
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain("进行中");
    expect(wrapper.text()).toContain("部门经理审批");
    expect(wrapper.text()).toContain("-");
    expect(wrapper.text()).not.toContain("RUNNING");
    expect(wrapper.text()).not.toContain("manager_approve");
    expect(wrapper.text()).not.toContain("--");
  });

  it("renders completed task action and read status with Chinese badges", async () => {
    const completedResponse = new Response(JSON.stringify({
      records: [{
        historyTaskId: "history-1",
        instanceId: "instance-1",
        instanceTitle: "申请单",
        processName: "入金",
        nodeCode: "manager_approve",
        nodeName: "部门经理审批",
        actionType: "REJECT",
        completedAt: "2026-08-12T11:10:00",
      }],
      pageNo: 1, pageSize: 20, total: 1, totalPages: 1,
    }), { status: 200, headers: { "Content-Type": "application/json" } });
    const readResponse = new Response(JSON.stringify({
      records: [{
        readRecordId: "read-1",
        instanceId: "instance-2",
        instanceTitle: "已阅单",
        processName: "入金",
        instanceStatus: "ARCHIVED",
        readAt: "2026-08-12T11:10:00",
      }],
      pageNo: 1, pageSize: 20, total: 1, totalPages: 1,
    }), { status: 200, headers: { "Content-Type": "application/json" } });
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(completedResponse)
      .mockResolvedValueOnce(readResponse));

    const completed = mount(WorkflowListView, {
      props: { type: "completed", title: "我的已办" },
      global: { plugins: [createPinia()], stubs: baseStubs },
    });
    await flushPromises();

    expect(completed.get('[data-test="action-history-1"]').text()).toBe("驳回");
    expect(completed.text()).not.toContain("REJECT");

    const read = mount(WorkflowListView, {
      props: { type: "read", title: "我的已阅" },
      global: { plugins: [createPinia()], stubs: baseStubs },
    });
    await flushPromises();

    expect(read.get('[data-test="status-read-1"]').text()).toBe("已归档");
    expect(read.text()).not.toContain("ARCHIVED");
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

    expect(wrapper.get('[data-test="withdraw-completed-task"]').classes()).toContain("text-action");

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

    expect(wrapper.get('[data-test="withdraw-completed-task"]').classes()).toContain("text-action");

    await wrapper.get('[data-test="withdraw-completed-task"]').trigger("click");
    await flushPromises();

    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(1);
  });
});
