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
    userId: "u_sales_01",
    username: "sales01",
    realName: "张三",
    departmentId: "dept_sales",
    departmentName: "业务一部",
    userType: administrator ? "ADMIN" : "USER",
    administrator,
  } as const;
}

describe("App navigation", () => {
  let pinia: Pinia;

  beforeEach(() => {
    pinia = createPinia();
  it("shows the primary navigation and the authenticated user in the business shell", async () => {
    const pinia = createPinia();
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

  function mountApp(administrator = false) {
    const auth = useAuthStore();
    auth.user = buildUser(administrator);
    auth.initialized = true;
    const router = createBusinessRouter([], createMemoryHistory());
    void router.push("/workflow/todo");
    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router],
        stubs: {
          RouterLink: {
            props: ["to"],
            template: '<a :href="to"><slot /></a>',
          },
          RouterView: true,
        },
      },
    });
    return { auth, wrapper };
  }

  it("shows generated routes and the authenticated user in the business shell", async () => {
    const { wrapper } = mountApp(false);

    expect(wrapper.findAll(".nav-link").map((link) => link.text())).toEqual([
      "我发起的",
      "我的待办",
      "我的已办",
      "我的已阅",
    ]);
    expect(wrapper.text()).toContain("张三");
    expect(wrapper.text()).toContain("业务一部");
    expect(wrapper.find('[data-test="logout"]').exists()).toBe(true);
  });

  it("shows both administrator navigation entries only to administrators", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const auth = useAuthStore();
    auth.user = {
      userId: "u_admin_01", username: "admin01", realName: "管理员",
      departmentId: "dept_company", departmentName: "公司",
      userType: "ADMIN", administrator: true,
    };
    auth.initialized = true;
    const router = createBusinessRouter([], createMemoryHistory());
    await router.push("/workflow/todo");
    const wrapper = mount(App, { global: { plugins: [pinia, router], stubs: { RouterLink: { props: ["to"], template: '<a class="nav-link" :href="to"><slot /></a>' }, RouterView: true } } });
    const labels = wrapper.findAll(".nav-link").map((link) => link.text());
    expect(labels).toContain("流程定义");
    expect(labels).toContain("流程实例");
  });

  it("renders the message entry with the unread count badge", async () => {
    const { wrapper } = mountApp(false);

    expect(wrapper.find('[data-test="messages-link"]').exists()).toBe(true);
    // 未读数为 0 时不展示徽标。
    expect(wrapper.find('[data-test="unread-badge"]').exists()).toBe(false);
    // 非管理员不展示告警入口。
    expect(wrapper.find('[data-test="admin-alerts-link"]').exists()).toBe(false);
  });

  it("shows the admin alerts entry only for administrators", async () => {
    const { wrapper } = mountApp(true);

    expect(wrapper.find('[data-test="admin-alerts-link"]').exists()).toBe(true);
    expect(wrapper.findAll(".nav-link").map((link) => link.text())).toEqual([
      "入金申请",
      "我发起的",
      "我的待办",
      "我的已办",
      "我的已阅",
      "告警管理",
    ]);
  });
});
