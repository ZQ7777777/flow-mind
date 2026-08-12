import { flushPromises, mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import AttachmentPanel from "../components/workflow/AttachmentPanel.vue";
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
      uploadableAttachments: [],
      rejectTargetNodes: [{ nodeCode: "apply", nodeName: "提交申请", nodeType: "USER_TASK" }],
      allowedActions: ["APPROVE", "REJECT"],
      disabledActions: [],
    }), { status: 200, headers: { "Content-Type": "application/json" } })));

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/workflow/tasks/:taskId", component: WorkflowDetailView, props: { mode: "task" } }],
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

  it("uploads task attachments with current instance id and task version", async () => {
    const detailResponse = {
      instance: {
        instanceId: "instance-1",
        processName: "入金流程",
        instanceTitle: "入金申请",
        starterUserName: "张三",
        instanceStatus: "RUNNING",
        currentNodeCodes: ["manager"],
        variables: {},
      },
      formFields: [],
      nodes: [{ nodeCode: "manager", nodeName: "经理审批" }],
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
      uploadableAttachments: [{
        attachmentCode: "approvalNote",
        attachmentName: "审批补充材料",
        ownerType: "TASK",
        allowedExtensions: ["txt"],
      }],
      rejectTargetNodes: [],
      allowedActions: [],
      disabledActions: [],
    };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(detailResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ attachmentId: "att-1" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify(detailResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }));
    vi.stubGlobal("fetch", fetchMock);

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/workflow/tasks/:taskId", component: WorkflowDetailView, props: { mode: "task" } }],
    });
    await router.push("/workflow/tasks/task-1");
    await router.isReady();

    const wrapper = mount(WorkflowDetailView, {
      props: { mode: "task" },
      global: { plugins: [createPinia(), router] },
    });
    await flushPromises();

    wrapper.findComponent(AttachmentPanel).vm.$emit("upload", {
      file: new File(["note"], "note.txt", { type: "text/plain" }),
      ownerType: "TASK",
      template: {
        attachmentCode: "approvalNote",
        attachmentName: "审批补充材料",
        ownerType: "TASK",
        allowedExtensions: ["txt"],
      },
    });
    await flushPromises();

    const [url, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    const body = init.body as FormData;
    expect(url).toContain("/api/workflow/tasks/task-1/attachments");
    expect(body.get("instanceId")).toBe("instance-1");
    expect(body.get("expectedTaskVersion")).toBe("3");
    expect(body.get("attachmentCode")).toBe("approvalNote");
  });
});
