<script setup lang="ts">
import { computed, markRaw, onMounted, ref, shallowRef, toRaw, watch, type Component } from "vue";
import type { UploadFile } from "element-plus";
import { UploadFilled } from "@element-plus/icons-vue";
import { fetchWorkflowStartContext, startWorkflowProcess } from "../../api/workflow";
import type { WorkflowStartAttachmentRule, WorkflowStartContext } from "../../types/workflow";
import { createIdempotencyKey } from "../../utils/idempotency";

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
let submitKey = createIdempotencyKey("workflow:start");

const orderedAttachments = computed(() => [...(context.value?.attachmentTemplates || [])]
  .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0)));
const startable = computed(() => context.value?.startable ?? context.value?.canStart ?? false);

onMounted(loadContext);
watch(() => props.processCode, loadContext);

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
              <div v-if="orderedAttachments.length" class="attachment-list">
                <div v-for="rule in orderedAttachments" :key="rule.attachmentCode" class="attachment-card">
                  <div class="attachment-card-heading"><span>{{ rule.attachmentName }}<b v-if="rule.required"> *</b></span><small>{{ rule.description || `${rule.minCount}-${rule.maxCount} 个文件` }}</small></div>
                  <el-upload drag :auto-upload="false" :show-file-list="false" :multiple="rule.maxCount > 1" :accept="rule.allowedExtensions.map((item) => `.${item.replace(/^\./, '')}`).join(',')" :disabled="submitting || !startable" :file-list="filesFor(rule)" @change="(_file: UploadFile, fileList: UploadFile[]) => selectFiles(rule, fileList)">
                    <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                    <div class="el-upload__text">将文件拖到此处，或 <em>点击上传</em></div>
                  </el-upload>
                  <el-table v-if="filesFor(rule).length" :data="filesFor(rule)" class="file-table" size="small">
                    <el-table-column prop="name" label="文件名" min-width="240" />
                    <el-table-column label="文件大小" width="140"><template #default="scope">{{ `${Math.max(1, Math.ceil((scope.row.size || 0) / 1024))} KB` }}</template></el-table-column>
                    <el-table-column label="操作" width="100" align="center"><template #default="scope"><el-button link type="danger" @click="removeFile(rule, scope.row)">删除</el-button></template></el-table-column>
                  </el-table>
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
.attachment-list { display: grid; gap: 18px; }
.attachment-card { display: grid; gap: 10px; }
.attachment-card-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.attachment-card-heading span { font-weight: 650; }
.attachment-card-heading small { color: #718096; }
.attachment-card b { color: #d93025; }
.attachment-card :deep(.el-upload-dragger) { width: 100%; box-sizing: border-box; min-height: 130px; padding: 24px; border-color: #8ebbe0; background: #f8fcff; }
.attachment-card :deep(.el-icon--upload) { color: #0879c9; font-size: 38px; margin-bottom: 6px; }
.attachment-card :deep(.el-upload__text em) { color: #0879c9; font-style: normal; }
.file-table { width: 100%; }
.form-actions { display: flex; justify-content: flex-end; border: 1px solid #9aaabd; border-top: 0; padding: 12px 16px; background: #fff; }
.state-line { margin: 0; border: 1px solid #d8dee8; border-radius: 6px; padding: 10px 12px; }
.is-error { border-color: #fecdd3; background: #fff1f2; color: #be123c; }
.is-success { border-color: #bbf7d0; background: #f0fdf4; color: #15803d; }
@media (max-width: 640px) { .attachment-card-heading { align-items: flex-start; flex-direction: column; } .workflow-tabs :deep(.el-tabs__content) { padding: 16px 12px; } }
</style>
