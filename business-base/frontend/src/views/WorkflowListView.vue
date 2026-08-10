<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useWorkflowStore } from "../stores/workflow";
import type { WorkflowListItem, WorkflowListType } from "../types/workflow";
import { formatDateTime } from "../utils/format";

const props = defineProps<{
  type: WorkflowListType;
  title: string;
}>();

const store = useWorkflowStore();
const filters = reactive({
  keyword: "",
  pageNo: 1,
  pageSize: 20,
});
const todoScope = ref<"own" | "delegated">("own");

const isTodoList = computed(() => props.type === "todo");

const columns = computed(() => {
  if (props.type === "completed") {
    return ["processName", "nodeName", "action", "completedAt"];
  }
  if (props.type === "started") {
    return ["processName", "status", "nodeName", "createdAt"];
  }
  if (props.type === "read") {
    return ["processName", "status", "readAt", "createdAt"];
  }
  return ["processName", "nodeName", "taskSource", "createdAt"];
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
    keyword: filters.keyword.trim() || undefined,
    taskSource: isTodoList.value ? todoTaskSource() : undefined,
  });
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

function detailPath(row: WorkflowListItem): string {
  if (row.taskId) {
    return `/workflow/tasks/${encodeURIComponent(row.taskId)}`;
  }
  return `/workflow/instances/${encodeURIComponent(row.instanceId ?? row.id)}`;
}

function columnLabel(column: string): string {
  const labels: Record<string, string> = {
    processName: "流程",
    nodeName: "节点",
    taskSource: "来源",
    action: "动作",
    status: "状态",
    createdAt: "创建时间",
    completedAt: "完成时间",
    readAt: "阅读时间",
  };
  return labels[column] ?? column;
}

function columnValue(row: WorkflowListItem, column: string): string {
  const value = row[column as keyof WorkflowListItem];
  if (column.endsWith("At")) {
    return formatDateTime(value as string | undefined);
  }
  if (typeof value === "boolean") {
    return value ? "是" : "否";
  }
  if (column === "taskSource") {
    if (value === "DELEGATED") {
      return "委托代办任务";
    }
    if (value === "OWN") {
      return "自己的任务";
    }
  }
  return value == null || value === "" ? "--" : String(value);
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
        关键词
        <input v-model="filters.keyword" type="search" autocomplete="off" />
      </label>
      <div class="filter-actions">
        <button type="submit">查询</button>
      </div>
    </form>

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
          <tr v-else-if="store.list.items.length === 0">
            <td :colspan="columns.length + 2" class="empty-cell">暂无记录</td>
          </tr>
          <tr v-for="row in store.list.loading || store.list.error ? [] : store.list.items" :key="row.id">
            <td>
              <strong>{{ row.title }}</strong>
              <span v-if="row.starterName">发起人：{{ row.starterName }}</span>
            </td>
            <td v-for="column in columns" :key="column">{{ columnValue(row, column) }}</td>
            <td class="table-actions">
              <RouterLink class="table-link" :to="detailPath(row)">详情</RouterLink>
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
        :disabled="store.list.items.length < filters.pageSize || store.list.loading"
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
  grid-template-columns: minmax(180px, 1fr) auto;
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

button,
.table-link {
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

button:hover:not(:disabled),
.table-link:hover {
  border-color: #2563eb;
  color: #2563eb;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

input:focus,
button:focus-visible,
.table-link:focus-visible {
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
  gap: 6px;
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
