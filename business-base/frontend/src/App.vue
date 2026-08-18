<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import ToastHost from "./components/ToastHost.vue";
import { useAuthStore } from "./stores/auth";
import { useMessageStore } from "./stores/message";
import MessageCenterView from "./views/MessageCenterView.vue";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const messageStore = useMessageStore();
const messagePanelOpen = ref(false);
const messagePanelSize = ref({ width: 420, height: 560 });
const resizeState = ref<{
  edge: string;
  startX: number;
  startY: number;
  startWidth: number;
  startHeight: number;
} | null>(null);
const publicLayout = computed(() => route.meta.public === true);
const standaloneLayout = computed(() => route.meta.standalone === true);
const isAdmin = computed(() => auth.user?.administrator === true);

watch(
  () => (auth.initialized ? auth.user?.userId ?? null : null),
  (userId, previousUserId) => {
    if (!userId) {
      messagePanelOpen.value = false;
      messageStore.clearState();
      return;
    }
    if (previousUserId !== userId) {
      messageStore.clearState();
    }
    void messageStore.refreshUnreadCount();
  },
  { immediate: true },
);

const messagePanelStyle = computed(() => ({
  width: `${messagePanelSize.value.width}px`,
  height: `${messagePanelSize.value.height}px`,
}));

const resizeHandles = ["n", "e", "s", "w", "ne", "nw", "se", "sw"] as const;

function toggleMessagePanel(): void {
  messagePanelOpen.value = !messagePanelOpen.value;
}

function closeMessagePanel(): void {
  messagePanelOpen.value = false;
}

function startResize(edge: string, event: PointerEvent): void {
  event.preventDefault();
  event.stopPropagation();
  resizeState.value = {
    edge,
    startX: event.clientX,
    startY: event.clientY,
    startWidth: messagePanelSize.value.width,
    startHeight: messagePanelSize.value.height,
  };
  window.addEventListener("pointermove", resizeMessagePanel);
  window.addEventListener("pointerup", stopResizeMessagePanel);
}

function resizeMessagePanel(event: PointerEvent): void {
  const state = resizeState.value;
  if (!state) return;
  const deltaX = event.clientX - state.startX;
  const deltaY = event.clientY - state.startY;
  const maxWidth = Math.max(320, window.innerWidth - 32);
  const maxHeight = Math.max(360, window.innerHeight - 96);
  let width = state.startWidth;
  let height = state.startHeight;
  if (state.edge.includes("e")) width += deltaX;
  if (state.edge.includes("w")) width -= deltaX;
  if (state.edge.includes("s")) height += deltaY;
  if (state.edge.includes("n")) height -= deltaY;
  messagePanelSize.value = {
    width: Math.min(Math.max(width, 320), maxWidth),
    height: Math.min(Math.max(height, 360), maxHeight),
  };
}

function resizeMessagePanel(event: PointerEvent): void {
  const state = resizeState.value;
  if (!state) return;
  const deltaX = event.clientX - state.startX;
  const deltaY = event.clientY - state.startY;
  const maxWidth = Math.max(320, window.innerWidth - 32);
  const maxHeight = Math.max(360, window.innerHeight - 96);
  let width = state.startWidth;
  let height = state.startHeight;
  if (state.edge.includes("e")) width += deltaX;
  if (state.edge.includes("w")) width -= deltaX;
  if (state.edge.includes("s")) height += deltaY;
  if (state.edge.includes("n")) height -= deltaY;
  messagePanelSize.value = {
    width: Math.min(Math.max(width, 320), maxWidth),
    height: Math.min(Math.max(height, 360), maxHeight),
  };
}

function stopResizeMessagePanel(): void {
  resizeState.value = null;
  window.removeEventListener("pointermove", resizeMessagePanel);
  window.removeEventListener("pointerup", stopResizeMessagePanel);
}
function readableGeneratedLabel(value: string): string {
  return value
      .split("-")
      .filter((part) => part.length > 0)
      .map((part) => part[0].toUpperCase() + part.slice(1))
      .join(" ");
}

onBeforeUnmount(() => {
  stopResizeMessagePanel();
});

onBeforeUnmount(() => {
  stopResizeMessagePanel();
});

async function logout(): Promise<void> {
  await auth.logout();
  await router.replace("/login");
}
</script>

<template>
  <RouterView v-if="publicLayout || standaloneLayout" />
  <div v-else class="business-app-shell">
    <ToastHost />
    <header class="topbar">
      <div>
        <p class="eyebrow">Flow Mind</p>
        <h1>业务大厅</h1>
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
        <template v-if="auth.user?.administrator">
          <div class="nav-divider" aria-hidden="true"></div>
          <RouterLink class="nav-link" to="/admin/process-definitions">流程定义</RouterLink>
          <RouterLink class="nav-link" to="/admin/process-instances">流程实例</RouterLink>
          <RouterLink class="nav-link" data-test="admin-alerts-link" to="/admin/alerts">告警管理</RouterLink>
        </template>
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

.message-popover {
  position: relative;
}

.message-entry {
  display: inline-grid;
  place-items: center;
  position: relative;
  width: 34px;
  min-height: 32px;
  border: 1px solid #b9c2cf;
  border-radius: 5px;
  padding: 0;
  background: #fff;
  color: #374151;
  font: inherit;
  font-size: 17px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
}

.message-entry:hover {
  border-color: var(--teal);
  color: var(--teal);
}

.message-popover-backdrop {
  position: fixed;
  inset: 0;
  z-index: 20;
}

.message-popover-panel {
  position: absolute;
  top: 70px;
  right: 24px;
  min-width: 320px;
  min-height: 360px;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 96px);
  overflow: visible;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.18);
}

.message-popover-panel :deep(.message-center.is-popup) {
  width: 100%;
  height: 100%;
  overflow: auto;
}

.message-resize-handle {
  position: absolute;
  z-index: 2;
}

.message-resize-handle.is-n,
.message-resize-handle.is-s {
  left: 10px;
  right: 10px;
  height: 8px;
  cursor: ns-resize;
}

.message-resize-handle.is-e,
.message-resize-handle.is-w {
  top: 10px;
  bottom: 10px;
  width: 8px;
  cursor: ew-resize;
}

.message-resize-handle.is-n {
  top: -4px;
}

.message-resize-handle.is-s {
  bottom: -4px;
}

.message-resize-handle.is-e {
  right: -4px;
}

.message-resize-handle.is-w {
  left: -4px;
}

.message-resize-handle.is-ne,
.message-resize-handle.is-nw,
.message-resize-handle.is-se,
.message-resize-handle.is-sw {
  width: 14px;
  height: 14px;
}

.message-resize-handle.is-ne {
  top: -5px;
  right: -5px;
  cursor: nesw-resize;
}

.message-resize-handle.is-nw {
  top: -5px;
  left: -5px;
  cursor: nwse-resize;
}

.message-resize-handle.is-se {
  right: -5px;
  bottom: -5px;
  cursor: nwse-resize;
}

.message-resize-handle.is-sw {
  bottom: -5px;
  left: -5px;
  cursor: nesw-resize;
}

.unread-badge {
  position: absolute;
  top: -8px;
  right: -8px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #be123c;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
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

.nav-divider {
  height: 1px;
  margin: 8px 4px;
  background: #374151;
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

  .message-popover-panel {
    left: 16px;
    right: auto;
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

