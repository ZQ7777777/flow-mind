<script setup lang="ts">
import { computed, markRaw, onMounted, ref, shallowRef, toRaw, watch, type Component } from "vue";
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

function selectFiles(rule: WorkflowStartAttachmentRule, event: Event): void {
  const input = event.target as HTMLInputElement;
  attachments.value = { ...attachments.value, [rule.attachmentCode]: Array.from(input.files || []) };
  error.value = "";
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
    if (!validateAttachments()) return;
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
  <section class="page-surface workflow-start-shell" aria-labelledby="start-heading">
    <p v-if="loading" class="state-line" role="status">加载中...</p>
    <p v-else-if="!context" class="state-line is-error" role="alert">{{ error || "暂无可发起流程" }}</p>
    <template v-else>
      <header class="section-heading">
        <p class="eyebrow">Workflow Start</p>
        <h1 id="start-heading">{{ context.pageTitle || context.processName }}</h1>
      </header>
      <p v-if="!startable" class="state-line is-error" role="alert">{{ context.disabledReason || context.unavailableReason || "当前不可发起" }}</p>
      <component
        :is="businessFormComponent"
        ref="formRef"
        v-model="variables"
        :fields="context.formFields"
        :field-permissions="context.fieldPermissions"
        mode="edit"
        :disabled="submitting || !startable"
      />
      <section v-if="orderedAttachments.length" class="attachment-section" aria-labelledby="start-attachments-heading">
        <h2 id="start-attachments-heading">发起附件</h2>
        <label v-for="rule in orderedAttachments" :key="rule.attachmentCode" class="attachment-field">
          <span>{{ rule.attachmentName }}<b v-if="rule.required"> *</b></span>
          <small>{{ rule.description || `${rule.minCount}-${rule.maxCount} 个文件` }}</small>
          <input type="file" :multiple="rule.maxCount > 1" :accept="rule.allowedExtensions.map((item) => `.${item.replace(/^\./, '')}`).join(',')" :disabled="submitting" @change="selectFiles(rule, $event)" />
        </label>
      </section>
      <p v-if="error" class="state-line is-error" role="alert">{{ error }}</p>
      <p v-if="success" class="state-line is-success" role="status">{{ success }}</p>
      <div class="form-actions">
        <button type="button" :disabled="submitting || !startable" @click="submit">
          {{ submitting ? "提交中..." : "提交申请" }}
        </button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.workflow-start-shell { display: grid; gap: 18px; }
.section-heading p, .section-heading h1 { margin: 0; }
.eyebrow { color: #0f766e; font-size: 12px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
h1 { color: #17202a; font-size: 24px; }
.attachment-section { display: grid; gap: 12px; border-top: 1px solid #d8dee8; padding-top: 16px; }
.attachment-section h2 { margin: 0; font-size: 18px; }
.attachment-field { display: grid; gap: 6px; color: #17202a; }
.attachment-field small { color: #5d6978; }
.attachment-field b { color: #be123c; }
.form-actions { display: flex; justify-content: flex-end; border-top: 1px solid #d8dee8; padding-top: 16px; }
.form-actions button { min-height: 36px; border: 0; border-radius: 6px; padding: 0 18px; background: #2563eb; color: #fff; cursor: pointer; }
.form-actions button:disabled { cursor: not-allowed; opacity: .6; }
.state-line { margin: 0; border: 1px solid #d8dee8; border-radius: 6px; padding: 10px 12px; }
.is-error { border-color: #fecdd3; background: #fff1f2; color: #be123c; }
.is-success { border-color: #bbf7d0; background: #f0fdf4; color: #15803d; }
</style>
