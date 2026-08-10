import type { RouteRecordRaw } from "vue-router";
import { describe, expect, it } from "vitest";
import { baseRoutes, createBusinessRouter } from "./index";

describe("business router", () => {
  it("defines the common workflow routes required by B4", () => {
    expect(baseRoutes.map((route) => route.path)).toEqual(
      expect.arrayContaining([
        "/workflow/todo",
        "/workflow/completed",
        "/workflow/started",
        "/workflow/read",
        "/workflow/tasks/:taskId",
        "/workflow/instances/:instanceId",
      ]),
    );
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
});
