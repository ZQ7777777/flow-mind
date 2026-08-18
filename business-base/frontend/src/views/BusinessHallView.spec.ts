import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { defineComponent } from "vue";
import { useAuthStore } from "../stores/auth";
import BusinessHallView from "./BusinessHallView.vue";

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

describe("BusinessHallView", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    const auth = useAuthStore();
    auth.user = {
      userId: "u_sales_01",
      username: "sales01",
      realName: "张三",
      departmentId: "dept_sales",
      departmentName: "业务一部",
      userType: "USER",
      administrator: false,
    };
    auth.initialized = true;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("renders the home structure, configured business cards, and todo summary", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        {
          definitionId: "definition-entry",
          processCode: "entry_application",
          processName: "客户入金",
          entryDisplayName: "入金申请",
          entryPageUrl: "/generated/entry-application/apply",
          entrySource: "AGENT_GENERATED",
          enabled: true,
        },
        {
          definitionId: "definition-hidden",
          processCode: "hidden",
          processName: "隐藏流程",
          entryPageUrl: "/hidden",
          enabled: false,
        },
      ]))
      .mockResolvedValueOnce(jsonResponse({
        records: [
          {
            taskId: "task-1",
            instanceId: "instance-1",
            instanceTitle: "入金申请（RK202405210001）",
            starterUserName: "李四",
            nodeCode: "finance",
            nodeName: "财务审核",
            candidateUserIds: [],
            taskVersion: 1,
            createdAt: "2024-05-21T10:30:00",
          },
        ],
        pageNo: 1,
        pageSize: 5,
        total: 8,
        totalPages: 2,
      }));
    vi.stubGlobal("fetch", fetchMock);
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/business-hall", component: BusinessHallView },
        { path: "/generated/entry-application/apply", component: defineComponent({ template: "<div>entry</div>" }) },
        { path: "/workflow/todo", component: defineComponent({ template: "<div>todo</div>" }) },
        { path: "/workflow/tasks/:taskId", component: defineComponent({ template: "<div>task</div>" }) },
      ],
    });
    await router.push("/business-hall");
    await router.isReady();

    const wrapper = mount(BusinessHallView, { global: { plugins: [router] } });
    await flushPromises();

    expect(vi.mocked(fetch).mock.calls[0][0]).toBe("/api/workflow/process-entry-links");
    expect(String(vi.mocked(fetch).mock.calls[1][0])).toContain("/api/workflow/tasks/todo");
    expect(String(vi.mocked(fetch).mock.calls[1][0])).toContain("pageSize=5");
    expect(wrapper.text()).toContain("您好，张三 👋");
    expect(wrapper.text()).toContain("欢迎使用业务大厅");
    expect(wrapper.text()).toContain("业务申请");
    expect(wrapper.text()).toContain("全部业务");
    expect(wrapper.text()).toContain("入金申请");
    expect(wrapper.text()).toContain("出金申请");
    expect(wrapper.text()).toContain("报销申请");
    expect(wrapper.text()).toContain("付款申请");
    expect(wrapper.text()).toContain("借款申请");
    expect(wrapper.text()).not.toContain("隐藏流程");
    expect(wrapper.find('[data-test="business-card-deposit"] .business-icon svg').exists()).toBe(true);
    expect(wrapper.find(".welcome-visual svg").exists()).toBe(true);
    expect(wrapper.text()).toContain("待办事项");
    expect(wrapper.text()).toContain("8");
    expect(wrapper.text()).toContain("入金申请（RK202405210001）");
    expect(wrapper.text()).toContain("李四");
    expect(wrapper.text()).toContain("财务审核");

    await wrapper.get('[data-test="business-card-deposit"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/generated/entry-application/apply");
  });

  it("keeps business cards without page urls disabled", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { definitionId: "definition-empty", processCode: "manual_process", processName: "手工流程", enabled: true },
      ]))
      .mockResolvedValueOnce(jsonResponse({
        records: [],
        pageNo: 1,
        pageSize: 5,
        total: 0,
        totalPages: 0,
      })));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/business-hall", component: BusinessHallView },
        { path: "/workflow/todo", component: defineComponent({ template: "<div>todo</div>" }) },
      ],
    });
    await router.push("/business-hall");
    await router.isReady();

    const wrapper = mount(BusinessHallView, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.get('[data-test="business-card-deposit"]').attributes("disabled")).toBeDefined();
    expect(wrapper.get('[data-test="business-card-deposit"]').attributes("title")).toBe("尚未配置入口页面地址");
  });
});



