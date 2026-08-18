<script setup lang="ts">
import { computed, reactive, watch } from "vue";
import type { WorkflowFormField } from "../../types/workflow";
import { formatDateTime, formatUnknown } from "../../utils/format";

const props = defineProps<{
  fields: WorkflowFormField[];
  variables: Record<string, unknown>;
  editable?: boolean;
}>();

const emit = defineEmits<{
  "update:variables": [variables: Record<string, unknown>];
}>();

const draft = reactive<Record<string, unknown>>({});
const errors = reactive<Record<string, string>>({});

watch(
  () => props.variables,
  (variables) => {
    for (const key of Object.keys(draft)) delete draft[key];
    Object.assign(draft, variables);
  },
  { immediate: true, deep: true },
);

const sortedFields = computed(() =>
  [...props.fields]
    .filter((field) => field.visible !== false)
    .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0)),
);

function canEdit(field: WorkflowFormField): boolean {
  return Boolean(props.editable) && field.editable !== false;
}

function displayValue(field: WorkflowFormField): string {
  const value = props.variables[field.fieldCode];
  const optionLabel = findOptionLabel(field.validationRule, value);
  if (optionLabel) {
    return optionLabel;
  }
  if (field.fieldType?.toLowerCase().includes("date") || field.controlType?.toLowerCase().includes("date")) {
    return typeof value === "string" ? formatDateTime(value) : formatUnknown(value);
  }
  return formatUnknown(value);
}

function findOptionLabel(rule: string | undefined, value: unknown): string | undefined {
  if (!rule) return undefined;
  try {
    const parsed = JSON.parse(rule) as unknown;
    const options = Array.isArray(parsed)
      ? parsed
      : typeof parsed === "object" && parsed !== null && "options" in parsed
        ? (parsed as { options?: unknown }).options
        : undefined;
    if (!Array.isArray(options)) return undefined;
    const option = options.find((item) =>
      typeof item === "object" && item !== null && "value" in item
        ? String((item as { value: unknown }).value) === String(value)
        : false,
    );
    return typeof option === "object" && option !== null && "label" in option
      ? String((option as { label: unknown }).label)
      : undefined;
  } catch {
    return undefined;
  }
}

function options(field: WorkflowFormField): Array<{ label: string; value: string }> {
  if (!field.validationRule) return [];
  try {
    const parsed = JSON.parse(field.validationRule) as { options?: unknown } | unknown[];
    const source = Array.isArray(parsed) ? parsed : parsed.options;
    if (!Array.isArray(source)) return [];
    return source.flatMap((item) =>
      typeof item === "object" && item !== null && "value" in item
        ? [{
            label: "label" in item ? String(item.label) : String(item.value),
            value: String(item.value),
          }]
        : [],
    );
  } catch {
    return [];
  }
}

function inputType(field: WorkflowFormField): string {
  const control = field.controlType?.toLowerCase();
  if (control === "number" || field.fieldType?.toLowerCase() === "number") return "number";
  if (control === "datepicker" || field.fieldType?.toLowerCase() === "date") return "date";
  return "text";
}

function update(field: WorkflowFormField, event: Event): void {
  const element = event.target as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
  let value: unknown = element.value;
  if (element instanceof HTMLInputElement && element.type === "checkbox") value = element.checked;
  if (inputType(field) === "number") value = element.value === "" ? null : Number(element.value);
  if (field.fieldType?.toLowerCase() === "boolean" && element instanceof HTMLSelectElement) {
    value = element.value === "" ? null : element.value === "true";
  }
  draft[field.fieldCode] = value;
  delete errors[field.fieldCode];
  emit("update:variables", { ...draft });
}

function validate(): boolean {
  for (const key of Object.keys(errors)) delete errors[key];
  for (const field of sortedFields.value) {
    if (!canEdit(field)) continue;
    const value = draft[field.fieldCode];
    if (field.required && (value == null || typeof value === "string" && !value.trim())) {
      errors[field.fieldCode] = `${field.fieldName}不能为空`;
      continue;
    }
    if (value == null || value === "") continue;
    const rule = parseRule(field.validationRule);
    if (typeof value === "number") {
      if (typeof rule.minimum === "number" && value < rule.minimum) errors[field.fieldCode] = `${field.fieldName}不能小于${rule.minimum}`;
      if (typeof rule.maximum === "number" && value > rule.maximum) errors[field.fieldCode] = `${field.fieldName}不能大于${rule.maximum}`;
    }
    if (typeof value === "string") {
      if (typeof rule.minLength === "number" && value.length < rule.minLength) errors[field.fieldCode] = `${field.fieldName}长度不足`;
      if (typeof rule.maxLength === "number" && value.length > rule.maxLength) errors[field.fieldCode] = `${field.fieldName}长度超限`;
      if (typeof rule.pattern === "string") {
        try {
          if (!new RegExp(rule.pattern).test(value)) errors[field.fieldCode] = `${field.fieldName}格式不正确`;
        } catch { errors[field.fieldCode] = `${field.fieldName}校验规则无效`; }
      }
    }
    const allowed = options(field);
    if (allowed.length && !allowed.some((item) => item.value === String(value))) {
      errors[field.fieldCode] = `${field.fieldName}选项无效`;
    }
  }
  return Object.keys(errors).length === 0;
}

function parseRule(rule: string | undefined): Record<string, unknown> {
  if (!rule) return {};
  try {
    const value = JSON.parse(rule) as unknown;
    return typeof value === "object" && value !== null && !Array.isArray(value)
      ? value as Record<string, unknown> : {};
  } catch { return {}; }
}

defineExpose({ validate });
</script>

<template>
  <section class="workflow-section" aria-labelledby="variables-heading">
    <div class="section-header">
      <h2 id="variables-heading">业务信息</h2>
    </div>
    <div v-if="sortedFields.length" class="variable-grid" :class="{ 'editable-grid': editable }">
      <div
        v-for="field in sortedFields"
        :key="field.fieldCode"
        class="variable-item"
        :class="{ 'editable-item': canEdit(field) }"
      >
        <template v-if="canEdit(field)">
          <label class="editable-label">
            <span>{{ field.fieldName }}<b v-if="field.required" aria-hidden="true"> *</b></span>
            <textarea
              v-if="field.controlType?.toLowerCase() === 'textarea'"
              :value="String(draft[field.fieldCode] ?? '')"
              rows="3"
              @input="update(field, $event)"
            />
            <select
              v-else-if="field.controlType?.toLowerCase() === 'select'"
              :value="String(draft[field.fieldCode] ?? '')"
              @change="update(field, $event)"
            >
              <option value="">请选择</option>
              <option v-for="item in options(field)" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
            <input
              v-else-if="field.controlType?.toLowerCase() === 'checkbox'"
              type="checkbox"
              :checked="Boolean(draft[field.fieldCode])"
              @change="update(field, $event)"
            />
            <input
              v-else
              :type="inputType(field)"
              :value="String(draft[field.fieldCode] ?? '')"
              @input="update(field, $event)"
            />
          </label>
          <small v-if="errors[field.fieldCode]" class="field-error" role="alert">
            {{ errors[field.fieldCode] }}
          </small>
        </template>
        <template v-else>
          <dt>{{ field.fieldName }}<b v-if="field.required" aria-hidden="true"> *</b></dt>
          <dd>{{ displayValue(field) }}</dd>
        </template>
      </div>
    </div>
    <p v-else class="empty-state">暂无业务字段</p>
  </section>
</template>

<style scoped>
.workflow-section {
  padding: 20px 0;
  border-top: 1px solid #e5e7eb;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

h2 {
  margin: 0;
  color: #111827;
  font-size: 18px;
  font-weight: 650;
}

.variable-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  margin: 0;
}

.variable-item {
  min-width: 0;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}

dt {
  margin-bottom: 6px;
  color: #6b7280;
  font-size: 13px;
}

dd {
  margin: 0;
  overflow-wrap: anywhere;
  color: #111827;
  font-size: 14px;
}

.editable-item {
  display: grid;
  gap: 7px;
  color: #374151;
  font-size: 13px;
}

.editable-label {
  display: grid;
  gap: 7px;
}

.editable-item input:not([type="checkbox"]),
.editable-item textarea,
.editable-item select {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 8px 9px;
  color: #111827;
  font: inherit;
}

.editable-item b,
.field-error,
dt b {
  color: #b91c1c;
}

.empty-state {
  margin: 0;
  color: #6b7280;
}
</style>
