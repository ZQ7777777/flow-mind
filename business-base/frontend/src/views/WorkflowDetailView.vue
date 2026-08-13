<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AttachmentPanel from "../components/workflow/AttachmentPanel.vue";
import CommentPanel from "../components/workflow/CommentPanel.vue";
import ProcessGraph from "../components/workflow/ProcessGraph.vue";
import ProcessTimeline from "../components/workflow/ProcessTimeline.vue";
import TaskActionPanel from "../components/workflow/TaskActionPanel.vue";
import VariableFormReadonly from "../components/workflow/VariableFormReadonly.vue";
import {
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
const formError = ref("");
const variableFormRef = ref<InstanceType<typeof VariableFormReadonly> | null>(null);
const formVariables = ref<Record<string, unknown>>({});
const pendingReplacements = ref<Record<string, {
  attachment: WorkflowAttachmentView;
  file: File;
  idempotencyKey: string;
}>>({});
const isEditableApply = computed(() => Boolean(detail.value?.allowedActions.includes("SUBMIT")));
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
        authStore.user?.userId === detail.value?.instance.starterUserId
    ),
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
  if ((payload.action === "SUBMIT" || payload.action === "DIRECT_SEND") && isEditableApply.value) {
    if (!variableFormRef.value?.validate()) {
      formError.value = "请修正表单字段后再提交";
      return;
    }
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
      variables: isEditableApply.value
        && (payload.action === "SUBMIT" || payload.action === "DIRECT_SEND")
        ? { ...formVariables.value }
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

async function remindCurrentTask(): Promise<void> {
  if (!activeTask.value?.taskId) return;

  reminderSuccess.value = "";

  try {
    await store.remindTask(activeTask.value.taskId, {
      expectedTaskVersion: activeTask.value.taskVersion,
      comment: "请尽快处理",
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
    const blob = await downloadAttachment(item.attachmentId);
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
      <header class="detail-header">
        <div class="detail-title-row">
          <div>
            <h1 id="detail-heading">{{ detail.instance.instanceTitle }}</h1>
            <p>
              {{ detail.instance.processName ?? "--" }} / {{ detail.instance.instanceStatus ?? "--" }} /
              {{ currentNodeNames }}
            </p>
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
        </div>
        <dl class="summary-grid">
          <div>
            <dt>发起人</dt>
            <dd>{{ detail.instance.starterUserName ?? "--" }}</dd>
          </div>
          <div>
            <dt>发起时间</dt>
            <dd>{{ formatDateTime(detail.instance.startedAt) }}</dd>
          </div>
          <div>
            <dt>实例版本</dt>
            <dd>{{ detail.instance.version ?? "--" }}</dd>
          </div>
        </dl>
      </header>

      <div v-if="deadlineWarning" class="deadline-banner" data-test="deadline-banner">
        <div>
          <strong>{{ deadlineWarning }}</strong>
          <span v-if="activeTask?.dueAt">到期时间：{{ formatDateTime(activeTask.dueAt) }}</span>
        </div>
      </div>

      <ProcessGraph
        :nodes="detail.nodes"
        :edges="detail.edges"
        :current-node-codes="detail.instance.currentNodeCodes"
      />
      <VariableFormReadonly
        ref="variableFormRef"
        :fields="detail.formFields"
        :variables="formVariables"
        :editable="isEditableApply"
        @update:variables="formVariables = $event"
      />
      <p v-if="formError" class="action-error" role="alert">{{ formError }}</p>
      <AttachmentPanel
        :attachments="detail.attachments"
        :can-upload="Boolean(detail.currentTask) && !isEditableApply"
        :can-replace="isEditableApply"
        :uploadable-attachments="detail.uploadableAttachments"
        @upload="uploadAttachment"
        @replace="stageReplacement"
        @download="download"
      />
      <p v-if="attachmentError" class="action-error" role="alert">{{ attachmentError }}</p>
      <p v-if="attachmentStatus" class="action-status" role="status">{{ attachmentStatus }}</p>
      <CommentPanel :comments="detail.comments" />
      <ProcessTimeline :items="detail.historyTasks" />
      <p v-if="store.actionError" class="action-error" role="alert">{{ store.actionError }}</p>
      <TaskActionPanel
        v-if="detail.currentTask"
        :task-version="detail.currentTask.taskVersion"
        :allowed-actions="detail.allowedActions"
        :disabled-actions="detail.disabledActions"
        :reject-target-nodes="detail.rejectTargetNodes"
        :submitting="store.actionSubmitting"
        @submit="submitAction"
      />
    </template>
    <div v-else class="state-line">暂无详情</div>
  </section>
</template>

<style scoped>
.page-surface {
  display: grid;
  gap: 0;
}

.detail-header {
  display: grid;
  gap: 16px;
  padding-bottom: 18px;
}

h1 {
  margin: 0;
  color: #111827;
  font-size: 24px;
  font-weight: 700;
}

p {
  margin: 6px 0 0;
  color: #6b7280;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin: 0;
}

.summary-grid div {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}

dt {
  color: #6b7280;
  font-size: 13px;
}

dd {
  margin: 6px 0 0;
  color: #111827;
  font-weight: 600;
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

.deadline-banner button {
  min-height: 34px;
  border: 1px solid #d97706;
  border-radius: 6px;
  padding: 6px 12px;
  background: #fff;
  color: #92400e;
  cursor: pointer;
  font: inherit;
}

.deadline-banner button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.detail-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.remind-button {
  min-height: 34px;
  border: 1px solid #2563eb;
  border-radius: 6px;
  padding: 6px 12px;
  background: #fff;
  color: #2563eb;
  cursor: pointer;
  font: inherit;
  flex-shrink: 0;
  margin-top: 18px;
}

.remind-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.action-success {
  margin: 0;
  border: 1px solid #86efac;
  border-radius: 6px;
  padding: 10px 12px;
  background: #f0fdf4;
  color: #166534;
}

.action-error {
  margin: 0;
  border: 1px solid #fecaca;
  border-radius: 6px;
  padding: 10px 12px;
  background: #fef2f2;
  color: #b91c1c;
}

.action-status {
  margin: 0;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  padding: 10px 12px;
  background: #eff6ff;
  color: #1d4ed8;
}
</style>
