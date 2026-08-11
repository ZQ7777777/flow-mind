<script setup lang="ts">
import { computed } from "vue";
import type { WorkflowFormField } from "../../types/workflow";
import { formatDateTime, formatUnknown } from "../../utils/format";

const props = defineProps<{
  fields: WorkflowFormField[];
  variables: Record<string, unknown>;
}>();

const sortedFields = computed(() =>
  [...props.fields].sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0)),
);

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
</script>

<template>
  <section class="workflow-section" aria-labelledby="variables-heading">
    <div class="section-header">
      <h2 id="variables-heading">业务信息</h2>
    </div>
    <dl v-if="sortedFields.length" class="variable-grid">
      <div v-for="field in sortedFields" :key="field.fieldCode" class="variable-item">
        <dt>{{ field.fieldName }}</dt>
        <dd>{{ displayValue(field) }}</dd>
      </div>
    </dl>
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

.empty-state {
  margin: 0;
  color: #6b7280;
}
</style>
