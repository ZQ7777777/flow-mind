<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { generatedRoutes } from "./router/generated-routes";
import { useAuthStore } from "./stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const publicLayout = computed(() => route.meta.public === true);

const generatedEntryRoutes = computed(() =>
  generatedRoutes.map((route, index) => ({
    path: route.path,
    label: labelGeneratedRoute(route, index),
  })),
);

function labelGeneratedRoute(route: (typeof generatedRoutes)[number], index: number): string {
  if (typeof route.meta?.title === "string") {
    return route.meta.title;
  }
  if (typeof route.name === "string") {
    const businessCode = route.name
      .replace(/^generated-/, "")
      .replace(/-apply$/, "");
    return readableGeneratedLabel(businessCode);
  }
  return `生成录入 ${index + 1}`;
}

function readableGeneratedLabel(value: string): string {
  return value
    .split("-")
    .filter((part) => part.length > 0)
    .map((part) => part[0].toUpperCase() + part.slice(1))
    .join(" ");
}

async function logout(): Promise<void> {
  await auth.logout();
  await router.replace("/login");
}
</script>

<template>
  <RouterView v-if="publicLayout" />
  <div v-else class="business-app-shell">
    <header class="topbar">
      <div>
        <p class="eyebrow">Flow Mind</p>
        <h1>业务流程办理</h1>
      </div>
      <div class="user-panel" aria-label="当前用户">
        <div class="user-summary">
          <strong>{{ auth.user?.realName }}</strong>
          <span>{{ auth.user?.departmentName }}</span>
        </div>
        <button class="logout-button" data-test="logout" type="button" @click="logout">
          退出登录
        </button>
      </div>
    </header>

    <section class="status-bar ready" role="status" aria-live="polite">
      <span class="status-dot" aria-hidden="true"></span>
      <strong>就绪</strong>
      <span>业务系统前端运行中</span>
    </section>

    <div class="workspace">
      <nav class="side-nav" aria-label="功能导航">
        <RouterLink
          v-for="route in generatedEntryRoutes"
          :key="route.path"
          class="nav-link"
          :to="route.path"
        >
          {{ route.label }}
        </RouterLink>
        <RouterLink class="nav-link" to="/workflow/started">我发起的</RouterLink>
        <RouterLink class="nav-link" to="/workflow/todo">我的待办</RouterLink>
        <RouterLink class="nav-link" to="/workflow/completed">我的已办</RouterLink>
        <RouterLink class="nav-link" to="/workflow/read">我的已阅</RouterLink>
      </nav>

      <main class="main-panel">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.business-app-shell {
  --ink: #17202a;
  --muted: #5d6978;
  --line: #d8dee8;
  --panel: #ffffff;
  --canvas: #f5f7fb;
  --nav: #111827;
  --blue: #2563eb;
  --teal: #0f766e;
  --green: #15803d;
  --focus: #f59e0b;
  min-height: 100vh;
  background: #eef2f7;
  color: var(--ink);
  font-family: "Segoe UI", "Microsoft YaHei", Arial, sans-serif;
}

.business-app-shell,
.business-app-shell * {
  box-sizing: border-box;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 24px;
  border-bottom: 1px solid var(--line);
  background: #fff;
}

.topbar h1 {
  margin: 0;
  font-size: 24px;
  line-height: 1.2;
}

.eyebrow {
  margin: 0 0 4px;
  color: var(--teal);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}

.user-panel {
  display: flex;
  align-items: center;
  gap: 14px;
}

.user-summary {
  display: grid;
  gap: 2px;
  min-width: 100px;
  text-align: right;
}

.user-summary strong {
  color: var(--ink);
  font-size: 14px;
}

.user-summary span {
  color: var(--muted);
  font-size: 12px;
}

.logout-button {
  min-height: 32px;
  border: 1px solid #b9c2cf;
  border-radius: 5px;
  padding: 5px 11px;
  background: #fff;
  color: #374151;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.logout-button:hover {
  border-color: var(--teal);
  color: var(--teal);
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 8px 24px;
  border-bottom: 1px solid var(--line);
  background: #f8fafc;
  color: var(--muted);
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--green);
}

.status-bar strong {
  color: var(--ink);
}

.workspace {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr);
  min-height: calc(100vh - 115px);
  align-items: stretch;
}

.side-nav {
  display: grid;
  align-content: start;
  width: 210px;
  gap: 6px;
  padding: 18px 14px;
  background: var(--nav);
}

.nav-link {
  min-height: 34px;
  border: 1px solid transparent;
  border-radius: 6px;
  padding: 7px 12px;
  background: transparent;
  color: #d1d5db;
  font-weight: 600;
  text-align: left;
  text-decoration: none;
}

.nav-link:hover {
  border-color: #374151;
  color: #fff;
}

.nav-link:focus-visible {
  outline: 3px solid var(--focus);
  outline-offset: 2px;
}

.nav-link.router-link-active {
  border-color: var(--teal);
  background: #1f2937;
  color: #fff;
}

.main-panel {
  display: grid;
  align-content: start;
  align-items: start;
  gap: 16px;
  min-width: 0;
  padding: 18px;
}

.main-panel :deep(.page-surface) {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 16px;
  background: var(--panel);
}

@media (max-width: 960px) {
  .topbar {
    align-items: stretch;
    flex-direction: column;
  }

  .user-panel {
    justify-content: space-between;
  }

  .user-summary {
    text-align: left;
  }

  .workspace {
    grid-template-columns: 1fr;
  }

  .side-nav {
    grid-template-columns: repeat(4, minmax(120px, 1fr));
    width: auto;
    overflow-x: auto;
  }
}
</style>
