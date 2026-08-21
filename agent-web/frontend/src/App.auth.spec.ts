import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, shallowMount } from "@vue/test-utils";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import { ApiError } from "./api";

const mocks = vi.hoisted(() => ({ apiRequest: vi.fn(), streamEvents: vi.fn() }));
vi.mock("./api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api")>()),
  apiRequest: mocks.apiRequest,
  streamEvents: mocks.streamEvents,
}));

import App from "./App.vue";

const admin = {
  userId: "u_admin_01", username: "admin01", realName: "系统管理员一",
  departmentId: "dept_company", departmentName: "总公司",
  userType: "ADMIN" as const, administrator: true,
};

describe("App administrator access", () => {
  beforeEach(() => {
    localStorage.clear();
    mocks.apiRequest.mockReset();
    mocks.streamEvents.mockReset();
  });

  it("renders the login page when there is no business session", async () => {
    mocks.apiRequest.mockRejectedValue(new ApiError(401, "AGENT_AUTHENTICATION_REQUIRED", "请先登录"));
    const wrapper = shallowMount(App, { global: { plugins: [createPinia(), ElementPlus] } });
    await flushPromises();

    expect(wrapper.findComponent({ name: "AdminLogin" }).exists()).toBe(true);
    expect(wrapper.find(".topbar").exists()).toBe(false);
  });

  it("initializes the workflow with the authenticated administrator", async () => {
    mocks.apiRequest.mockImplementation(async (path: string) => {
      if (path === "/api/agent/auth/me") return admin;
      if (path === "/api/agent/config") {
        return {
          defaultTargetRoot: "D:\\flow-platform\\flow-mind\\business-base",
          businessFrontendBaseUrl: "http://127.0.0.1:5174",
        };
      }
      throw new Error(`unexpected request: ${path}`);
    });
    const wrapper = shallowMount(App, { global: { plugins: [createPinia(), ElementPlus] } });
    await flushPromises();

    expect(wrapper.text()).toContain("系统管理员一");
    expect(wrapper.find('[aria-label="查看历史会话"]').exists()).toBe(true);
    expect(mocks.apiRequest).toHaveBeenCalledWith("/api/agent/config");
  });
});
