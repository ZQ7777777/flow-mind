<script setup lang="ts">
import { computed, onMounted, watch } from "vue";
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
import { useWorkflowStore } from "../stores/workflow";
import type { TaskActionCode, WorkflowAttachment } from "../types/workflow";
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
}): Promise<void> {
  if (!taskId.value) {
    return;
  }
  await store.submitAction(taskId.value, payload.action, {
    expectedTaskVersion: payload.expectedTaskVersion,
    comment: payload.comment,
    targetNodeCode: payload.targetNodeCode,
    targetUserId: payload.targetUserId,
    idempotencyKey: createIdempotencyKey(`workflow:${payload.action.toLowerCase()}`),
  });
  await loadDetail();
}

async function uploadAttachment(payload: {
  file: File;
  fieldCode: string;
  templateCode: string;
}): Promise<void> {
  const uploadPayload = {
    ...payload,
    idempotencyKey: createIdempotencyKey("workflow:attachment-upload"),
  };
  if (detail.value?.currentTask?.taskId) {
    await uploadTaskAttachment(detail.value.currentTask.taskId, uploadPayload);
  } else if (detail.value?.instance.instanceId) {
    await uploadInstanceAttachment(detail.value.instance.instanceId, uploadPayload);
  }
  await loadDetail();
}

async function download(item: WorkflowAttachment): Promise<void> {
  const blob = await downloadAttachment(item.attachmentId);
  const href = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = href;
  anchor.download = item.fileName;
  anchor.click();
  URL.revokeObjectURL(href);
}

async function remove(item: WorkflowAttachment): Promise<void> {
  await deleteAttachment(
    item.attachmentId,
    createIdempotencyKey("workflow:attachment-delete"),
  );
  await loadDetail();
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
          <h1 id="detail-heading">{{ detail.instance.title }}</h1>
          <p>
            {{ detail.instance.processName }} / {{ detail.instance.status }} /
            {{ detail.instance.currentNodeName ?? "--" }}
          </p>
        </div>
        <dl class="summary-grid">
          <div>
            <dt>发起人</dt>
            <dd>{{ detail.instance.starterName ?? "--" }}</dd>
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

      <ProcessGraph :graph="detail.graph" />
      <VariableFormReadonly :fields="detail.formFields" :variables="detail.variables" />
      <AttachmentPanel
        :attachments="detail.attachments"
        :can-upload="Boolean(detail.currentTask)"
        @upload="uploadAttachment"
        @download="download"
        @delete="remove"
      />
      <CommentPanel :comments="detail.comments" />
      <ProcessTimeline :items="detail.timeline" />
      <TaskActionPanel
        v-if="detail.currentTask"
        :task-version="detail.currentTask.taskVersion"
        :allowed-actions="detail.currentTask.allowedActions"
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
</style>
