<script setup lang="ts">
import { computed, ref } from "vue";
import type {
  WorkflowAttachmentView,
  WorkflowUploadableAttachmentView,
} from "../../types/workflow";
import { formatDateTime, formatFileSize } from "../../utils/format";

const props = defineProps<{
  attachments: WorkflowAttachmentView[];
  uploadableAttachments?: WorkflowUploadableAttachmentView[];
  canUpload?: boolean;
}>();

const emit = defineEmits<{
  upload: [payload: {
    file: File;
    ownerType: "INSTANCE" | "TASK";
    template: WorkflowUploadableAttachmentView;
  }];
  download: [attachment: WorkflowAttachmentView];
  delete: [attachment: WorkflowAttachmentView];
}>();

const selectedFile = ref<File | null>(null);
const selectedAttachmentCode = ref("");

const grouped = computed(() => ({
  instance: props.attachments.filter((item) => item.ownerType !== "TASK"),
  task: props.attachments.filter((item) => item.ownerType === "TASK"),
}));
const templates = computed(() => props.uploadableAttachments ?? []);
const selectedTemplate = computed(() =>
  templates.value.find((item) => item.attachmentCode === selectedAttachmentCode.value)
    ?? templates.value[0],
);
const uploadEnabled = computed(() => Boolean(props.canUpload && templates.value.length));
const canSubmitUpload = computed(() => Boolean(uploadEnabled.value && selectedFile.value && selectedTemplate.value));
const acceptExtensions = computed(() => {
  const values = selectedTemplate.value?.allowedExtensions ?? [];
  return values.length ? values.map((item) => `.${item.replace(/^\./, "")}`).join(",") : undefined;
});

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement;
  selectedFile.value = input.files?.[0] ?? null;
}

function submitUpload(): void {
  if (!canSubmitUpload.value || !selectedFile.value) {
    return;
  }
  const template = selectedTemplate.value;
  if (!template) return;
  emit("upload", {
    file: selectedFile.value,
    ownerType: template.ownerType === "INSTANCE" ? "INSTANCE" : "TASK",
    template,
  });
  selectedFile.value = null;
}
</script>

<template>
  <section class="workflow-section" aria-labelledby="attachments-heading">
    <div class="section-header">
      <h2 id="attachments-heading">附件</h2>
    </div>

    <form v-if="canUpload" class="upload-row" :class="{ disabled: !uploadEnabled }" @submit.prevent="submitUpload">
      <label>
        <span>文件</span>
        <input type="file" :accept="acceptExtensions" :disabled="!uploadEnabled" @change="onFileChange" />
      </label>
      <label>
        <span>材料类型</span>
        <select v-model="selectedAttachmentCode" :disabled="!uploadEnabled">
          <option
            v-for="template in templates"
            :key="template.attachmentCode"
            :value="template.attachmentCode"
          >
            {{ template.attachmentName || template.attachmentCode }}
          </option>
        </select>
      </label>
      <button type="submit" :disabled="!canSubmitUpload">上传</button>
      <p v-if="!uploadEnabled" class="upload-disabled">当前节点未配置可上传材料</p>
      <p v-else-if="selectedTemplate" class="upload-hint">
        {{ selectedTemplate.description || "按当前节点附件模板上传材料" }}
      </p>
    </form>

    <div class="attachment-groups">
      <div>
        <h3>实例附件</h3>
        <ul v-if="grouped.instance.length" class="attachment-list">
          <li v-for="attachment in grouped.instance" :key="attachment.attachmentId">
            <span class="file-name">{{ attachment.fileName }}</span>
            <span>{{ formatFileSize(attachment.sizeBytes) }}</span>
            <span>{{ formatDateTime(attachment.uploadedAt) }}</span>
            <button type="button" @click="emit('download', attachment)">
              下载
            </button>
            <button type="button" @click="emit('delete', attachment)">
              删除
            </button>
          </li>
        </ul>
        <p v-else class="empty-state">暂无实例附件</p>
      </div>

      <div>
        <h3>任务附件</h3>
        <ul v-if="grouped.task.length" class="attachment-list">
          <li v-for="attachment in grouped.task" :key="attachment.attachmentId">
            <span class="file-name">{{ attachment.fileName }}</span>
            <span>{{ formatFileSize(attachment.sizeBytes) }}</span>
            <span>{{ formatDateTime(attachment.uploadedAt) }}</span>
            <button type="button" @click="emit('download', attachment)">
              下载
            </button>
            <button type="button" @click="emit('delete', attachment)">
              删除
            </button>
          </li>
        </ul>
        <p v-else class="empty-state">暂无任务附件</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.workflow-section {
  padding: 20px 0;
  border-top: 1px solid #e5e7eb;
}

.section-header {
  margin-bottom: 12px;
}

h2,
h3 {
  margin: 0;
  color: #111827;
}

h2 {
  font-size: 18px;
  font-weight: 650;
}

h3 {
  margin-bottom: 8px;
  font-size: 15px;
}

.upload-row {
  display: grid;
  grid-template-columns: minmax(180px, 1.5fr) minmax(110px, 0.8fr) minmax(140px, 1fr) minmax(140px, 1fr) auto;
  gap: 10px;
  align-items: end;
  margin-bottom: 16px;
}

label {
  display: grid;
  gap: 6px;
  color: #374151;
  font-size: 13px;
}

input,
select {
  min-height: 36px;
  box-sizing: border-box;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 8px;
}

.upload-row.disabled {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px;
  background: #f9fafb;
  color: #9ca3af;
}

.upload-disabled,
.upload-hint {
  align-self: center;
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}

button {
  min-height: 36px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 0 12px;
  background: #fff;
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

input:focus,
select:focus,
button:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}

.attachment-groups {
  display: grid;
  gap: 18px;
}

.attachment-list {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.attachment-list li {
  display: grid;
  grid-template-columns: minmax(150px, 1fr) 90px 150px auto auto;
  gap: 8px;
  align-items: center;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  color: #4b5563;
  font-size: 13px;
}

.file-name {
  overflow-wrap: anywhere;
  color: #111827;
  font-weight: 600;
}

.empty-state {
  margin: 0;
  color: #6b7280;
}

@media (max-width: 760px) {
  .upload-row,
  .attachment-list li {
    grid-template-columns: 1fr;
  }
}
</style>
