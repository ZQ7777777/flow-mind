<script setup lang="ts">
import { onMounted, reactive } from "vue";
import { useMessageStore } from "../stores/message";
import { formatDateTime } from "../utils/format";
import {
  MESSAGE_TYPE_FILTERS,
  READ_STATUS_FILTERS,
  messageTypeLabel,
  readStatusLabel,
  severityClass,
  severityLabel,
} from "../utils/message";
import type { BusinessMessage } from "../types/message";

const store = useMessageStore();

const filters = reactive({
  readStatus: "",
  messageType: "",
});

onMounted(() => {
  void store.loadMessages(1);
});

function applyFilters(): void {
  store.applyFilters(filters.readStatus, filters.messageType);
}

function resetPage(): void {
  filters.readStatus = "";
  filters.messageType = "";
  store.applyFilters("", "");
}

async function markRead(message: BusinessMessage): Promise<void> {
  try {
    await store.markRead(message.messageId);
  } catch {
    // 错误已通过 store 回滚，列表状态保持一致。
  }
}

async function markAllRead(): Promise<void> {
  try {
    await store.markAllRead();
  } catch {
    // 忽略，store 已回滚。
  }
}

function detailPath(message: BusinessMessage): string | null {
  const payload = message.payload;
  if (!payload) return null;
  const taskId = payload.taskId;
  if (typeof taskId === "string" && taskId) {
    return `/workflow/tasks/${encodeURIComponent(taskId)}`;
  }
  const instanceId = payload.instanceId;
  if (typeof instanceId === "string" && instanceId) {
    return `/workflow/instances/${encodeURIComponent(instanceId)}`;
  }
  return null;
}

function prevPage(): void {
  if (store.pageNo <= 1 || store.loading) return;
  void store.loadMessages(store.pageNo - 1);
}

function nextPage(): void {
  if (!store.hasMore || store.loading) return;
  void store.loadMessages(store.pageNo + 1);
}
</script>

<template>
  <section class="page-surface" aria-labelledby="messages-heading">
    <header class="section-heading">
      <div>
        <p class="eyebrow">Inbox</p>
        <h1 id="messages-heading">
          消息中心
          <span class="unread-pill" data-test="unread-count">
            {{ store.unreadCount }} 条未读
          </span>
        </h1>
      </div>
      <div class="header-actions">
        <button
          type="button"
          data-test="messages-mark-all-read"
          :disabled="store.unreadCount === 0 || store.markingAllRead"
          @click="markAllRead"
        >
          {{ store.markingAllRead ? "处理中..." : "全部已读" }}
        </button>
        <button type="button" data-test="messages-refresh" @click="store.loadMessages()">
          刷新
        </button>
      </div>
    </header>

    <form class="filter-grid" @submit.prevent="applyFilters">
      <label>
        已读状态
        <select v-model="filters.readStatus" data-test="messages-filter-readStatus">
          <option v-for="opt in READ_STATUS_FILTERS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </label>
      <label>
        消息类型
        <select v-model="filters.messageType" data-test="messages-filter-type">
          <option v-for="opt in MESSAGE_TYPE_FILTERS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </label>
      <div class="filter-actions">
        <button type="submit" data-test="messages-query">查询</button>
        <button type="button" @click="resetPage">重置</button>
      </div>
    </form>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th scope="col">标题</th>
            <th scope="col">类型</th>
            <th scope="col">级别</th>
            <th scope="col">状态</th>
            <th scope="col">时间</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.loading">
            <td colspan="6" class="state-cell" role="status">加载中...</td>
          </tr>
          <tr v-else-if="store.error">
            <td colspan="6" class="state-cell is-error" role="alert">{{ store.error }}</td>
          </tr>
          <tr v-else-if="store.records.length === 0">
            <td colspan="6" class="empty-cell">暂无消息</td>
          </tr>
          <tr
            v-for="message in store.loading || store.error ? [] : store.records"
            v-else
            :key="message.messageId"
            :data-test="`message-row-${message.messageId}`"
            :class="{ 'is-unread': message.readStatus !== 'READ' }"
          >
            <td>
              <strong>{{ message.title || messageTypeLabel(message.messageType) }}</strong>
              <span v-if="message.content">{{ message.content }}</span>
            </td>
            <td>{{ messageTypeLabel(message.messageType) }}</td>
            <td>
              <span class="severity-badge" :class="severityClass(message.severity)">
                {{ severityLabel(message.severity) }}
              </span>
            </td>
            <td>
              <span class="read-badge" :class="{ 'is-unread': message.readStatus !== 'READ' }">
                {{ readStatusLabel(message.readStatus) }}
              </span>
            </td>
            <td>{{ formatDateTime(message.createdAt) }}</td>
            <td class="table-actions">
              <button
                v-if="message.readStatus !== 'READ'"
                type="button"
                :data-test="`message-mark-read-${message.messageId}`"
                @click="markRead(message)"
              >
                标记已读
              </button>
              <RouterLink
                v-if="detailPath(message)"
                class="table-link"
                :data-test="`message-detail-${message.messageId}`"
                :to="detailPath(message)!"
              >
                查看详情
              </RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <footer class="pager">
      <button type="button" :disabled="store.pageNo <= 1 || store.loading" @click="prevPage">
        上一页
      </button>
      <span>第 {{ store.pageNo }} 页</span>
      <button type="button" :disabled="!store.hasMore || store.loading" @click="nextPage">
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
  text-transform: uppercase;
}

h1 {
  margin: 0;
  color: #17202a;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 700;
}

.unread-pill {
  margin-left: 10px;
  padding: 2px 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  align-items: end;
  margin-bottom: 14px;
}

.filter-actions {
  display: flex;
  gap: 8px;
}

label {
  display: grid;
  gap: 6px;
  color: #5d6978;
  font-size: 13px;
}

select,
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

select:focus,
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

tbody tr.is-unread {
  background: #f8fafc;
}

tbody tr.is-unread:hover {
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

.severity-badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.severity-badge.is-high {
  background: #fff1f2;
  color: #be123c;
}

.severity-badge.is-medium {
  background: #fffbeb;
  color: #b45309;
}

.severity-badge.is-normal {
  background: #f1f5f9;
  color: #475569;
}

.read-badge.is-unread {
  color: #1d4ed8;
  font-weight: 600;
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
