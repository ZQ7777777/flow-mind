import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory } from "vue-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createBusinessRouter } from "../router";
import LoginView from "./LoginView.vue";

describe("LoginView", () => {
  afterEach(() => vi.restoreAllMocks());

  it("logs in and returns to a safe requested route", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      userId: "u_sales_01", username: "sales01", realName: "张三",
      departmentId: "dept_sales", departmentName: "业务一部",
      userType: "USER", administrator: false,
    }), { status: 200, headers: { "Content-Type": "application/json" } })));
    const pinia = createPinia();
    setActivePinia(pinia);
    const router = createBusinessRouter([], createMemoryHistory());
    await router.push("/login?redirect=/workflow/started");
    await router.isReady();
    const wrapper = mount(LoginView, { global: { plugins: [pinia, router] } });

    await wrapper.find('input[name="username"]').setValue("sales01");
    await wrapper.find('input[name="password"]').setValue("123456");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(router.currentRoute.value.fullPath).toBe("/workflow/started");
  });

  it("shows the backend generic authentication error", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: "BUSINESS_AUTHENTICATION_REQUIRED", message: "用户名或密码错误",
    }), { status: 401, headers: { "Content-Type": "application/json" } })));
    const pinia = createPinia();
    setActivePinia(pinia);
    const router = createBusinessRouter([], createMemoryHistory());
    await router.push("/login");
    const wrapper = mount(LoginView, { global: { plugins: [pinia, router] } });

    await wrapper.find('input[name="username"]').setValue("sales01");
    await wrapper.find('input[name="password"]').setValue("wrong");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toBe("用户名或密码错误");
  });
});
