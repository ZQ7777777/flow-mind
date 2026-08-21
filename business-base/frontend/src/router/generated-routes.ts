import type { RouteRecordRaw } from "vue-router";
import type { Component } from "vue";

type GeneratedBusinessFormLoader = () => Promise<{ default: Component }>;

/**
 * Stable registration point for routes produced by Flow Mind.
 * Business generators may update this array but must not modify the main router.
 */
export const generatedRoutes: RouteRecordRaw[] = [
  {
    path: "/generated/warehouse-pledge/apply",
    name: "generated-warehouse-pledge-apply",
    meta: { title: "仓单、国债（解）质押申请", standalone: true },
    component: () => import("../modules/generated/warehouse-pledge/Apply.vue"),
  },
  {
    path: "/generated/pledge-application/apply",
    name: "generated-pledge-application-apply",
    meta: { title: "仓单/国债（解）质押申请", standalone: true },
    component: () => import("../modules/generated/pledge-application/Apply.vue"),
  },
  {
    path: "/generated/warehouse-treasury-pledge-application/apply",
    name: "generated-warehouse-treasury-pledge-application-apply",
    meta: { title: "发起仓单、国债（解）质押申请", standalone: true },
    component: () => import("../modules/generated/warehouse-treasury-pledge-application/Apply.vue"),
  },
];

export const generatedBusinessFormRegistry: Record<string, GeneratedBusinessFormLoader> = {
  warehouse_pledge: () => import("../modules/generated/warehouse-pledge/BusinessForm.vue"),
  pledge_application: () => import("../modules/generated/pledge-application/BusinessForm.vue"),
  warehouse_treasury_pledge_application: () => import("../modules/generated/warehouse-treasury-pledge-application/BusinessForm.vue"),
};

export async function resolveGeneratedBusinessForm(processCode?: string): Promise<Component | undefined> {
  if (!processCode) return undefined;
  const loader = generatedBusinessFormRegistry[processCode];
  if (!loader) return undefined;
  return (await loader()).default;
}
