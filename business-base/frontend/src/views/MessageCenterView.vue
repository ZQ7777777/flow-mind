<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useMessageStore } from "../stores/message";
import { formatDateTime } from "../utils/format";
import {
  MESSAGE_TYPE_FILTERS,
  READ_STATUS_FILTERS,
  messageTypeLabel,
  readStatusLabel,
} from "../utils/message";
import type { BusinessMessage } from "../types/message";

const props = withDefaults(defineProps<{ variant?: "page" | "popup" }>(), {
  variant: "page",
});

const store = useMessageStore();
const selectedMessage = ref<BusinessMessage | null>(null);

const filters = reactive({
  readStatus: "",
  messageType: "",
});

const isPopup = computed(() => props.variant === "popup");

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

async function openDetail(message: BusinessMessage): Promise<void> {
  selectedMessage.value = message;
  if (message.readStatus !== "READ") {
    await markRead(message);
  }
}

function closeDetail(): void {
  selectedMessage.value = null;
}

function prevPage(): void {
  if (store.pageNo <= 1 || store.loading) return;
  void store.loadMessages(store.pageNo - 1);
}

function nextPage(): void {
  if (!store.hasMore || store.loading) return;
  void store.loadMessages(store.pageNo + 1);
}

function instanceTitle(message: BusinessMessage): string {
  const payload = message.payload;
  const title = payload?.instanceTitle;
  if (typeof title === "string" && title.trim()) {
    return title;
  }
  return message.title || messageTypeLabel(message.messageType);
}
</script>

<template>
  <section
    class="page-surface message-center"
    :class="{ 'is-popup': isPopup }"
    aria-labelledby="messages-heading"
  >
    <header class="section-heading">
      <div>
        <p v-if="!isPopup" class="eyebrow">Inbox</p>
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

    <form v-if="!isPopup" class="filter-grid" @submit.prevent="applyFilters">
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

    <div class="message-list" role="list">
      <p v-if="store.loading" class="state-cell" role="status">加载中...</p>
      <p v-else-if="store.error" class="state-cell is-error" role="alert">{{ store.error }}</p>
      <p v-else-if="store.records.length === 0" class="empty-cell">暂无消息</p>
      <button
        v-for="message in store.loading || store.error ? [] : store.records"
        v-else
        :key="message.messageId"
        class="message-item"
        :class="{ 'is-unread': message.readStatus !== 'READ' }"
        type="button"
        role="listitem"
        :data-test="`message-row-${message.messageId}`"
        @click="openDetail(message)"
      >
        <span class="message-avatar" aria-hidden="true"></span>
        <span class="message-main">
          <strong>{{ instanceTitle(message) }}</strong>
          <span>{{ messageTypeLabel(message.messageType) }}</span>
        </span>
        <span class="message-side">
          <time>{{ formatDateTime(message.createdAt) }}</time>
          <span class="read-badge" :class="{ 'is-unread': message.readStatus !== 'READ' }">
            {{ readStatusLabel(message.readStatus) }}
          </span>
        </span>
      </button>
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

    <div v-if="selectedMessage" class="detail-backdrop" role="presentation" @click.self="closeDetail">
      <article class="detail-dialog" role="dialog" aria-modal="true" aria-labelledby="message-detail-title">
        <header class="detail-heading">
          <h2 id="message-detail-title">消息详情</h2>
          <button type="button" aria-label="关闭" @click="closeDetail">×</button>
        </header>
        <dl class="detail-fields">
          <div>
            <dt>流程实例标题</dt>
            <dd>{{ instanceTitle(selectedMessage) }}</dd>
          </div>
          <div>
            <dt>消息类型</dt>
            <dd>{{ messageTypeLabel(selectedMessage.messageType) }}</dd>
          </div>
          <div>
            <dt>消息内容</dt>
            <dd>{{ selectedMessage.content || "--" }}</dd>
          </div>
          <div>
            <dt>产生时间</dt>
            <dd>{{ formatDateTime(selectedMessage.createdAt) }}</dd>
          </div>
        </dl>
      </article>
    </div>
  </section>
</template>

<style scoped>
.page-surface {
  display: block;
}

.message-center.is-popup {
  border: 1px solid #d8dee8;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
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

h1,
h2 {
  margin: 0;
  color: #17202a;
  line-height: 1.2;
  font-weight: 700;
}

h1 {
  font-size: 24px;
}

.is-popup h1 {
  font-size: 18px;
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

.header-actions,
.filter-actions,
.pager {
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

label {
  display: grid;
  gap: 6px;
  color: #5d6978;
  font-size: 13px;
}

select {
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
}

button:hover:not(:disabled) {
  border-color: #2563eb;
  color: #2563eb;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

select:focus,
button:focus-visible {
  outline: 3px solid #f59e0b;
  outline-offset: 2px;
}

.message-list {
  display: grid;
  border: 1px solid #d8dee8;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.is-popup .message-list {
  max-height: 430px;
  overflow: auto;
}

.message-item {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  width: 100%;
  min-height: 68px;
  border: 0;
  border-bottom: 1px solid #edf1f6;
  border-radius: 0;
  padding: 10px 12px;
  text-align: left;
}

.message-item:last-child {
  border-bottom: 0;
}

.message-item:hover {
  background: #eef6ff;
}

.message-item.is-unread {
  background: #f8fafc;
}

.message-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #dbeafe;
}

.message-main,
.message-side {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.message-main strong {
  overflow: hidden;
  color: #17202a;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-main span,
.message-side,
.state-cell,
.empty-cell {
  color: #5d6978;
  font-size: 12px;
}

.message-side {
  justify-items: end;
  white-space: nowrap;
}

.read-badge.is-unread {
  color: #1d4ed8;
  font-weight: 600;
}

.state-cell,
.empty-cell {
  margin: 0;
  padding: 22px;
  text-align: center;
  background: #f8fafc;
}

.state-cell.is-error {
  background: #fff1f2;
  color: #be123c;
}

.pager {
  justify-content: flex-end;
  align-items: center;
  margin-top: 10px;
  color: #5d6978;
  font-size: 13px;
}

.detail-backdrop {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: 18px;
  background: rgba(15, 23, 42, 0.32);
}

.detail-dialog {
  width: min(520px, 100%);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.28);
}

.detail-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #d8dee8;
}

.detail-heading button {
  width: 34px;
  padding: 0;
  font-size: 20px;
}

.detail-fields {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 16px;
}

.detail-fields div {
  display: grid;
  gap: 5px;
}

.detail-fields dt {
  color: #5d6978;
  font-size: 12px;
  font-weight: 700;
}

.detail-fields dd {
  margin: 0;
  color: #17202a;
  font-size: 14px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

@media (max-width: 760px) {
  .section-heading,
  .filter-grid,
  .pager {
    align-items: stretch;
    flex-direction: column;
    grid-template-columns: 1fr;
  }

  .message-item {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .message-side {
    grid-column: 2;
    justify-items: start;
  }
}
</style>
