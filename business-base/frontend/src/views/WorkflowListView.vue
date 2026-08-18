<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useWorkflowStore } from "../stores/workflow";
import type {
  WorkflowHistoryTaskResponse,
  WorkflowInstanceResponse,
  WorkflowListRecord,
  WorkflowListType,
  WorkflowReadRecordResponse,
  WorkflowTaskResponse,
} from "../types/workflow";
import {
  displayWorkflowValue,
  formatWorkflowDateTime,
  instanceStatusBadgeClass,
  instanceStatusLabel,
  taskActionBadgeClass,
  taskActionLabel,
  workflowEmptyText,
  workflowNodeLabel,
} from "../utils/workflowDisplay";
import { performTaskAction } from "../api/workflow";
import { WorkflowApiError } from "../api/http";
import { createIdempotencyKey } from "../utils/idempotency";

const props = defineProps<{
  type: WorkflowListType;
  title: string;
}>();

const store = useWorkflowStore();
const filters = reactive({
  processName: "",
  instanceTitle: "",
  nodeCode: "",
  status: "",
  actionType: "",
  pageNo: 1,
  pageSize: 20,
});
const todoScope = ref<"own" | "delegated">("own");
const withdrawingHistoryId = ref("");
const operationError = ref("");
const operationSuccess = ref("");
const withdrawKeys = new Map<string, string>();

const isTodoList = computed(() => props.type === "todo");

const columns = computed(() => {
  if (props.type === "completed") {
    return ["processName", "nodeName", "actionType", "comment", "completedAt"];
  }
  if (props.type === "started") {
    return ["processName", "instanceStatus", "currentNodeCodes", "startedAt", "endedAt"];
  }
  if (props.type === "read") {
    return ["processName", "instanceStatus", "readAt"];
  }
  return ["processName", "nodeName", "starterUserName", "createdAt", "dueAt", "deadlineStatus"];
});

onMounted(() => {
  void load();
});

watch(
  () => props.type,
  () => {
    filters.pageNo = 1;
    void load();
  },
);

async function load(): Promise<void> {
  await store.loadList(props.type, {
    pageNo: filters.pageNo,
    pageSize: filters.pageSize,
    processName: filters.processName.trim() || undefined,
    instanceTitle: filters.instanceTitle.trim() || undefined,
    nodeCode: filters.nodeCode.trim() || undefined,
    status: filters.status.trim() || undefined,
    actionType: filters.actionType.trim() || undefined,
    source: isTodoList.value ? todoTaskSource() : undefined,
  });
}

async function withdraw(row: WorkflowHistoryTaskResponse): Promise<void> {
  const context = row.withdrawContext;
  if (!context || withdrawingHistoryId.value) return;
  const target = context.targetNodeName || context.targetNodeCode || "上一节点";
  if (!window.confirm(`确认撤回至 ${target}？当前下游任务将被取消。`)) return;
  operationError.value = "";
  operationSuccess.value = "";
  withdrawingHistoryId.value = row.historyTaskId;
  const key = withdrawKeys.get(row.historyTaskId) ?? createIdempotencyKey("workflow:withdraw");
  withdrawKeys.set(row.historyTaskId, key);
  try {
    await performTaskAction(context.taskId, "WITHDRAW", {
      expectedTaskVersion: context.expectedTaskVersion,
      comment: "",
      idempotencyKey: key,
    });
    withdrawKeys.delete(row.historyTaskId);
    operationSuccess.value = `已撤回至 ${target}`;
    await load();
  } catch (error) {
    operationError.value = error instanceof Error ? error.message : "撤回失败";
    if (error instanceof WorkflowApiError) {
      withdrawKeys.delete(row.historyTaskId);
      if (error.status === 409) await load();
    }
  } finally {
    withdrawingHistoryId.value = "";
  }
}

function setTodoScope(scope: "own" | "delegated"): void {
  if (todoScope.value === scope) {
    return;
  }
  todoScope.value = scope;
  filters.pageNo = 1;
  void load();
}

function todoTaskSource(): "OWN" | "DELEGATED" {
  return todoScope.value === "delegated" ? "DELEGATED" : "OWN";
}

function detailPath(row: WorkflowListRecord): string {
  if (isTask(row)) {
    return `/workflow/tasks/${encodeURIComponent(row.taskId)}`;
  }
  return `/workflow/instances/${encodeURIComponent(row.instanceId)}`;
}

function rowKey(row: WorkflowListRecord): string {
  if (isTask(row)) return row.taskId;
  if (isHistoryTask(row)) return row.historyTaskId;
  if (isReadRecord(row)) return row.readRecordId;
  return row.instanceId;
}

function isTask(row: WorkflowListRecord): row is WorkflowTaskResponse {
  return "taskVersion" in row;
}

function isHistoryTask(row: WorkflowListRecord): row is WorkflowHistoryTaskResponse {
  return "historyTaskId" in row;
}

function isReadRecord(row: WorkflowListRecord): row is WorkflowReadRecordResponse {
  return "readRecordId" in row;
}

function isInstance(row: WorkflowListRecord): row is WorkflowInstanceResponse {
  return "variables" in row;
}

function columnLabel(column: string): string {
  const labels: Record<string, string> = {
    processName: "流程",
    nodeName: "节点",
    source: "任务来源",
    actionType: "办理动作",
    comment: "办理意见",
    instanceStatus: "状态",
    currentNodeCodes: "当前节点",
    starterUserName: "发起人",
    createdAt: "创建时间",
    dueAt: "到期时间",
    deadlineStatus: "时限",
    startedAt: "发起时间",
    endedAt: "结束时间",
    completedAt: "完成时间",
    readAt: "阅读时间",
  };
  return labels[column] ?? column;
}

function deadlineLabel(row: WorkflowListRecord): string {
  if (!isTask(row)) return workflowEmptyText;
  if (row.deadlineStatus === "OVERDUE") return "已超时";
  if (row.deadlineStatus === "DUE_SOON") return "即将超时";
  if (row.deadlineStatus === "NORMAL") return "正常";
  return workflowEmptyText;
}

function deadlineClass(row: WorkflowListRecord): Record<string, boolean> {
  return {
    "is-overdue": isTask(row) && row.deadlineStatus === "OVERDUE",
    "is-due-soon": isTask(row) && row.deadlineStatus === "DUE_SOON",
  };
}

function rawColumnValue(row: WorkflowListRecord, column: string): unknown {
  return (row as unknown as Record<string, unknown>)[column];
}

function columnValue(row: WorkflowListRecord, column: string): string {
  if (column === "source" && isTask(row)) {
    return row.delegateFromUserId ? "委托代办任务" : "自己的任务";
  }
  if (column === "currentNodeCodes" && isInstance(row)) {
    return currentNodeNames(row);
  }
  const value = (row as unknown as Record<string, unknown>)[column];
  if (column.endsWith("At")) {
    return formatWorkflowDateTime(value as string | undefined | null);
  }
  if (column === "nodeName" && (isTask(row) || isHistoryTask(row))) {
    return workflowNodeLabel(row.nodeCode, row.nodeName);
  }
  return displayWorkflowValue(value);
}

function starterName(row: WorkflowListRecord): string | undefined {
  return "starterUserName" in row ? row.starterUserName : undefined;
}

function currentNodeNames(row: WorkflowInstanceResponse): string {
  const names = (row as { currentNodeNames?: string[] }).currentNodeNames;
  if (names?.length) {
    return names.map((name) => displayWorkflowValue(name)).join("、");
  }
  return row.currentNodeCodes.length
    ? row.currentNodeCodes.map((code) => workflowNodeLabel(code)).join("、")
    : workflowEmptyText;
}

function statusDataTest(row: WorkflowListRecord): string | undefined {
  if (isReadRecord(row)) return `status-${row.readRecordId}`;
  if (isInstance(row)) return `status-${row.instanceId}`;
  return undefined;
}

function actionDataTest(row: WorkflowListRecord): string | undefined {
  return isHistoryTask(row) ? `action-${row.historyTaskId}` : undefined;
}
</script>

<template>
  <section class="page-surface" :aria-labelledby="`${type}-heading`">
    <header class="section-heading">
      <div>
        <p class="eyebrow">Workflow</p>
        <h1 :id="`${type}-heading`">{{ title }}</h1>
      </div>
      <button type="button" @click="load">刷新</button>
    </header>

    <div v-if="isTodoList" class="scope-tabs todo-segmented" aria-label="待办任务范围">
      <button
        type="button"
        data-test="todo-scope-own"
        :class="{ active: todoScope === 'own' }"
        @click="setTodoScope('own')"
      >
        自己的任务
      </button>
      <button
        type="button"
        data-test="todo-scope-delegated"
        :class="{ active: todoScope === 'delegated' }"
        @click="setTodoScope('delegated')"
      >
        委托代办任务
      </button>
    </div>

    <form class="filter-grid" @submit.prevent="load">
      <label>
        流程名称
        <input v-model="filters.processName" type="search" autocomplete="off" />
      </label>
      <label>
        标题
        <input v-model="filters.instanceTitle" type="search" autocomplete="off" />
      </label>
      <label v-if="type === 'todo' || type === 'completed'">
        节点编码
        <input v-model="filters.nodeCode" type="search" autocomplete="off" />
      </label>
      <label v-if="type === 'started' || type === 'read'">
        状态
        <input v-model="filters.status" type="search" autocomplete="off" />
      </label>
      <label v-if="type === 'completed'">
        办理动作
        <input v-model="filters.actionType" type="search" autocomplete="off" />
      </label>
      <div class="filter-actions">
        <button type="submit">查询</button>
      </div>
    </form>

    <p v-if="operationSuccess" class="operation-message is-success" role="status">
      {{ operationSuccess }}
    </p>
    <p v-if="operationError" class="operation-message is-error" role="alert">
      {{ operationError }}
    </p>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th scope="col">标题</th>
            <th v-for="column in columns" :key="column" scope="col">
              {{ columnLabel(column) }}
            </th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.list.loading">
            <td :colspan="columns.length + 2" class="state-cell" role="status">加载中...</td>
          </tr>
          <tr v-else-if="store.list.error">
            <td :colspan="columns.length + 2" class="state-cell is-error" role="alert">
              {{ store.list.error }}
            </td>
          </tr>
          <tr v-else-if="store.list.records.length === 0">
            <td :colspan="columns.length + 2" class="empty-cell">
              {{ isTodoList ? "暂无代办" : "暂无记录" }}
            </td>
          </tr>
          <tr
            v-for="row in store.list.loading || store.list.error ? [] : store.list.records"
            :key="rowKey(row)"
          >
            <td>
              <strong>{{ row.instanceTitle }}</strong>
              <span v-if="starterName(row)">发起人：{{ starterName(row) }}</span>
            </td>
            <td v-for="column in columns" :key="column">
              <span
                v-if="column === 'deadlineStatus'"
                class="deadline-badge"
                :class="deadlineClass(row)"
                :data-test="isTask(row) ? `deadline-${row.taskId}` : undefined"
              >
                {{ deadlineLabel(row) }}
              </span>
              <span
                v-else-if="column === 'instanceStatus'"
                class="status-badge"
                :class="instanceStatusBadgeClass(rawColumnValue(row, column))"
                :data-test="statusDataTest(row)"
              >
                {{ instanceStatusLabel(rawColumnValue(row, column)) }}
              </span>
              <span
                v-else-if="column === 'actionType'"
                class="action-badge"
                :class="taskActionBadgeClass(rawColumnValue(row, column))"
                :data-test="actionDataTest(row)"
              >
                {{ taskActionLabel(rawColumnValue(row, column)) }}
              </span>
              <template v-else>{{ columnValue(row, column) }}</template>
            </td>
            <td class="table-actions">
              <RouterLink class="detail-link" :to="detailPath(row)">详情</RouterLink>
              <button
                v-if="isHistoryTask(row) && row.withdrawContext"
                type="button"
                class="withdraw-button text-action"
                data-test="withdraw-completed-task"
                :disabled="Boolean(withdrawingHistoryId)"
                @click="withdraw(row)"
              >
                {{ withdrawingHistoryId === row.historyTaskId ? "撤回中..." : "撤回" }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <footer class="pager">
      <button
        type="button"
        :disabled="filters.pageNo <= 1 || store.list.loading"
        @click="filters.pageNo -= 1; load()"
      >
        上一页
      </button>
      <span>第 {{ filters.pageNo }} 页 / 共 {{ store.list.total }} 条</span>
      <button
        type="button"
        :disabled="filters.pageNo >= store.list.totalPages || store.list.loading"
        @click="filters.pageNo += 1; load()"
      >
        下一页
      </button>
    </footer>
  </section>
</template>

<style scoped>
.page-surface {
  display: block;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.eyebrow {
  margin: 0 0 4px;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  color: #17202a;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 700;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  align-items: end;
  margin-bottom: 14px;
}

.filter-actions {
  display: flex;
  gap: 8px;
}

.scope-tabs,
.todo-segmented {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.scope-tabs button.active {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
}

label {
  display: grid;
  gap: 6px;
  color: #5d6978;
  font-size: 13px;
}

input {
  width: 100%;
  min-height: 34px;
  border: 1px solid #d8dee8;
  border-radius: 6px;
  padding: 7px 9px;
  background: #fff;
  color: #17202a;
  font: inherit;
}

button {
  min-height: 34px;
  border: 1px solid #d8dee8;
  border-radius: 6px;
  padding: 6px 12px;
  background: #fff;
  color: #17202a;
  cursor: pointer;
  font: inherit;
  text-decoration: none;
}

button:hover:not(:disabled) {
  border-color: #2563eb;
  color: #2563eb;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

input:focus,
button:focus-visible,
.detail-link:focus-visible {
  outline: 3px solid #f59e0b;
  outline-offset: 2px;
}

.table-wrap {
  overflow: auto;
  border: 1px solid #d8dee8;
  border-radius: 8px;
  background: #fff;
}

table {
  width: 100%;
  min-width: 760px;
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

.state-cell {
  height: 64px;
  background: #f8fafc;
  color: #5d6978;
  text-align: center;
}

.state-cell.is-error {
  background: #fff1f2;
  color: #be123c;
}

.operation-message {
  margin: 0 0 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 9px 11px;
}

.operation-message.is-success {
  border-color: #86efac;
  background: #f0fdf4;
  color: #166534;
}

.operation-message.is-error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

tbody tr:hover {
  background: #eef6ff;
}

td strong,
td span {
  display: block;
}

td span {
  margin-top: 4px;
  color: #5d6978;
  font-size: 12px;
}

.empty-cell {
  padding: 20px;
  color: #5d6978;
  text-align: center;
  background: #f8fafc;
}

.table-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.detail-link,
.text-action {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  border: 0;
  padding: 0 2px;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  font: inherit;
  font-weight: 650;
  text-decoration: none;
}

.detail-link:hover,
.text-action:hover:not(:disabled) {
  color: #1d4ed8;
  text-decoration: underline;
}

.text-action {
  min-height: 28px;
  border: 0;
}

.withdraw-button {
  color: #b91c1c;
}

.withdraw-button:hover:not(:disabled) {
  color: #991b1b;
}

.status-badge,
.action-badge {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  min-height: 24px;
  border: 1px solid #d8dee8;
  border-radius: 6px;
  padding: 2px 9px;
  background: #f8fafc;
  color: #5d6978;
  font-size: 12px;
  font-weight: 800;
}

.status-badge.is-running,
.status-badge.is-pending {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.status-badge.is-completed,
.action-badge.is-positive {
  border-color: #bbf7d0;
  background: #ecfdf5;
  color: #15803d;
}

.status-badge.is-stopped,
.action-badge.is-danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.status-badge.is-neutral,
.action-badge.is-neutral,
.action-badge.is-muted {
  border-color: #d8dee8;
  background: #f8fafc;
  color: #344054;
}

.pager {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  align-items: center;
  margin-top: 10px;
  color: #5d6978;
  font-size: 13px;
}

@media (max-width: 760px) {
  .section-heading,
  .filter-grid,
  .pager {
    align-items: stretch;
    flex-direction: column;
    grid-template-columns: 1fr;
  }

}
</style>
