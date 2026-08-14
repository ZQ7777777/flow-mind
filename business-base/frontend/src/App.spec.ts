import { mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { Pinia } from "pinia";
import { createMemoryHistory } from "vue-router";
import { createBusinessRouter } from "./router";
import { useAuthStore } from "./stores/auth";
import { useMessageStore } from "./stores/message";
import App from "./App.vue";

class FakeEventSource {
  url: string;
  onmessage: ((ev: { data: string }) => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;

  constructor(url: string) {
    this.url = url;
  }

  close(): void {
    this.closed = true;
  }
}

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

function buildUser(administrator: boolean) {
  return {
    userId: administrator ? "u_admin_01" : "u_sales_01",
    username: administrator ? "admin01" : "sales01",
    realName: administrator ? "管理员" : "张三",
    departmentId: administrator ? "dept_company" : "dept_sales",
    departmentName: administrator ? "公司" : "业务一部",
    userType: administrator ? "ADMIN" : "USER",
    administrator,
  } as const;
}

describe("App navigation", () => {
  let pinia: Pinia;

  beforeEach(() => {
    pinia = createPinia();
    setActivePinia(pinia);
    vi.stubGlobal("EventSource", FakeEventSource);
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(jsonResponse({ unreadCount: 0 })),
    );
  });

  afterEach(() => {
    useMessageStore().disconnectStream();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  async function mountApp(administrator = false) {
    const auth = useAuthStore();
    auth.user = buildUser(administrator);
    auth.initialized = true;
    const router = createBusinessRouter(undefined, createMemoryHistory());
    await router.push("/workflow/todo");
    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router],
        stubs: {
          RouterView: true,
        },
      },
    });
    return { router, wrapper };
  }

  it("shows the generated entry route before the common workflow navigation", async () => {
    const { wrapper } = await mountApp(false);

    expect(wrapper.findAll(".nav-link").map((link) => link.text())).toEqual([
      "入金申请",
      "我发起的",
      "我的待办",
      "我的已办",
      "我的已阅",
    ]);
    expect(wrapper.get('.nav-link[href="/generated/entry-application/apply"]').text()).toBe("入金申请");
    expect(wrapper.text()).toContain("张三");
    expect(wrapper.text()).toContain("业务一部");
    expect(wrapper.find('[data-test="logout"]').exists()).toBe(true);
  });

  it("resolves the generated entry link to the agent-generated page", async () => {
    const { router } = await mountApp(false);

    await router.push("/generated/entry-application/apply");

    expect(router.currentRoute.value.path).toBe("/generated/entry-application/apply");
    expect(router.currentRoute.value.name).toBe("generated-entry-application-apply");
  });

  it("shows administrator navigation entries only to administrators", async () => {
    const { wrapper } = await mountApp(true);
    const labels = wrapper.findAll(".nav-link").map((link) => link.text());

    expect(labels).toEqual([
      "入金申请",
      "我发起的",
      "我的待办",
      "我的已办",
      "我的已阅",
      "流程定义",
      "流程实例",
      "告警管理",
    ]);
    expect(wrapper.find('[data-test="admin-alerts-link"]').exists()).toBe(true);
  });

  it("renders the message entry without a badge when there are no unread messages", async () => {
    const { wrapper } = await mountApp(false);

    expect(wrapper.find('[data-test="messages-link"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="unread-badge"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="admin-alerts-link"]').exists()).toBe(false);
  });
});
