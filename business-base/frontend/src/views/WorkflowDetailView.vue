<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowDown, ArrowUp, Finished, Tickets } from "@element-plus/icons-vue";
import AttachmentPanel from "../components/workflow/AttachmentPanel.vue";
import ProcessTimeline from "../components/workflow/ProcessTimeline.vue";
import TaskActionPanel from "../components/workflow/TaskActionPanel.vue";
import VariableFormReadonly from "../components/workflow/VariableFormReadonly.vue";
import {
  deleteAttachment as deleteWorkflowAttachment,
  downloadAttachment,
  uploadInstanceAttachment,
  uploadTaskAttachment,
  replaceInstanceAttachment,
} from "../api/workflow";
import { WorkflowApiError } from "../api/http";
import { useAuthStore } from "../stores/auth";
import { useWorkflowStore } from "../stores/workflow";
import type {
  TaskActionCode,
  WorkflowAttachmentView,
  WorkflowUploadableAttachmentView,
} from "../types/workflow";
import { formatDateTime } from "../utils/format";
import { createIdempotencyKey } from "../utils/idempotency";

const props = defineProps<{
  mode: "task" | "instance";
}>();

const route = useRoute();
const router = useRouter();
const store = useWorkflowStore();
const authStore = useAuthStore();

const detail = computed(() => store.detail.data);
const instanceId = computed(() => String(route.params.instanceId ?? ""));
const taskId = computed(() => String(route.params.taskId ?? ""));
const currentNodeNames = computed(() => {
  if (!detail.value) return "--";
  const names = detail.value.instance.currentNodeCodes.map((code) =>
    detail.value?.nodes.find((node) => node.nodeCode === code)?.nodeName ?? code,
  );
  return names.length ? names.join("、") : "--";
});
const actionKeys = new Map<TaskActionCode, string>();
const actionsThatLeaveCurrentTask = new Set<TaskActionCode>([
  "APPROVE",
  "SUBMIT",
  "REJECT",
  "RETURN",
  "WITHDRAW",
  "DIRECT_SEND",
  "TRANSFER",
  "DELEGATE",
  "ADD_SIGN",
]);
const attachmentError = ref("");
const attachmentStatus = ref("");
const deletingAttachmentId = ref("");
const formError = ref("");
const variableFormRef = ref<InstanceType<typeof VariableFormReadonly> | null>(null);
const formVariables = ref<Record<string, unknown>>({});
const timelineExpanded = ref(false);
const pendingReplacements = ref<Record<string, {
  attachment: WorkflowAttachmentView;
  file: File;
  idempotencyKey: string;
}>>({});
const displayedAttachments = computed(() => (detail.value?.attachments ?? []).map((attachment) => {
  const replacement = pendingReplacements.value[attachment.attachmentId];
  if (!replacement) return attachment;
  return {
    ...attachment,
    fileName: replacement.file.name,
    sizeBytes: replacement.file.size,
    contentType: replacement.file.type || attachment.contentType,
  };
}));
const isEditableApply = computed(() => Boolean(detail.value?.allowedActions.includes("SUBMIT")));
const canEditBusinessFields = computed(() =>
  Boolean(detail.value?.currentTask && detail.value.formFields.some(fieldAllowsEdit)),
);
const reminderSuccess = ref("");
const currentTask = computed(() => detail.value?.currentTask ?? null);
const activeTask = computed(
  () => detail.value?.activeTasks?.[0] ?? null,
);
const deadlineWarning = computed(() => {
  const task = currentTask.value;
  if (!task) return null;
  if (task.deadlineStatus === "OVERDUE") return "已超时";
  if (task.deadlineStatus === "DUE_SOON") return "即将超时";
  return null;
});
const canRemindCurrentTask = computed(() =>
  Boolean(
    props.mode === "instance" &&
    activeTask.value?.taskId &&
    authStore.user?.userId === detail.value?.instance.starterUserId,
  ),
);
const breadcrumbHome = computed(() => {
  const source = typeof route.query.from === "string" ? route.query.from : "";
  const items: Record<string, { label: string; path: string }> = {
    todo: { label: "我的待办", path: "/workflow/todo" },
    started: { label: "我发起的", path: "/workflow/started" },
    completed: { label: "我的已办", path: "/workflow/completed" },
    read: { label: "我的已阅", path: "/workflow/read" },
  };
  if (source in items) return items[source];
  return props.mode === "instance" ? items.started : items.todo;
});
const summaryItems = computed(() => {
  const loaded = detail.value;
  if (!loaded) return [];
  return [
    { label: "业务类型", value: loaded.instance.processName ?? loaded.definition?.processName ?? "--" },
    { label: "申请编号", value: loaded.instance.instanceId ?? "--" },
    { label: "当前节点", value: currentNodeNames.value },
    { label: "发起人", value: loaded.instance.starterUserName ?? "--" },
    { label: "发起时间", value: formatDateTime(loaded.instance.startedAt) },
    { label: "实例版本", value: loaded.instance.version ?? "--" },
  ];
});
const statusLabel = computed(() => instanceStatusLabel(detail.value?.instance.instanceStatus));
const statusClass = computed(() => {
  const status = detail.value?.instance.instanceStatus;
  return {
    "is-running": status === "RUNNING",
    "is-completed": status === "COMPLETED",
    "is-stopped": status === "TERMINATED" || status === "CANCELLED",
  };
});
const timelineItems = computed(() => detail.value?.historyTasks ?? []);
const timelineSummary = computed(() =>
  currentNodeNames.value === "--" ? "暂无当前节点" : `当前节点：${currentNodeNames.value}`,
);

onMounted(() => {
  void loadDetail();
});

watch(
  () => [props.mode, route.params.taskId, route.params.instanceId],
  () => {
    void loadDetail();
  },
);

async function loadDetail(): Promise<void> {
  pendingReplacements.value = {};
  attachmentStatus.value = "";
  deletingAttachmentId.value = "";
  if (props.mode === "task" && taskId.value) {
    await store.loadTaskDetail(taskId.value);
    if (store.detail.errorCode === "FLOW_TASK_NOT_FOUND") {
      await router.replace({ name: "workflow-todo" });
      return;
    }
    formVariables.value = definitionVariables();
    return;
  }
  if (instanceId.value) {
    await store.loadInstanceDetail(instanceId.value);
    formVariables.value = definitionVariables();
  }
}

function definitionVariables(): Record<string, unknown> {
  const loaded = store.detail.data;
  if (!loaded) return {};
  const values: Record<string, unknown> = {};
  for (const field of loaded.formFields) {
    if (Object.prototype.hasOwnProperty.call(loaded.instance.variables, field.fieldCode)) {
      values[field.fieldCode] = loaded.instance.variables[field.fieldCode];
    }
  }
  return values;
}

async function submitAction(payload: {
  action: TaskActionCode;
  expectedTaskVersion: number;
  comment: string;
  targetNodeCode?: string;
  targetUserId?: string;
  targetUserName?: string;
  addSignUserIds?: string[];
}): Promise<void> {
  const actionTaskId = detail.value?.currentTask?.taskId || taskId.value;
  if (!actionTaskId) {
    return;
  }
  formError.value = "";
  if (shouldSubmitVariables(payload.action) && canEditBusinessFields.value) {
    if (!variableFormRef.value?.validate()) {
      formError.value = "请修正表单字段后再提交";
      return;
    }
  }
  if ((payload.action === "SUBMIT" || payload.action === "DIRECT_SEND") && isEditableApply.value) {
    try {
      await savePendingReplacements();
    } catch {
      return;
    }
  }
  const idempotencyKey = actionKeys.get(payload.action)
    ?? createIdempotencyKey(`workflow:${payload.action.toLowerCase()}`);
  actionKeys.set(payload.action, idempotencyKey);
  try {
    await store.submitAction(actionTaskId, payload.action, {
      expectedTaskVersion: payload.expectedTaskVersion,
      comment: payload.comment,
      targetNodeCode: payload.targetNodeCode,
      targetUserId: payload.targetUserId,
      targetUserName: payload.targetUserName,
      addSignUserIds: payload.addSignUserIds,
      variables: shouldSubmitVariables(payload.action) && canEditBusinessFields.value
        ? editableVariables()
        : undefined,
      idempotencyKey,
    });
    actionKeys.delete(payload.action);
    if (actionsThatLeaveCurrentTask.has(payload.action)) {
      await router.replace({ name: "workflow-todo" });
      return;
    }
    await loadDetail();
  } catch (error) {
    if (error instanceof WorkflowApiError && error.status === 409) {
      await loadDetail();
    }
  }
}

function fieldAllowsEdit(field: { visible?: boolean; editable?: boolean }): boolean {
  return field.visible !== false && (field.editable === true || field.editable == null && isEditableApply.value);
}

function shouldSubmitVariables(action: TaskActionCode): boolean {
  return action === "APPROVE" || action === "SUBMIT" || action === "DIRECT_SEND";
}

function editableVariables(): Record<string, unknown> {
  const loaded = detail.value;
  if (!loaded) return {};
  const values: Record<string, unknown> = {};
  for (const field of loaded.formFields) {
    if (!fieldAllowsEdit(field)) continue;
    if (Object.prototype.hasOwnProperty.call(formVariables.value, field.fieldCode)) {
      values[field.fieldCode] = formVariables.value[field.fieldCode];
    }
  }
  return values;
}

function toggleTimeline(): void {
  timelineExpanded.value = !timelineExpanded.value;
}

function goBack(): void {
  router.back();
}

function goBreadcrumb(): void {
  void router.push(breadcrumbHome.value.path);
}

function instanceStatusLabel(status: string | undefined): string {
  const labels: Record<string, string> = {
    RUNNING: "运行中",
    COMPLETED: "已完成",
    TERMINATED: "已终止",
    CANCELLED: "已取消",
  };
  return status ? labels[status] ?? status : "--";
}

async function remindCurrentTask(): Promise<void> {
  if (!activeTask.value?.taskId) return;

  reminderSuccess.value = "";

  try {
    await store.remindTask(activeTask.value.taskId, {
      expectedTaskVersion: activeTask.value.taskVersion,
      comment: "",
      idempotencyKey: createIdempotencyKey("workflow:remind"),
    });

    reminderSuccess.value = "催办已发送";
    await loadDetail();
  } catch {
    // store.reminderError drives the visible error message.
  }
}
function stageReplacement(payload: { attachment: WorkflowAttachmentView; file: File }): void {
  pendingReplacements.value = {
    ...pendingReplacements.value,
    [payload.attachment.attachmentId]: {
      attachment: payload.attachment,
      file: payload.file,
      idempotencyKey: createIdempotencyKey("workflow:attachment-replace"),
    },
  };
  attachmentError.value = "";
  attachmentStatus.value = `已选择 ${payload.file.name}，将在提交前替换`;
}

async function savePendingReplacements(): Promise<void> {
  const task = detail.value?.currentTask;
  if (!task) return;
  attachmentError.value = "";
  attachmentStatus.value = "";
  for (const replacement of Object.values(pendingReplacements.value)) {
    try {
      const saved = await replaceInstanceAttachment(
        task.taskId,
        replacement.attachment.attachmentId,
        replacement.file,
        task.taskVersion,
        replacement.idempotencyKey,
      );
      if (detail.value) {
        detail.value.attachments = detail.value.attachments.map((item) =>
          item.attachmentId === replacement.attachment.attachmentId ? saved : item,
        );
      }
      const next = { ...pendingReplacements.value };
      delete next[replacement.attachment.attachmentId];
      pendingReplacements.value = next;
    } catch (error) {
      attachmentError.value = error instanceof Error ? error.message : "附件替换失败";
      throw error;
    }
  }
}

async function uploadAttachment(payload: {
  file: File;
  ownerType: "INSTANCE" | "TASK";
  template: WorkflowUploadableAttachmentView;
}): Promise<void> {
  attachmentError.value = "";
  const currentTask = detail.value?.currentTask;
  const currentInstanceId = detail.value?.instance.instanceId;
  if (!currentTask || !currentInstanceId) {
    attachmentError.value = "当前详情不可上传附件";
    return;
  }
  try {
    if (payload.ownerType === "TASK") {
      await uploadTaskAttachment(currentTask.taskId, {
        file: payload.file,
        ownerType: payload.ownerType,
        attachmentCode: payload.template.attachmentCode,
        fieldCode: payload.template.fieldCode,
        instanceId: currentInstanceId,
        expectedTaskVersion: currentTask.taskVersion,
        idempotencyKey: createIdempotencyKey("workflow:attachment-upload-task"),
      });
    } else {
      await uploadInstanceAttachment(currentInstanceId, {
        file: payload.file,
        ownerType: payload.ownerType,
        attachmentCode: payload.template.attachmentCode,
        fieldCode: payload.template.fieldCode,
        sourceTaskId: currentTask.taskId,
        expectedTaskVersion: currentTask.taskVersion,
        idempotencyKey: createIdempotencyKey("workflow:attachment-upload-instance"),
      });
    }
    await loadDetail();
  } catch (error) {
    attachmentError.value = error instanceof Error ? error.message : "附件上传失败";
  }
}

async function download(item: WorkflowAttachmentView): Promise<void> {
  attachmentError.value = "";
  try {
    const blob = pendingReplacements.value[item.attachmentId]?.file
      ?? await downloadAttachment(item.attachmentId);
    const href = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = href;
    anchor.download = item.fileName;
    anchor.click();
    URL.revokeObjectURL(href);
  } catch (error) {
    attachmentError.value = error instanceof Error ? error.message : "附件下载失败";
  }
}

async function removeAttachment(item: WorkflowAttachmentView): Promise<void> {
  if (deletingAttachmentId.value) return;
  if (!window.confirm(`确认删除附件“${item.fileName}”？`)) return;

  attachmentError.value = "";
  attachmentStatus.value = "";
  deletingAttachmentId.value = item.attachmentId;
  try {
    await deleteWorkflowAttachment(
      item.attachmentId,
      createIdempotencyKey("workflow:attachment-delete"),
    );
    if (detail.value) {
      detail.value.attachments = detail.value.attachments.filter(
        (attachment) => attachment.attachmentId !== item.attachmentId,
      );
    }
    const next = { ...pendingReplacements.value };
    delete next[item.attachmentId];
    pendingReplacements.value = next;
    attachmentStatus.value = `已删除 ${item.fileName}`;
  } catch (error) {
    attachmentError.value = error instanceof Error ? error.message : "附件删除失败";
  } finally {
    deletingAttachmentId.value = "";
  }
}

</script>

<template>
  <section class="page-surface" aria-labelledby="detail-heading">
    <div v-if="store.detail.loading" class="state-line" role="status">加载中...</div>
    <div v-else-if="store.detail.error" class="state-line is-error" role="alert">
      {{ store.detail.error }}
    </div>
    <template v-else-if="detail">
      <p v-if="reminderSuccess" class="action-success" role="status">{{ reminderSuccess }}</p>
      <p v-if="store.reminderError" class="action-error" role="alert">{{ store.reminderError }}</p>

      <header class="detail-topbar">
        <div class="detail-navigation">
          <button type="button" class="back-button" data-test="detail-back" @click="goBack">返回</button>
          <nav class="detail-breadcrumb" aria-label="审批详情位置">
            <a :href="breadcrumbHome.path" @click.prevent="goBreadcrumb">{{ breadcrumbHome.label }}</a>
            <span aria-hidden="true">/</span>
            <strong>{{ detail.instance.instanceTitle }}</strong>
          </nav>
        </div>
        <button
          v-if="canRemindCurrentTask"
          type="button"
          class="remind-button"
          data-test="remind-task"
          :disabled="store.reminderSubmitting"
          @click="remindCurrentTask"
        >
          {{ store.reminderSubmitting ? "发送中" : "发送催办" }}
        </button>
      </header>

      <div class="detail-layout has-assistant">
        <main class="detail-main" aria-label="审批详情主要内容">
          <section class="summary-card" aria-labelledby="detail-heading">
            <span class="summary-icon" aria-hidden="true"><Tickets /></span>
            <div class="summary-content">
              <div class="summary-title-row">
                <h1 id="detail-heading">{{ detail.instance.instanceTitle }}</h1>
                <span class="status-badge" :class="statusClass">{{ statusLabel }}</span>
              </div>
              <dl class="summary-grid">
                <div v-for="item in summaryItems" :key="item.label">
                  <dt>{{ item.label }}</dt>
                  <dd>{{ item.value }}</dd>
                </div>
              </dl>
            </div>
          </section>

          <div v-if="deadlineWarning" class="deadline-banner" data-test="deadline-banner">
            <div>
              <strong>{{ deadlineWarning }}</strong>
              <span v-if="activeTask?.dueAt">到期时间：{{ formatDateTime(activeTask.dueAt) }}</span>
            </div>
          </div>

          <section class="content-card">
            <VariableFormReadonly
              ref="variableFormRef"
              :fields="detail.formFields"
              :variables="formVariables"
              :editable="canEditBusinessFields"
              @update:variables="formVariables = $event"
            />
          </section>
          <p v-if="formError" class="action-error" role="alert">{{ formError }}</p>

          <section class="content-card">
            <AttachmentPanel
              :attachments="displayedAttachments"
              :can-upload="Boolean(detail.currentTask) && !isEditableApply"
              :can-replace="isEditableApply"
              can-delete
              :current-user-id="authStore.user?.userId"
              :deleting-attachment-id="deletingAttachmentId"
              :uploadable-attachments="detail.uploadableAttachments"
              @upload="uploadAttachment"
              @replace="stageReplacement"
              @download="download"
              @delete="removeAttachment"
            />
          </section>
          <p v-if="attachmentError" class="action-error" role="alert">{{ attachmentError }}</p>
          <p v-if="attachmentStatus" class="action-status" role="status">{{ attachmentStatus }}</p>
        </main>

        <aside class="assistant-panel" aria-label="审批辅助信息">
          <section class="timeline-card" :class="{ 'is-expanded': timelineExpanded }">
            <button
              type="button"
              class="timeline-card-header"
              data-test="timeline-toggle"
              :aria-expanded="timelineExpanded"
              aria-controls="detail-timeline-body"
              @click="toggleTimeline"
            >
              <span class="timeline-title-wrap">
                <span class="timeline-card-icon" aria-hidden="true"><Finished /></span>
                <span>
                  <strong>流程轨迹</strong>
                  <small>{{ timelineSummary }}</small>
                </span>
              </span>
              <span class="timeline-toggle-icon" aria-hidden="true">
                <ArrowUp v-if="timelineExpanded" />
                <ArrowDown v-else />
              </span>
            </button>
            <Transition name="timeline-collapse">
              <div v-if="timelineExpanded" id="detail-timeline-body" class="timeline-card-body">
                <ProcessTimeline
                  :items="timelineItems"
                  :nodes="detail.nodes"
                  :current-node-codes="detail.instance.currentNodeCodes"
                  :comments="detail.comments"
                />
              </div>
            </Transition>
          </section>
          <p v-if="store.actionError" class="action-error" role="alert">{{ store.actionError }}</p>
          <section v-if="detail.currentTask" class="assistant-action-card">
            <TaskActionPanel
              :task-version="detail.currentTask.taskVersion"
              :allowed-actions="detail.allowedActions"
              :disabled-actions="detail.disabledActions"
              :reject-target-nodes="detail.rejectTargetNodes"
              :submitting="store.actionSubmitting"
              @submit="submitAction"
            />
          </section>
        </aside>
      </div>
    </template>
    <div v-else class="state-line">暂无详情</div>
  </section>
</template>

<style scoped>
.page-surface {
  display: grid;
  gap: 14px;
}

.detail-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-navigation,
.detail-breadcrumb {
  display: flex;
  align-items: center;
  min-width: 0;
}

.detail-navigation {
  gap: 12px;
}

.detail-breadcrumb {
  gap: 8px;
  color: #5d6978;
  font-size: 14px;
}

.detail-breadcrumb a {
  color: #5d6978;
  font-weight: 700;
  text-decoration: none;
}

.detail-breadcrumb a:hover {
  color: #2563eb;
}

.detail-breadcrumb strong {
  min-width: 0;
  overflow: hidden;
  color: #17202a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.back-button,
.remind-button,
.dialog-header button {
  min-height: 34px;
  border: 1px solid #d8dee8;
  border-radius: 6px;
  padding: 6px 12px;
  background: #fff;
  color: #17202a;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}

.back-button:hover,
.remind-button:hover:not(:disabled),
.dialog-header button:hover {
  border-color: #2563eb;
  color: #2563eb;
}

.back-button::before {
  content: "‹";
  margin-right: 4px;
  font-size: 18px;
  line-height: 0;
}

.remind-button {
  flex-shrink: 0;
  color: #2563eb;
}

.remind-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 300px);
  gap: 16px;
  align-items: start;
}

.detail-main,
.assistant-panel {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.summary-card,
.content-card,
.assistant-panel {
  border: 1px solid #d8dee8;
  border-radius: 8px;
  background: #fff;
}

.summary-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  padding: 18px;
}

.summary-icon,
.assistant-icon {
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #ecfdf5;
  color: #0f766e;
}

.summary-icon {
  width: 54px;
  height: 54px;
}

.summary-icon :deep(svg),
.assistant-icon :deep(svg) {
  width: 24px;
  height: 24px;
}

.summary-content {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.summary-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

h1,
h2,
p,
dl {
  margin: 0;
}

h1 {
  min-width: 0;
  overflow-wrap: anywhere;
  color: #17202a;
  font-size: 24px;
  line-height: 1.25;
  font-weight: 700;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 18px;
}

.summary-grid div {
  min-width: 0;
}

dt {
  color: #5d6978;
  font-size: 13px;
}

dd {
  margin: 5px 0 0;
  overflow-wrap: anywhere;
  color: #17202a;
  font-size: 14px;
  font-weight: 650;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  border: 1px solid #d8dee8;
  border-radius: 999px;
  padding: 2px 10px;
  background: #f8fafc;
  color: #344054;
  font-size: 12px;
  font-weight: 800;
}

.status-badge.is-running {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.status-badge.is-completed {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.status-badge.is-stopped {
  border-color: #fecaca;
  background: #fff1f2;
  color: #be123c;
}

.content-card {
  padding: 0 16px;
}

.content-card :deep(.workflow-section) {
  border-top: 0;
}

.content-card :deep(.variable-grid) {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.assistant-action-card {
  min-width: 0;
}

.assistant-action-card :deep(.workflow-section) {
  border-top: 1px solid #d8dee8;
  padding: 14px 0 0;
}

.state-line {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 22px;
  background: #fff;
  color: #6b7280;
}

.is-error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.deadline-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid #f59e0b;
  border-radius: 6px;
  padding: 12px;
  background: #fffbeb;
  color: #92400e;
}

.deadline-banner div {
  display: grid;
  gap: 4px;
}

.deadline-banner strong {
  color: #78350f;
}

.deadline-banner span {
  font-size: 13px;
}

.action-success,
.action-error,
.action-status {
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 10px 12px;
}

.action-success {
  border-color: #86efac;
  background: #f0fdf4;
  color: #166534;
}

.action-error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.action-status {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.assistant-panel {
  position: sticky;
  top: 18px;
  padding: 12px;
}

.timeline-card {
  overflow: hidden;
  border: 1px solid #d8dee8;
  border-radius: 8px;
  background: #fff;
}

.timeline-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  min-height: 66px;
  border: 0;
  padding: 12px;
  background: #f8fafc;
  color: #17202a;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.timeline-card-header:hover {
  background: #fff;
}

.timeline-title-wrap {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.timeline-title-wrap strong,
.timeline-title-wrap small {
  display: block;
  overflow-wrap: anywhere;
}

.timeline-title-wrap strong {
  font-size: 15px;
}

.timeline-title-wrap small {
  margin-top: 4px;
  color: #5d6978;
  font-size: 12px;
  line-height: 1.5;
}

.timeline-card-icon,
.timeline-toggle-icon {
  display: grid;
  place-items: center;
  border-radius: 8px;
}

.timeline-card-icon {
  width: 36px;
  height: 36px;
  background: #ecfdf5;
  color: #0f766e;
}

.timeline-toggle-icon {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  color: #2563eb;
}

.timeline-card-icon :deep(svg),
.timeline-toggle-icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.timeline-card-body {
  border-top: 1px solid #d8dee8;
  padding: 0 12px 12px;
}

.timeline-card-body :deep(.workflow-section) {
  border-top: 0;
  padding-bottom: 0;
}

.timeline-collapse-enter-active,
.timeline-collapse-leave-active {
  overflow: hidden;
  transition: max-height 180ms ease, opacity 180ms ease;
}

.timeline-collapse-enter-from,
.timeline-collapse-leave-to {
  max-height: 0;
  opacity: 0;
}

.timeline-collapse-enter-to,
.timeline-collapse-leave-from {
  max-height: 720px;
  opacity: 1;
}
button:focus-visible,
a:focus-visible {
  outline: 3px solid #f59e0b;
  outline-offset: 2px;
}

@media (max-width: 1180px) {
  .content-card :deep(.variable-grid),
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .assistant-panel {
    position: static;
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .detail-topbar,
  .summary-title-row {
    align-items: stretch;
    flex-direction: column;
  }

  .detail-navigation {
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
  }

  .summary-card,
  .assistant-panel {
    grid-template-columns: 1fr;
  }

  .content-card :deep(.variable-grid),
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
