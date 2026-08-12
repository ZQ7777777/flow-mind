<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
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
} from "../api/workflow";
import { WorkflowApiError } from "../api/http";
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
  if (props.mode === "task" && taskId.value) {
    await store.loadTaskDetail(taskId.value);
    return;
  }
  if (instanceId.value) {
    await store.loadInstanceDetail(instanceId.value);
  }
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
  if (!taskId.value) {
    return;
  }
  const idempotencyKey = actionKeys.get(payload.action)
    ?? createIdempotencyKey(`workflow:${payload.action.toLowerCase()}`);
  actionKeys.set(payload.action, idempotencyKey);
  try {
    await store.submitAction(taskId.value, payload.action, {
      expectedTaskVersion: payload.expectedTaskVersion,
      comment: payload.comment,
      targetNodeCode: payload.targetNodeCode,
      targetUserId: payload.targetUserId,
      targetUserName: payload.targetUserName,
      addSignUserIds: payload.addSignUserIds,
      idempotencyKey,
    });
    actionKeys.delete(payload.action);
    await loadDetail();
  } catch (error) {
    if (error instanceof WorkflowApiError && error.status === 409) {
      await loadDetail();
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
      <VariableFormReadonly :fields="detail.formFields" :variables="detail.instance.variables" />
      <AttachmentPanel
        :attachments="detail.attachments"
        :can-upload="Boolean(detail.currentTask)"
        :uploadable-attachments="detail.uploadableAttachments"
        @upload="uploadAttachment"
        @download="download"
        @delete="remove"
      />
      <p v-if="attachmentError" class="action-error" role="alert">{{ attachmentError }}</p>
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
</style>
