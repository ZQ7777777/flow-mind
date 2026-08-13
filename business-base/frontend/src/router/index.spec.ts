import type { RouteRecordRaw } from "vue-router";
import { createMemoryHistory } from "vue-router";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, describe, expect, it, vi } from "vitest";
import { baseRoutes, createBusinessRouter } from "./index";
import { useAuthStore } from "../stores/auth";

function mockMe(administrator: boolean) {
  return vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        userId: administrator ? "admin-1" : "u1",
        username: administrator ? "admin" : "user",
        realName: administrator ? "管理员" : "用户",
        departmentId: "d",
        departmentName: "D",
        userType: administrator ? "ADMIN" : "USER",
        administrator,
      }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    ),
  );
}

describe("business router", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("defines the common workflow routes required by B4", () => {
    expect(baseRoutes.map((route) => route.path)).toEqual(
      expect.arrayContaining([
        "/workflow/todo",
        "/workflow/completed",
        "/workflow/started",
        "/workflow/read",
        "/workflow/tasks/:taskId",
        "/workflow/instances/:instanceId",
        "/admin/process-definitions",
        "/admin/process-instances",
      ]),
    );

    const readRoute = baseRoutes.find((route) => route.path === "/workflow/read");
    expect(readRoute?.props).toEqual({ type: "read", title: "我的已阅" });
  });

  it("redirects an authenticated non-administrator away from admin routes", async () => {
    setActivePinia(createPinia());
    const auth = useAuthStore();
    auth.user = {
      userId: "u_sales_01", username: "sales01", realName: "张三",
      departmentId: "dept_sales", departmentName: "业务部",
      userType: "USER", administrator: false,
    };
    auth.initialized = true;
    const router = createBusinessRouter([], createMemoryHistory());
    await router.push("/admin/process-definitions");
    await router.isReady();
    expect(router.currentRoute.value.path).toBe("/workflow/todo");
  });

  it("redirects anonymous protected navigation to login", async () => {
    setActivePinia(createPinia());
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: "BUSINESS_AUTHENTICATION_REQUIRED", message: "请先登录",
    }), { status: 401, headers: { "Content-Type": "application/json" } })));
    const router = createBusinessRouter([], createMemoryHistory());

    await router.push("/workflow/todo");
    await router.isReady();

    expect(router.currentRoute.value.path).toBe("/login");
    expect(router.currentRoute.value.query.redirect).toBe("/workflow/todo");
  });

  it("statically merges generated routes into the application router", () => {
    const generatedRoute: RouteRecordRaw = {
      path: "/generated/demo/apply",
      name: "generated-demo-apply",
      component: { template: "<div>generated demo</div>" },
    };

    const router = createBusinessRouter([generatedRoute]);

    expect(router.getRoutes().map((route) => route.path)).toContain(
      "/generated/demo/apply",
    );
  });

  it("admits administrators to the admin alerts route", async () => {
    setActivePinia(createPinia());
    vi.stubGlobal("fetch", mockMe(true));
    const router = createBusinessRouter([], createMemoryHistory());

    await router.push("/admin/alerts");
    await router.isReady();

    expect(router.currentRoute.value.path).toBe("/admin/alerts");
  });

  it("redirects non-administrators away from the admin alerts route", async () => {
    setActivePinia(createPinia());
    vi.stubGlobal("fetch", mockMe(false));
    const router = createBusinessRouter([], createMemoryHistory());

    await router.push("/admin/alerts");
    await router.isReady();

    expect(router.currentRoute.value.path).toBe("/workflow/todo");
  });
});
