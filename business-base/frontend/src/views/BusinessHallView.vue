<script setup lang="ts">
import { computed, markRaw, onMounted, ref } from "vue";
import type { Component } from "vue";
import { Check, Coin, Download, Finished, Memo, Tickets, Upload, Wallet } from "@element-plus/icons-vue";
import { useRouter } from "vue-router";
import { fetchProcessEntryLinks } from "../api/workflow";
import { useAuthStore } from "../stores/auth";
import { useWorkflowStore } from "../stores/workflow";
import type { WorkflowListRecord, WorkflowProcessEntryLink, WorkflowTaskResponse } from "../types/workflow";
import { formatDateTime } from "../utils/format";

type BusinessKey = "deposit" | "withdraw" | "reimburse" | "payment" | "loan";

interface BusinessConfig {
  key: BusinessKey;
  name: string;
  description: string;
  icon: Component;
  matchers: string[];
}

interface BusinessCard extends BusinessConfig {
  link?: WorkflowProcessEntryLink;
  entryPageUrl?: string;
}

const router = useRouter();
const auth = useAuthStore();
const workflowStore = useWorkflowStore();
const loadingEntries = ref(false);
const entryError = ref("");
const entryLinks = ref<WorkflowProcessEntryLink[]>([]);

const businessConfigs: BusinessConfig[] = [
  {
    key: "deposit",
    name: "入金申请",
    description: "发起资金入账申请",
    icon: markRaw(Download),
    matchers: ["入金", "客户入金", "entry_application", "deposit"],
  },
  {
    key: "withdraw",
    name: "出金申请",
    description: "发起资金出账申请",
    icon: markRaw(Upload),
    matchers: ["出金", "withdraw", "withdrawal"],
  },
  {
    key: "reimburse",
    name: "报销申请",
    description: "发起费用报销申请",
    icon: markRaw(Tickets),
    matchers: ["报销", "reimburse", "expense"],
  },
  {
    key: "payment",
    name: "付款申请",
    description: "发起对外付款申请",
    icon: markRaw(Wallet),
    matchers: ["付款", "payment", "pay"],
  },
  {
    key: "loan",
    name: "借款申请",
    description: "发起借款申请",
    icon: markRaw(Coin),
    matchers: ["借款", "loan", "borrow"],
  },
];

const userName = computed(() => auth.user?.realName || auth.user?.username || "用户");
const visibleEntryLinks = computed(() => entryLinks.value.filter((item) => item.enabled !== false));
const todoRows = computed(() => workflowStore.list.records.filter(isTask).slice(0, 5));

const businessCards = computed<BusinessCard[]>(() =>
  businessConfigs.map((config) => {
    const link = visibleEntryLinks.value.find((item) => matchesBusiness(item, config));
    return {
      ...config,
      link,
      entryPageUrl: link?.entryPageUrl?.trim() || undefined,
    };
  }),
);

onMounted(() => {
  void loadHomeData();
});

async function loadHomeData(): Promise<void> {
  await Promise.all([loadEntryLinks(), loadTodos()]);
}

async function loadEntryLinks(): Promise<void> {
  loadingEntries.value = true;
  entryError.value = "";
  try {
    entryLinks.value = await fetchProcessEntryLinks();
  } catch (caught) {
    entryError.value = caught instanceof Error ? caught.message : "业务入口加载失败";
  } finally {
    loadingEntries.value = false;
  }
}

async function loadTodos(): Promise<void> {
  await workflowStore.loadList("todo", {
    pageNo: 1,
    pageSize: 5,
    source: "OWN",
  });
}

function matchesBusiness(item: WorkflowProcessEntryLink, config: BusinessConfig): boolean {
  const identity = [
    item.entryDisplayName,
    item.processName,
    item.processCode,
    item.entrySource,
    item.definitionId,
  ].filter(Boolean).join("|").toLowerCase();
  return config.matchers.some((matcher) => identity.includes(matcher.toLowerCase()));
}

function canOpen(card: BusinessCard): boolean {
  return Boolean(card.entryPageUrl);
}

async function openBusiness(card: BusinessCard): Promise<void> {
  const url = card.entryPageUrl;
  if (!url) return;
  if (url.startsWith("/")) {
    await router.push(url);
    return;
  }
  window.location.assign(url);
}

function isTask(row: WorkflowListRecord): row is WorkflowTaskResponse {
  return "taskId" in row && "taskVersion" in row;
}

function taskDetailPath(row: WorkflowTaskResponse): string {
  return `/workflow/tasks/${encodeURIComponent(row.taskId)}`;
}

function nodeLabel(row: WorkflowTaskResponse): string {
  return row.nodeName || row.nodeCode || "--";
}

function starterLabel(row: WorkflowTaskResponse): string {
  return row.starterUserName || row.starterUserId || "--";
}
</script>

<template>
  <div class="business-home">
    <section class="page-surface welcome-panel" aria-labelledby="home-welcome-heading">
      <div>
        <h2 id="home-welcome-heading">您好，{{ userName }} 👋</h2>
        <p>欢迎使用业务大厅，您可以在这里发起各类申请、查看进度及相关业务信息。</p>
      </div>
      <div class="welcome-visual" aria-hidden="true">
        <span class="visual-card is-primary"><Memo /></span>
        <span class="visual-card is-secondary"><Finished /></span>
        <span class="visual-line"></span>
        <span class="visual-line is-short"></span>
        <span class="visual-check"><Check /></span>
      </div>
    </section>

    <section class="page-surface home-section" aria-labelledby="business-apply-heading">
      <header class="section-heading">
        <div>
          <p class="eyebrow">Business</p>
          <h2 id="business-apply-heading">业务申请</h2>
        </div>
        <RouterLink class="section-link" to="/business-hall">全部业务</RouterLink>
      </header>

      <p v-if="loadingEntries" class="state-line" role="status">正在加载业务入口...</p>
      <p v-else-if="entryError" class="state-line is-error" role="alert">{{ entryError }}</p>

      <div class="business-grid" aria-label="业务申请入口">
        <button
          v-for="card in businessCards"
          :key="card.key"
          type="button"
          class="business-card"
          :disabled="!canOpen(card)"
          :title="canOpen(card) ? card.name : '尚未配置入口页面地址'"
          :data-test="`business-card-${card.key}`"
          @click="openBusiness(card)"
        >
          <span class="business-icon" aria-hidden="true">
            <component :is="card.icon" />
          </span>
          <strong>{{ card.name }}</strong>
          <small>{{ card.description }}</small>
          <span class="apply-link">立即申请</span>
        </button>
      </div>
    </section>

    <section class="page-surface home-section" aria-labelledby="todo-heading">
      <header class="section-heading">
        <div class="heading-inline">
          <span class="heading-icon" aria-hidden="true"><Finished /></span>
          <div>
            <p class="eyebrow">Workflow</p>
            <h2 id="todo-heading">待办事项</h2>
          </div>
          <span class="count-badge" aria-label="当前待办数量">{{ workflowStore.list.total }}</span>
        </div>
        <RouterLink class="section-link" to="/workflow/todo">全部待办</RouterLink>
      </header>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th scope="col">事项名称</th>
              <th scope="col">申请人</th>
              <th scope="col">申请时间</th>
              <th scope="col">当前节点</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="workflowStore.list.loading">
              <td colspan="5" class="state-cell" role="status">加载中...</td>
            </tr>
            <tr v-else-if="workflowStore.list.error">
              <td colspan="5" class="state-cell is-error" role="alert">{{ workflowStore.list.error }}</td>
            </tr>
            <tr v-else-if="todoRows.length === 0">
              <td colspan="5" class="empty-cell">暂无待办</td>
            </tr>
            <tr v-for="row in todoRows" v-else :key="row.taskId">
              <td><strong>{{ row.instanceTitle }}</strong></td>
              <td>{{ starterLabel(row) }}</td>
              <td>{{ formatDateTime(row.createdAt) }}</td>
              <td>{{ nodeLabel(row) }}</td>
              <td class="table-actions">
                <RouterLink class="table-link" :to="taskDetailPath(row)">去处理</RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="more-row">
        <RouterLink class="section-link" to="/workflow/todo">查看更多</RouterLink>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.business-home {
  display: grid;
  gap: 16px;
}

.welcome-panel,
.home-section {
  display: grid;
  gap: 16px;
}

.welcome-panel {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  min-height: 142px;
}

.welcome-panel h2,
.home-section h2,
p {
  margin: 0;
}

.welcome-panel h2,
.home-section h2 {
  color: #17202a;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 700;
}

.welcome-panel p {
  margin-top: 12px;
  max-width: 720px;
  color: #5d6978;
  line-height: 1.7;
}

.welcome-visual {
  display: grid;
  align-content: center;
  gap: 8px;
  width: 168px;
  min-height: 96px;
  border: 1px solid #d8dee8;
  border-radius: 8px;
  padding: 16px;
  background: #f8fafc;
}

.visual-card,
.visual-line,
.visual-check {
  display: grid;
  place-items: center;
}

.visual-card {
  width: 76px;
  height: 44px;
  border: 1px solid #b9c2cf;
  border-radius: 6px;
  background: #fff;
  color: #0f766e;
}

.visual-card :deep(svg) {
  width: 24px;
  height: 24px;
}

.visual-card.is-secondary {
  margin-top: -30px;
  margin-left: 52px;
  background: #ecfdf5;
  color: #2563eb;
}

.visual-line {
  width: 118px;
  height: 8px;
  border-radius: 999px;
  background: #d8dee8;
}

.visual-line.is-short {
  width: 84px;
}

.visual-check {
  position: absolute;
  transform: translate(116px, -32px);
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #0f766e;
  color: #fff;
  font-weight: 800;
  line-height: 28px;
  text-align: center;
}

.visual-check :deep(svg) {
  width: 16px;
  height: 16px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.heading-inline {
  display: flex;
  align-items: center;
  gap: 10px;
}

.heading-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: #ecfdf5;
  color: #0f766e;
}

.heading-icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.eyebrow {
  margin: 0 0 4px;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}

.section-link,
.table-link {
  min-height: 34px;
  border: 1px solid #d8dee8;
  border-radius: 6px;
  padding: 6px 12px;
  background: #fff;
  color: #17202a;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
  text-decoration: none;
}

.section-link:hover,
.table-link:hover {
  border-color: #2563eb;
  color: #2563eb;
}

.section-link:focus-visible,
.table-link:focus-visible,
.business-card:focus-visible {
  outline: 3px solid #f59e0b;
  outline-offset: 2px;
}

.business-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 14px;
}

.business-card {
  display: grid;
  justify-items: center;
  gap: 8px;
  min-height: 168px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px 12px;
  background: #fff;
  color: #17202a;
  cursor: pointer;
  font: inherit;
  text-align: center;
}

.business-card:hover:not(:disabled) {
  border-color: #0f766e;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.business-card:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.business-icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 8px;
  background: #ecfdf5;
  color: #0f766e;
}

.business-icon :deep(svg) {
  width: 24px;
  height: 24px;
}

.business-card strong,
.business-card small,
.apply-link {
  width: 100%;
  overflow-wrap: anywhere;
}

.business-card strong {
  font-size: 16px;
}

.business-card small {
  color: #5d6978;
  line-height: 1.5;
}

.apply-link {
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.count-badge {
  min-width: 24px;
  height: 24px;
  border-radius: 999px;
  padding: 0 8px;
  background: #0f766e;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  line-height: 24px;
  text-align: center;
}

.table-wrap {
  overflow: auto;
  border: 1px solid #d8dee8;
  border-radius: 8px;
  background: #fff;
}

table {
  width: 100%;
  min-width: 720px;
  border-collapse: collapse;
  table-layout: fixed;
}

th,
td {
  overflow-wrap: anywhere;
  border-bottom: 1px solid #d8dee8;
  padding: 9px 10px;
  text-align: left;
  vertical-align: middle;
}

th {
  position: sticky;
  top: 0;
  z-index: 1;
  height: 40px;
  background: #f8fafc;
  color: #344054;
  font-size: 12px;
  font-weight: 700;
}

td {
  color: #17202a;
  font-size: 13px;
}

tbody tr:hover {
  background: #eef6ff;
}

td strong {
  display: block;
}

.table-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.state-line,
.state-cell,
.empty-cell {
  background: #f8fafc;
  color: #5d6978;
  text-align: center;
}

.state-line {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 18px;
}

.state-line.is-error,
.state-cell.is-error {
  background: #fff1f2;
  color: #be123c;
}

.state-cell,
.empty-cell {
  height: 64px;
}

.more-row {
  display: flex;
  justify-content: center;
}

@media (max-width: 760px) {
  .welcome-panel,
  .section-heading {
    align-items: stretch;
    grid-template-columns: 1fr;
  }

  .section-heading {
    flex-direction: column;
  }

  .welcome-visual {
    width: 100%;
  }
}
</style>


