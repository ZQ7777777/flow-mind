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
  deleteAttachment,
  downloadAttachment,
  uploadInstanceAttachment,
  uploadTaskAttachment,
  replaceInstanceAttachment,
} from "../api/workflow";
import { WorkflowApiError } from "../api/http";
import { useWorkflowStore } from "../stores/workflow";
import type { TaskActionCode, WorkflowAttachmentView } from "../types/workflow";
import { formatDateTime } from "../utils/format";
import { createIdempotencyKey } from "../utils/idempotency";

const props = defineProps<{
  mode: "task" | "instance";
}>();

const route = useRoute();
const router = useRouter();
const store = useWorkflowStore();

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
    if ((payload.action === "SUBMIT" || payload.action === "DIRECT_SEND")
        && detail.value?.instance.instanceId) {
      await router.replace({
        name: "workflow-instance-detail",
        params: { instanceId: detail.value.instance.instanceId },
      });
      return;
    }
    await loadDetail();
  } catch (error) {
    if (error instanceof WorkflowApiError && error.status === 409) {
      await loadDetail();
    }
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
  fieldCode: string;
  attachmentCode: string;
}): Promise<void> {
  attachmentError.value = "";
  const uploadPayload = {
    ...payload,
    sourceTaskId: detail.value?.currentTask?.taskId,
    expectedTaskVersion: detail.value?.currentTask?.taskVersion,
    idempotencyKey: createIdempotencyKey("workflow:attachment-upload"),
  };
  try {
    if (detail.value?.currentTask?.taskId) {
      await uploadTaskAttachment(detail.value.currentTask.taskId, uploadPayload);
    } else if (detail.value?.instance.instanceId) {
      await uploadInstanceAttachment(detail.value.instance.instanceId, uploadPayload);
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

async function remove(item: WorkflowAttachmentView): Promise<void> {
  attachmentError.value = "";
  try {
    await deleteAttachment(
      item.attachmentId,
      createIdempotencyKey("workflow:attachment-delete"),
    );
    await loadDetail();
  } catch (error) {
    attachmentError.value = error instanceof Error ? error.message : "附件删除失败";
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
      <header class="detail-header">
        <div>
          <h1 id="detail-heading">{{ detail.instance.instanceTitle }}</h1>
          <p>
            {{ detail.instance.processName ?? "--" }} / {{ detail.instance.instanceStatus ?? "--" }} /
            {{ currentNodeNames }}
          </p>
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
        :can-delete="!isEditableApply"
        @upload="uploadAttachment"
        @replace="stageReplacement"
        @download="download"
        @delete="remove"
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
