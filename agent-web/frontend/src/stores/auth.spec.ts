import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { ApiError } from "../api";

const mocks = vi.hoisted(() => ({ apiRequest: vi.fn() }));
vi.mock("../api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api")>()),
  apiRequest: mocks.apiRequest,
}));

import { useAuthStore } from "./auth";

const admin = {
  userId: "u_admin_01", username: "admin01", realName: "系统管理员一",
  departmentId: "dept_company", departmentName: "总公司",
  userType: "ADMIN" as const, administrator: true,
};

describe("agent auth store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.apiRequest.mockReset();
  });

  it("initializes an authenticated administrator session", async () => {
    mocks.apiRequest.mockResolvedValue(admin);

    const store = useAuthStore();
    await store.initialize();

    expect(store.user).toEqual(admin);
    expect(store.authenticated).toBe(true);
    expect(store.forbidden).toBe(false);
  });

  it("distinguishes anonymous and non-administrator access", async () => {
    const store = useAuthStore();
    mocks.apiRequest.mockRejectedValueOnce(new ApiError(401, "AGENT_AUTHENTICATION_REQUIRED", "请先登录"));
    await store.initialize();
    expect(store.authenticated).toBe(false);
    expect(store.forbidden).toBe(false);
    expect(store.error).toBe("");

    mocks.apiRequest.mockRejectedValueOnce(new ApiError(403, "AGENT_ADMIN_REQUIRED", "仅管理员可以访问"));
    await store.login("sales01", "123456");
    expect(store.authenticated).toBe(false);
    expect(store.forbidden).toBe(true);
  });
});
