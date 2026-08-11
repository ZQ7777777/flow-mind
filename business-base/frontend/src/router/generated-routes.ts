import type { RouteRecordRaw } from "vue-router";

/**
 * Stable registration point for routes produced by Flow Mind.
 * Business generators may update this array but must not modify the main router.
 */
export const generatedRoutes: RouteRecordRaw[] = [
  {
    path: "/generated/entry-application/apply",
    name: "generated-entry-application-apply",
    meta: { title: "入金申请" },
    component: () => import("../modules/generated/entry-application/EntryApplicationApply.vue"),
  },
];
