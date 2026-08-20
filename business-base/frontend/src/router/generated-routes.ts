import type { RouteRecordRaw } from "vue-router";
import type { Component } from "vue";

type GeneratedBusinessFormLoader = () => Promise<{ default: Component }>;

/**
 * Stable registration point for routes produced by Flow Mind.
 * Business generators may update this array but must not modify the main router.
 */
export const generatedRoutes: RouteRecordRaw[] = [
  {
    path: "/generated/entry-application/apply",
    name: "generated-entry-application-apply",
    meta: { title: "入金申请", standalone: true },
    component: () => import("../modules/generated/entry-application/EntryApplicationApply.vue"),
  },
  {
    path: "/generated/warehouse-pledge/apply",
    name: "generated-warehouse-pledge-apply",
    meta: { title: "仓单、国债（解）质押申请", standalone: true },
    component: () => import("../modules/generated/sample/Apply.vue"),
  },
];

export const generatedBusinessFormRegistry: Record<string, GeneratedBusinessFormLoader> = {
  entry_application: () => import("../modules/generated/entry-application/BusinessForm.vue"),
  warehouse_pledge: () => import("../modules/generated/sample/BusinessForm.vue"),
};

export async function resolveGeneratedBusinessForm(processCode?: string): Promise<Component | undefined> {
  if (!processCode) return undefined;
  const loader = generatedBusinessFormRegistry[processCode];
  if (!loader) return undefined;
  return (await loader()).default;
}
