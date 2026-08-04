import type { RouteRecordRaw } from "vue-router";

/**
 * Stable registration point for routes produced by Flow Mind.
 * Business generators may update this array but must not modify the main router.
 */
export const generatedRoutes: RouteRecordRaw[] = [];
