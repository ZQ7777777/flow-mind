import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuthStore } from "./auth";

describe("auth store", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("stores the authenticated user after login", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      userId: "u_sales_01", username: "sales01", realName: "张三",
      departmentId: "dept_sales", departmentName: "业务一部",
      userType: "USER", administrator: false,
    }), { status: 200, headers: { "Content-Type": "application/json" } })));
    const store = useAuthStore();

    const authenticated = await store.login("sales01", "123456");

    expect(authenticated).toBe(true);
    expect(store.user?.realName).toBe("张三");
    expect(store.authenticated).toBe(true);
  });

  it("treats a 401 current-user response as an anonymous session", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: "BUSINESS_AUTHENTICATION_REQUIRED", message: "请先登录",
    }), { status: 401, headers: { "Content-Type": "application/json" } })));
    const store = useAuthStore();

    expect(await store.ensureAuthenticated()).toBe(false);
    expect(store.user).toBeNull();
    expect(store.initialized).toBe(true);
  });
});
