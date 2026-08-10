import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";
import WorkflowDetailView from "../views/WorkflowDetailView.vue";
import WorkflowListView from "../views/WorkflowListView.vue";
import { generatedRoutes } from "./generated-routes";

export const baseRoutes: RouteRecordRaw[] = [
  { path: "/", redirect: "/workflow/todo" },
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
    props: { type: "read", title: "已阅" },
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
];

export function createBusinessRouter(extraGeneratedRoutes: RouteRecordRaw[] = generatedRoutes) {
  return createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [...baseRoutes, ...extraGeneratedRoutes],
  });
}

const router = createBusinessRouter();

export default router;
