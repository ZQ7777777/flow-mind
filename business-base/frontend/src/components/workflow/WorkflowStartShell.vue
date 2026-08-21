<script setup lang="ts">
import { computed, markRaw, onBeforeUnmount, onMounted, ref, shallowRef, toRaw, watch, type Component } from "vue";
import type { UploadFile } from "element-plus";
import { UploadFilled } from "@element-plus/icons-vue";
import { fetchWorkflowStartContext, startWorkflowProcess } from "../../api/workflow";
import type { WorkflowStartAttachmentRule, WorkflowStartContext } from "../../types/workflow";
import { createIdempotencyKey } from "../../utils/idempotency";
import { formatFileSize } from "../../utils/format";

interface LocalAttachmentRow {
  id: string;
  index: number;
  rule: WorkflowStartAttachmentRule;
  file: File;
  extension: string;
}

interface AttachmentTemplateRow {
  index: number;
  rule: WorkflowStartAttachmentRule;
  files: LocalAttachmentRow[];
}

const props = defineProps<{
  processCode: string;
  businessForm: Component;
}>();

const context = shallowRef<WorkflowStartContext>();
const businessFormComponent = computed(() => markRaw(toRaw(props.businessForm)));
const variables = ref<Record<string, unknown>>({});
const attachments = ref<Record<string, File[]>>({});
const formRef = ref<{ validate?: () => Promise<boolean> | boolean }>();
const loading = ref(false);
const submitting = ref(false);
const error = ref("");
const success = ref("");
const activeTab = ref("application");
const selectedAttachmentId = ref("");
const previewText = ref("");
const previewLoading = ref(false);
const previewError = ref("");
const objectUrls = new Map<string, string>();
let submitKey = createIdempotencyKey("workflow:start");

const orderedAttachments = computed(() => [...(context.value?.attachmentTemplates || [])]
  .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0)));
const startable = computed(() => context.value?.startable ?? context.value?.canStart ?? false);
const attachmentRows = computed<LocalAttachmentRow[]>(() => {
  let index = 0;
  const rows: LocalAttachmentRow[] = [];
  for (const rule of orderedAttachments.value) {
    const files = attachments.value[rule.attachmentCode] || [];
    files.forEach((file, fileIndex) => {
      index += 1;
      rows.push({
        id: attachmentRowId(rule, file, fileIndex),
        index,
        rule,
        file,
        extension: fileExtension(file.name),
      });
    });
  }
  return rows;
});
const attachmentTemplateRows = computed<AttachmentTemplateRow[]>(() =>
  orderedAttachments.value.map((rule, index) => ({
    index: index + 1,
    rule,
    files: attachmentRows.value.filter((row) => row.rule.attachmentCode === rule.attachmentCode),
  })),
);
const selectedAttachment = computed(() =>
  attachmentRows.value.find((row) => row.id === selectedAttachmentId.value) ?? null,
);
const selectedAttachmentUrl = computed(() =>
  selectedAttachment.value ? objectUrlFor(selectedAttachment.value) : "",
);
const selectedAttachmentPreviewType = computed<"image" | "pdf" | "text" | "unsupported" | "empty">(() => {
  const row = selectedAttachment.value;
  if (!row) return "empty";
  if (isImageFile(row)) return "image";
  if (isPdfFile(row)) return "pdf";
  if (isTextFile(row)) return "text";
  return "unsupported";
});

onMounted(loadContext);
onBeforeUnmount(revokeAllObjectUrls);
watch(() => props.processCode, loadContext);
watch(attachmentRows, (rows) => {
  const rowIds = new Set(rows.map((row) => row.id));
  for (const [id, url] of objectUrls.entries()) {
    if (!rowIds.has(id)) {
      URL.revokeObjectURL(url);
      objectUrls.delete(id);
    }
  }
  if (!rows.length) {
    selectedAttachmentId.value = "";
    return;
  }
  if (!rowIds.has(selectedAttachmentId.value)) {
    selectedAttachmentId.value = rows[0].id;
  }
});
watch(selectedAttachment, () => {
  void loadPreviewText();
});

async function loadContext(): Promise<void> {
  loading.value = true;
  error.value = "";
  success.value = "";
  try {
    const loaded = await fetchWorkflowStartContext(props.processCode);
    context.value = {
      ...loaded,
      formFields: loaded.formFields ?? [],
      fieldPermissions: loaded.fieldPermissions ?? [],
      attachmentTemplates: loaded.attachmentTemplates?.length
        ? loaded.attachmentTemplates
        : loaded.attachments ?? loaded.attachmentTemplates ?? [],
      defaultVariables: loaded.defaultVariables ?? {},
    };
    variables.value = { ...context.value.defaultVariables };
    attachments.value = {};
    selectedAttachmentId.value = "";
    revokeAllObjectUrls();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "发起上下文加载失败";
  } finally {
    loading.value = false;
  }
}

function selectFiles(rule: WorkflowStartAttachmentRule, fileList: UploadFile[]): void {
  const files: File[] = [];
  for (const file of fileList) if (file.raw) files.push(file.raw as File);
  attachments.value = { ...attachments.value, [rule.attachmentCode]: files };
  error.value = "";
}

function filesFor(rule: WorkflowStartAttachmentRule): UploadFile[] {
  return (attachments.value[rule.attachmentCode] || []).map((file, index) => ({ name: file.name, uid: index + 1, status: "ready", size: file.size, raw: file as UploadFile["raw"] }));
}

function removeFile(rule: WorkflowStartAttachmentRule, file: UploadFile): void {
  const files = attachments.value[rule.attachmentCode] || [];
  const index = files.findIndex((item) => item.name === file.name && item.size === file.size && item.lastModified === file.raw?.lastModified);
  if (index >= 0) attachments.value = { ...attachments.value, [rule.attachmentCode]: files.filter((_, fileIndex) => fileIndex !== index) };
}

function selectAttachment(row: LocalAttachmentRow): void {
  selectedAttachmentId.value = row.id;
}

function selectTemplateAttachment(row: AttachmentTemplateRow): void {
  if (row.files[0]) selectAttachment(row.files[0]);
}

function isTemplateRowSelected(row: AttachmentTemplateRow): boolean {
  return row.files.some((file) => file.id === selectedAttachmentId.value);
}

function attachmentRowId(rule: WorkflowStartAttachmentRule, file: File, fileIndex: number): string {
  return [
    rule.attachmentCode,
    fileIndex,
    file.name,
    file.size,
    file.lastModified,
  ].join(":");
}

function fileExtension(fileName: string): string {
  return fileName.includes(".") ? fileName.split(".").pop()!.toLowerCase() : "";
}

function displayExtension(row: LocalAttachmentRow): string {
  return row.extension ? row.extension.toUpperCase() : "--";
}

function displayAttachmentFormats(rule: WorkflowStartAttachmentRule): string {
  const formats = [...new Set((rule.allowedExtensions ?? [])
    .map((value) => value.replace(/^\./, "").trim().toUpperCase())
    .filter(Boolean))];
  return formats.length ? formats.join("、") : "不限制";
}

function attachmentMaxSizeBytes(rule: WorkflowStartAttachmentRule): number | undefined {
  return rule.maxSizeBytes
    ?? (rule.maxSizeMb == null ? undefined : rule.maxSizeMb * 1024 * 1024);
}

function displayAttachmentSize(rule: WorkflowStartAttachmentRule): string {
  const value = attachmentMaxSizeBytes(rule);
  return value == null ? "不限制" : formatFileSize(value);
}

function objectUrlFor(row: LocalAttachmentRow): string {
  const existing = objectUrls.get(row.id);
  if (existing) return existing;
  const url = URL.createObjectURL(row.file);
  objectUrls.set(row.id, url);
  return url;
}

function revokeAllObjectUrls(): void {
  for (const url of objectUrls.values()) {
    URL.revokeObjectURL(url);
  }
  objectUrls.clear();
}

function isImageFile(row: LocalAttachmentRow): boolean {
  return row.file.type.startsWith("image/")
    || ["jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"].includes(row.extension);
}

function isPdfFile(row: LocalAttachmentRow): boolean {
  return row.file.type === "application/pdf" || row.extension === "pdf";
}

function isTextFile(row: LocalAttachmentRow): boolean {
  return row.file.type.startsWith("text/")
    || ["txt", "csv", "json", "xml", "log", "md"].includes(row.extension);
}

async function loadPreviewText(): Promise<void> {
  const row = selectedAttachment.value;
  previewText.value = "";
  previewError.value = "";
  if (!row || !isTextFile(row)) {
    previewLoading.value = false;
    return;
  }
  previewLoading.value = true;
  try {
    previewText.value = await readFileText(row.file);
  } catch (reason) {
    previewError.value = reason instanceof Error ? reason.message : "文件内容读取失败";
  } finally {
    previewLoading.value = false;
  }
}

function readFileText(file: File): Promise<string> {
  if (typeof file.text === "function") {
    return file.text();
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ""));
    reader.onerror = () => reject(reader.error ?? new Error("文件内容读取失败"));
    reader.readAsText(file);
  });
}

function validateAttachments(): boolean {
  for (const rule of orderedAttachments.value) {
    const files = attachments.value[rule.attachmentCode] || [];
    if (files.length < rule.minCount || files.length > rule.maxCount) {
      error.value = `${rule.attachmentName}需上传 ${rule.minCount}-${rule.maxCount} 个文件`;
      return false;
    }
    const extensions = new Set(rule.allowedExtensions.map((value) => value.toLowerCase().replace(/^\./, "")));
    for (const file of files) {
      const extension = file.name.includes(".") ? file.name.split(".").pop()!.toLowerCase() : "";
      if (extensions.size && !extensions.has(extension)) {
        error.value = `${rule.attachmentName}不支持文件 ${file.name}`;
        return false;
      }
      const maxSizeBytes = rule.maxSizeBytes ?? (rule.maxSizeMb == null ? Number.POSITIVE_INFINITY : rule.maxSizeMb * 1024 * 1024);
      if (file.size > maxSizeBytes) {
        error.value = `${rule.attachmentName}中的 ${file.name} 超过大小限制`;
        return false;
      }
    }
  }
  return true;
}

async function submit(): Promise<void> {
  if (!context.value || submitting.value || !startable.value) return;
  error.value = "";
  success.value = "";
  submitting.value = true;
  try {
    if (!await formRef.value?.validate?.()) {
      error.value = "请修正表单字段后再提交";
      return;
    }
    if (!validateAttachments()) {
      activeTab.value = "attachments";
      return;
    }
    const result = await startWorkflowProcess(props.processCode, {
      definitionId: context.value.definitionId,
      definitionVersion: context.value.definitionVersion,
      variables: { ...variables.value },
      attachments: attachments.value,
      idempotencyKey: submitKey,
    });
    success.value = `流程已发起，实例编号：${result.instanceId}`;
    submitKey = createIdempotencyKey("workflow:start");
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "流程发起失败";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <section class="workflow-start-shell" aria-labelledby="start-heading">
    <p v-if="loading" class="state-line" role="status">加载中...</p>
    <p v-else-if="!context" class="state-line is-error" role="alert">{{ error || "暂无可发起流程" }}</p>
    <template v-else>
      <header class="block-title"><h1 id="start-heading">{{ context.pageTitle || context.processName || "办理详情" }}</h1></header>
      <p v-if="!startable" class="state-line is-error" role="alert">{{ context.disabledReason || context.unavailableReason || "当前不可发起" }}</p>
      <div class="content-panel">
        <el-tabs v-model="activeTab" class="workflow-tabs">
          <el-tab-pane name="application" label="申请详情">
            <component :is="businessFormComponent" ref="formRef" v-model="variables" :fields="context.formFields" :field-permissions="context.fieldPermissions" mode="edit" :disabled="submitting || !startable" />
          </el-tab-pane>
          <el-tab-pane name="attachments" label="影像资料上传">
            <section class="attachment-section" aria-labelledby="start-attachments-heading">
              <h2 id="start-attachments-heading">影像资料上传</h2>
              <div v-if="orderedAttachments.length" class="attachment-workbench" data-test="attachment-workbench">
                <aside class="archive-list-panel" aria-labelledby="archive-list-heading">
                  <div class="panel-title-row">
                    <h3 id="archive-list-heading">档案列表</h3>
                  </div>
                  <div class="archive-table-scroll">
                    <table class="archive-table">
                      <thead>
                        <tr>
                          <th>序号</th>
                          <th>档案（附件）名称</th>
                          <th>档案（附件）格式</th>
                          <th>大小限制</th>
                          <th>是否必填</th>
                          <th>下载</th>
                          <th>是否上传</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr
                          v-for="row in attachmentTemplateRows"
                          :key="row.rule.attachmentCode"
                          :class="{ selected: isTemplateRowSelected(row) }"
                          @click="selectTemplateAttachment(row)"
                        >
                          <td class="number-cell">{{ row.index }}</td>
                          <td>{{ row.rule.attachmentName }}</td>
                          <td>{{ displayAttachmentFormats(row.rule) }}</td>
                          <td>{{ displayAttachmentSize(row.rule) }}</td>
                          <td class="number-cell">{{ row.rule.required ? "是" : "否" }}</td>
                          <td class="download-cell">
                            <div v-if="row.files.length" class="download-items">
                              <a
                                v-for="file in row.files"
                                :key="file.id"
                                class="download-link"
                                :href="objectUrlFor(file)"
                                :download="file.file.name"
                                @click.stop
                              >
                                {{ file.file.name }}
                              </a>
                            </div>
                            <span v-else class="muted-cell">暂无下载</span>
                          </td>
                          <td class="number-cell">{{ row.files.length ? "已上传" : "未上传" }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </aside>

                <div class="archive-upload-panel">
                  <div class="panel-title-row">
                    <h3>档案上传</h3>
                  </div>
                  <div class="attachment-list">
                    <div v-for="rule in orderedAttachments" :key="rule.attachmentCode" class="attachment-card">
                      <div class="attachment-card-heading">
                        <span>{{ rule.attachmentName }}<b v-if="rule.required"> *</b></span>
                        <small>{{ rule.description || `${rule.minCount}-${rule.maxCount} 个文件` }}</small>
                      </div>
                      <el-upload
                        drag
                        :auto-upload="false"
                        :show-file-list="false"
                        :multiple="rule.maxCount > 1"
                        :accept="rule.allowedExtensions.map((item) => `.${item.replace(/^\./, '')}`).join(',')"
                        :disabled="submitting || !startable"
                        :file-list="filesFor(rule)"
                        @change="(_file: UploadFile, fileList: UploadFile[]) => selectFiles(rule, fileList)"
                      >
                        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                        <div class="el-upload__text">将文件拖到此处，或 <em>点击上传</em></div>
                      </el-upload>
                      <table v-if="filesFor(rule).length" class="file-table">
                        <thead>
                          <tr>
                            <th>文件名</th>
                            <th>文件大小</th>
                            <th>操作</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="file in filesFor(rule)" :key="`${file.name}:${file.size}:${file.raw?.lastModified}`">
                            <td class="file-name-cell">{{ file.name }}</td>
                            <td>{{ formatFileSize(file.size) }}</td>
                            <td><el-button link type="danger" @click="removeFile(rule, file)">删除</el-button></td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </div>

                  <section class="preview-panel" aria-labelledby="archive-preview-heading">
                    <div class="panel-title-row">
                      <h3 id="archive-preview-heading">档案内容</h3>
                      <span v-if="selectedAttachment" class="preview-meta">
                        {{ selectedAttachment.file.name }} · {{ formatFileSize(selectedAttachment.file.size) }}
                      </span>
                    </div>
                    <div v-if="!selectedAttachment" class="preview-empty">
                      请选择或上传附件后查看内容
                    </div>
                    <img
                      v-else-if="selectedAttachmentPreviewType === 'image'"
                      class="preview-image"
                      :src="selectedAttachmentUrl"
                      :alt="selectedAttachment.file.name"
                    />
                    <iframe
                      v-else-if="selectedAttachmentPreviewType === 'pdf'"
                      class="preview-frame"
                      :src="selectedAttachmentUrl"
                      :title="selectedAttachment.file.name"
                    />
                    <div v-else-if="selectedAttachmentPreviewType === 'text'" class="preview-text-wrap">
                      <p v-if="previewLoading" class="preview-empty">正在读取文件内容...</p>
                      <p v-else-if="previewError" class="preview-empty is-error-text">{{ previewError }}</p>
                      <pre v-else class="preview-text">{{ previewText }}</pre>
                    </div>
                    <div v-else class="preview-unsupported">
                      <strong>{{ selectedAttachment.file.name }}</strong>
                      <span>文件格式：{{ displayExtension(selectedAttachment) }}</span>
                      <span>文件大小：{{ formatFileSize(selectedAttachment.file.size) }}</span>
                      <span>当前格式不支持在线预览，请下载后查看。</span>
                      <a
                        class="download-link"
                        :href="selectedAttachmentUrl"
                        :download="selectedAttachment.file.name"
                      >
                        下载
                      </a>
                    </div>
                  </section>
                </div>
              </div>
              <el-empty v-else description="暂无影像资料上传要求" />
            </section>
          </el-tab-pane>
        </el-tabs>
      </div>
      <p v-if="error" class="state-line is-error" role="alert">{{ error }}</p>
      <p v-if="success" class="state-line is-success" role="status">{{ success }}</p>
      <div class="form-actions"><el-button data-test="submit-button" type="primary" :loading="submitting" :disabled="!startable" @click="submit">{{ submitting ? "提交中..." : "提交" }}</el-button></div>
    </template>
  </section>
</template>

<style scoped>
.workflow-start-shell { display: grid; gap: 12px; color: #18324d; }
.block-title { background: #0879c9; color: #fff; padding: 6px 16px; }
.block-title h1 { margin: 0; font-size: 16px; font-weight: 700; }
.content-panel { border: 1px solid #9aaabd; background: #fff; }
.workflow-tabs { min-height: 380px; }
.workflow-tabs :deep(.el-tabs__header) { margin: 0; padding: 0 18px; }
.workflow-tabs :deep(.el-tabs__item) { color: #18324d; font-weight: 600; }
.workflow-tabs :deep(.el-tabs__item.is-active) { color: #0879c9; }
.workflow-tabs :deep(.el-tabs__active-bar) { background: #0879c9; }
.workflow-tabs :deep(.el-tabs__content) { padding: 22px 20px; }
.attachment-section { display: grid; gap: 16px; }
.attachment-section h2 { margin: 0; border-left: 5px solid #0879c9; padding-left: 12px; font-size: 16px; }
.attachment-workbench { display: grid; grid-template-columns: minmax(360px, 0.9fr) minmax(0, 1.5fr); gap: 14px; align-items: start; min-height: 560px; }
.archive-list-panel,
.archive-upload-panel { min-width: 0; }
.archive-list-panel { border-right: 1px solid #d8dee8; padding-right: 14px; }
.panel-title-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 28px; margin-bottom: 10px; }
.panel-title-row h3 { margin: 0; border-left: 4px solid #0879c9; padding-left: 8px; color: #18324d; font-size: 15px; font-weight: 700; }
.archive-table-scroll { overflow: auto; border: 1px solid #d8dee8; }
.archive-table { width: 100%; min-width: 760px; border-collapse: collapse; font-size: 13px; }
.archive-table th,
.archive-table td { border: 1px solid #d8dee8; padding: 8px 10px; text-align: left; vertical-align: middle; }
.archive-table th { background: #e7f2fb; color: #18324d; font-weight: 700; text-align: center; white-space: nowrap; }
.archive-table tbody tr { cursor: pointer; }
.archive-table tbody tr:hover td,
.archive-table tbody tr.selected td { background: #d7ebfb; }
.archive-table .number-cell { text-align: center; white-space: nowrap; }
.archive-table .file-name-cell { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.archive-table .empty-cell { height: 86px; color: #8a96a6; text-align: center; cursor: default; }
.download-cell { min-width: 150px; }
.download-items { display: flex; flex-wrap: wrap; gap: 6px 10px; }
.muted-cell { color: #8a96a6; }
.download-link { color: #0879c9; font-weight: 700; text-decoration: none; }
.download-link:hover { text-decoration: underline; }
.attachment-list { display: grid; gap: 18px; }
.attachment-card { display: grid; gap: 10px; }
.attachment-card-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.attachment-card-heading span { font-weight: 650; }
.attachment-card-heading small { color: #718096; }
.attachment-card b { color: #d93025; }
.attachment-card :deep(.el-upload-dragger) { width: 100%; box-sizing: border-box; min-height: 130px; padding: 24px; border-color: #8ebbe0; background: #f8fcff; }
.attachment-card :deep(.el-icon--upload) { color: #0879c9; font-size: 38px; margin-bottom: 6px; }
.attachment-card :deep(.el-upload__text em) { color: #0879c9; font-style: normal; }
.file-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.file-table th,
.file-table td { border: 1px solid #d8dee8; padding: 7px 9px; text-align: left; }
.file-table th { background: #f8fafc; color: #18324d; font-weight: 700; }
.file-table td:nth-child(2),
.file-table td:nth-child(3) { text-align: center; white-space: nowrap; }
.preview-panel { display: grid; gap: 10px; margin-top: 18px; border: 1px dashed #9aaabd; padding: 12px; min-height: 300px; background: #fff; }
.preview-meta { min-width: 0; overflow: hidden; color: #718096; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.preview-empty { display: grid; min-height: 220px; place-items: center; margin: 0; color: #8a96a6; }
.preview-image { display: block; max-width: 100%; max-height: 520px; margin: 0 auto; object-fit: contain; }
.preview-frame { width: 100%; min-height: 520px; border: 0; background: #f8fafc; }
.preview-text-wrap { min-height: 260px; overflow: auto; background: #f8fafc; }
.preview-text { margin: 0; padding: 12px; color: #18324d; font: 13px/1.6 Consolas, "Courier New", monospace; white-space: pre-wrap; overflow-wrap: anywhere; }
.preview-unsupported { display: grid; align-content: center; justify-items: center; gap: 8px; min-height: 240px; color: #5d6978; text-align: center; }
.preview-unsupported strong { max-width: 100%; overflow-wrap: anywhere; color: #18324d; }
.is-error-text { color: #be123c; }
.form-actions { display: flex; justify-content: flex-end; border: 1px solid #9aaabd; border-top: 0; padding: 12px 16px; background: #fff; }
.state-line { margin: 0; border: 1px solid #d8dee8; border-radius: 6px; padding: 10px 12px; }
.is-error { border-color: #fecdd3; background: #fff1f2; color: #be123c; }
.is-success { border-color: #bbf7d0; background: #f0fdf4; color: #15803d; }
@media (max-width: 980px) {
  .attachment-workbench { grid-template-columns: 1fr; min-height: 0; }
  .archive-list-panel { border-right: 0; border-bottom: 1px solid #d8dee8; padding: 0 0 14px; }
}
@media (max-width: 640px) {
  .attachment-card-heading,
  .panel-title-row { align-items: flex-start; flex-direction: column; }
  .workflow-tabs :deep(.el-tabs__content) { padding: 16px 12px; }
  .preview-frame { min-height: 360px; }
}
</style>
