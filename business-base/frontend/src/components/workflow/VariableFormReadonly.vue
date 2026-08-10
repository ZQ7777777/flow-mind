<script setup lang="ts">
import { computed } from "vue";
import type { WorkflowFormField } from "../../types/workflow";
import { formatUnknown } from "../../utils/format";

const props = defineProps<{
  fields: WorkflowFormField[];
  variables: Record<string, unknown>;
}>();

const sortedFields = computed(() =>
  [...props.fields].sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0)),
);

function displayValue(field: WorkflowFormField): string {
  const value = props.variables[field.fieldCode];
  if (field.fieldType === "date" || field.controlType === "datePicker") {
    return typeof value === "string" ? value : formatUnknown(value);
  }
  return formatUnknown(value);
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
