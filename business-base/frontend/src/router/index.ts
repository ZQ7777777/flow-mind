import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw, RouterHistory } from "vue-router";
import { useAuthStore } from "../stores/auth";
import AdminAlertView from "../views/AdminAlertView.vue";
import LoginView from "../views/LoginView.vue";
import MessageCenterView from "../views/MessageCenterView.vue";
import WorkflowDetailView from "../views/WorkflowDetailView.vue";
import WorkflowListView from "../views/WorkflowListView.vue";
import { generatedRoutes } from "./generated-routes";

export const baseRoutes: RouteRecordRaw[] = [
  { path: "/", redirect: "/workflow/todo" },
  {
    path: "/login",
    name: "login",
    component: LoginView,
    meta: { public: true },
  },
  {
    path: "/workflow/todo",
    name: "workflow-todo",
    component: WorkflowListView,
    props: { type: "todo", title: "我的待办" },
  },
  {
    path: "/workflow/completed",
    name: "workflow-completed",
    component: WorkflowListView,
    props: { type: "completed", title: "我的已办" },
  },
  {
    path: "/workflow/started",
    name: "workflow-started",
    component: WorkflowListView,
    props: { type: "started", title: "我发起的" },
  },
  {
    path: "/workflow/read",
    name: "workflow-read",
    component: WorkflowListView,
    props: { type: "read", title: "我的已阅" },
  },
  {
    path: "/messages",
    name: "messages",
    component: MessageCenterView,
  },
  {
    path: "/admin/alerts",
    name: "admin-alerts",
    component: AdminAlertView,
    meta: { requiresAdmin: true },
  },
  {
    path: "/workflow/tasks/:taskId",
    name: "workflow-task-detail",
    component: WorkflowDetailView,
    props: { mode: "task" },
  },
  {
    path: "/workflow/instances/:instanceId",
    name: "workflow-instance-detail",
    component: WorkflowDetailView,
    props: { mode: "instance" },
  },
  {
    path: "/admin/process-definitions",
    name: "admin-process-definitions",
    component: () => import("../views/admin/AdminProcessDefinitionsView.vue"),
    meta: { requiresAdmin: true },
  },
  {
    path: "/admin/process-instances",
    name: "admin-process-instances",
    component: () => import("../views/admin/AdminProcessInstancesView.vue"),
    meta: { requiresAdmin: true },
  },
];

export function createBusinessRouter(
  extraGeneratedRoutes: RouteRecordRaw[] = generatedRoutes,
  history: RouterHistory = createWebHistory(import.meta.env.BASE_URL),
) {
  const router = createRouter({
    history,
    routes: [...baseRoutes, ...extraGeneratedRoutes],
  });

  router.beforeEach(async (to) => {
    const auth = useAuthStore();
    if (to.meta.public === true) {
      return to.path === "/login" && auth.initialized && auth.authenticated
        ? "/workflow/todo"
        : true;
    }

    try {
      if (await auth.ensureAuthenticated()) {
        if (to.meta.requiresAdmin === true && !auth.user?.administrator) {
          return "/workflow/todo";
        }
        return true;
      }
    } catch {
      auth.clear();
    }

    return { path: "/login", query: { redirect: to.fullPath } };
  });

  return router;
}

const router = createBusinessRouter();

export default router;
