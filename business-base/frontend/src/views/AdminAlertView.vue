<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useAlertStore } from "../stores/alert";
import { formatDateTime } from "../utils/format";
import { createIdempotencyKey } from "../utils/idempotency";
import {
  ALERT_SEVERITY_FILTERS,
  ALERT_STATUS_FILTERS,
  ALERT_TYPE_FILTERS,
  alertStatusLabel,
  alertStatusClass,
  alertTypeLabel,
  severityClass,
  severityLabel,
} from "../utils/message";
import type { AlertRecord, AlertStatus } from "../types/alert";

const store = useAlertStore();

const filters = reactive({
  alertStatus: "",
  alertType: "",
  severity: "",
  instanceId: "",
});

const handleTarget = ref<AlertRecord | null>(null);
const handleTargetStatus = ref<AlertStatus>("HANDLED");
const handleComment = ref("");
const handleError = ref("");

const idempotencyKeys = new Map<string, string>();

onMounted(() => {
  void store.loadAlerts(1);
});

function applyFilters(): void {
  store.applyFilters({
    alertStatus: filters.alertStatus,
    alertType: filters.alertType,
    severity: filters.severity,
    instanceId: filters.instanceId,
  });
}

function resetFilters(): void {
  filters.alertStatus = "";
  filters.alertType = "";
  filters.severity = "";
  filters.instanceId = "";
  store.applyFilters({
    alertStatus: "",
    alertType: "",
    severity: "",
    instanceId: "",
  });
}

function openHandle(alert: AlertRecord, targetStatus: AlertStatus): void {
  handleTarget.value = alert;
  handleTargetStatus.value = targetStatus;
  handleComment.value = "";
  handleError.value = "";
}

function cancelHandle(): void {
  handleTarget.value = null;
  handleComment.value = "";
  handleError.value = "";
}

async function confirmHandle(): Promise<void> {
  const target = handleTarget.value;
  if (!target) return;
  const alertId = target.alertId;
  let key = idempotencyKeys.get(alertId);
  if (!key) {
    key = createIdempotencyKey(`alert:handle:${alertId}`);
    idempotencyKeys.set(alertId, key);
  }
  try {
    await store.handleAlert(
      alertId,
      { targetStatus: handleTargetStatus.value, comment: handleComment.value.trim() || undefined },
      key,
    );
    idempotencyKeys.delete(alertId);
    cancelHandle();
  } catch (error) {
    handleError.value = error instanceof Error ? error.message : "告警处理失败";
  }
}

function prevPage(): void {
  if (store.pageNo <= 1 || store.loading) return;
  void store.loadAlerts(store.pageNo - 1);
}

function nextPage(): void {
  if (store.pageNo >= store.totalPages || store.loading) return;
  void store.loadAlerts(store.pageNo + 1);
}
</script>

<template>
  <section class="page-surface" aria-labelledby="admin-alerts-heading">
    <header class="section-heading">
      <div>
        <p class="eyebrow">Admin</p>
        <h1 id="admin-alerts-heading">异常告警管理</h1>
      </div>
      <button type="button" data-test="alerts-refresh" @click="store.loadAlerts()">
        刷新
      </button>
    </header>

    <form class="filter-grid" @submit.prevent="applyFilters">
      <label>
        告警状态
        <select v-model="filters.alertStatus" data-test="alerts-filter-status">
          <option v-for="opt in ALERT_STATUS_FILTERS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </label>
      <label>
        告警类型
        <select v-model="filters.alertType" data-test="alerts-filter-type">
          <option v-for="opt in ALERT_TYPE_FILTERS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </label>
      <label>
        告警级别
        <select v-model="filters.severity" data-test="alerts-filter-severity">
          <option v-for="opt in ALERT_SEVERITY_FILTERS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </label>
      <label>
        流程实例 ID
        <input v-model="filters.instanceId" type="search" autocomplete="off" data-test="alerts-filter-instance" />
      </label>
      <div class="filter-actions">
        <button type="submit" data-test="alerts-query">查询</button>
        <button type="button" @click="resetFilters">重置</button>
      </div>
    </form>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th scope="col">告警类型</th>
            <th scope="col">级别</th>
            <th scope="col">状态</th>
            <th scope="col">流程实例</th>
            <th scope="col">任务 ID</th>
            <th scope="col">创建时间</th>
            <th scope="col">处理人</th>
            <th scope="col">处理时间</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.loading">
            <td colspan="9" class="state-cell" role="status">加载中...</td>
          </tr>
          <tr v-else-if="store.error">
            <td colspan="9" class="state-cell is-error" role="alert">{{ store.error }}</td>
          </tr>
          <tr v-else-if="store.records.length === 0">
            <td colspan="9" class="empty-cell">暂无告警</td>
          </tr>
          <tr
            v-for="alert in store.loading || store.error ? [] : store.records"
            v-else
            :key="alert.alertId"
            :data-test="`alert-row-${alert.alertId}`"
          >
            <td>{{ alertTypeLabel(alert.alertType) }}</td>
            <td>
              <span class="severity-badge" :class="severityClass(alert.severity)">
                {{ severityLabel(alert.severity) }}
              </span>
            </td>
            <td>
              <span class="status-badge" :class="alertStatusClass(alert.alertStatus)">
                {{ alertStatusLabel(alert.alertStatus) }}
              </span>
            </td>
            <td>{{ alert.instanceId || "--" }}</td>
            <td>{{ alert.taskId || "--" }}</td>
            <td>{{ formatDateTime(alert.createdAt) }}</td>
            <td>{{ alert.handledBy || "--" }}</td>
            <td>{{ formatDateTime(alert.handledAt) }}</td>
            <td class="table-actions">
              <template v-if="alert.alertStatus === 'OPEN'">
                <button
                  type="button"
                  :data-test="`alert-handle-${alert.alertId}`"
                  @click="openHandle(alert, 'HANDLED')"
                >
                  处理
                </button>
                <button
                  type="button"
                  :data-test="`alert-ignore-${alert.alertId}`"
                  @click="openHandle(alert, 'IGNORED')"
                >
                  忽略
                </button>
              </template>
              <span v-else class="handled-text">已处置</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <footer class="pager">
      <button type="button" :disabled="store.pageNo <= 1 || store.loading" @click="prevPage">
        上一页
      </button>
      <span>第 {{ store.pageNo }} 页 / 共 {{ store.total }} 条</span>
      <button type="button" :disabled="store.pageNo >= store.totalPages || store.loading" @click="nextPage">
        下一页
      </button>
    </footer>

    <div v-if="handleTarget" class="modal-overlay" @click.self="cancelHandle">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="handle-modal-title">
        <h2 id="handle-modal-title">
          {{ handleTargetStatus === "HANDLED" ? "处理告警" : "忽略告警" }}
        </h2>
        <p class="modal-meta">
          告警 {{ handleTarget.alertId }} · {{ alertTypeLabel(handleTarget.alertType) }}
        </p>
        <label class="modal-field">
          处理说明
          <textarea v-model="handleComment" rows="3" data-test="handle-comment" />
        </label>
        <p v-if="handleError || store.handleError" class="modal-error" role="alert">
          {{ handleError || store.handleError }}
        </p>
        <div class="modal-actions">
          <button type="button" data-test="handle-cancel" @click="cancelHandle">取消</button>
          <button
            type="button"
            class="primary"
            data-test="handle-confirm"
            :disabled="store.handleLoading"
            @click="confirmHandle"
          >
            {{ store.handleLoading ? "提交中..." : "确认" }}
          </button>
        </div>
      </div>
    </div>
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

button.primary {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}

button.primary:hover:not(:disabled) {
  background: #1d4ed8;
  color: #fff;
}

select:focus,
input:focus,
button:focus-visible {
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
  min-width: 880px;
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

.empty-cell {
  padding: 20px;
  color: #5d6978;
  text-align: center;
  background: #f8fafc;
}

.severity-badge,
.status-badge {
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

.status-badge.is-open {
  background: #fff1f2;
  color: #be123c;
}

.status-badge.is-handled {
  background: #f0fdf4;
  color: #15803d;
}

.status-badge.is-ignored {
  background: #f1f5f9;
  color: #475569;
}

.handled-text {
  color: #94a3b8;
  font-size: 12px;
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

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgba(15, 23, 42, 0.45);
}

.modal {
  width: min(480px, 100%);
  border-radius: 10px;
  padding: 20px;
  background: #fff;
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.24);
}

.modal h2 {
  margin: 0 0 6px;
  color: #17202a;
  font-size: 18px;
}

.modal-meta {
  margin: 0 0 14px;
  color: #5d6978;
  font-size: 13px;
}

.modal-field {
  display: grid;
  gap: 6px;
  margin-bottom: 12px;
  color: #5d6978;
  font-size: 13px;
}

.modal-field textarea {
  width: 100%;
  border: 1px solid #d8dee8;
  border-radius: 6px;
  padding: 8px 10px;
  background: #fff;
  color: #17202a;
  font: inherit;
  resize: vertical;
}

.modal-error {
  margin: 0 0 12px;
  color: #be123c;
  font-size: 13px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
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
