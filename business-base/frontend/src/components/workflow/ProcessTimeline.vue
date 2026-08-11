<script setup lang="ts">
import type { WorkflowHistoryTaskResponse } from "../../types/workflow";
import { formatDateTime } from "../../utils/format";

defineProps<{
  items: WorkflowHistoryTaskResponse[];
}>();
</script>

<template>
  <section class="workflow-section" aria-labelledby="timeline-heading">
    <div class="section-header">
      <h2 id="timeline-heading">流程轨迹</h2>
    </div>
    <ol v-if="items.length" class="timeline">
      <li v-for="item in items" :key="item.historyTaskId">
        <span class="marker" aria-hidden="true" />
        <div>
          <strong>{{ item.nodeName ?? "--" }}</strong>
          <span>{{ item.actionType ?? item.handleType ?? "--" }}</span>
          <span>{{ item.assigneeUserName ?? "--" }}</span>
          <time>{{ formatDateTime(item.completedAt ?? item.startedAt) }}</time>
        </div>
        <p v-if="item.comment">{{ item.comment }}</p>
      </li>
    </ol>
    <p v-else class="empty-state">暂无流程轨迹</p>
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

h2 {
  margin: 0;
  color: #111827;
  font-size: 18px;
  font-weight: 650;
}

.timeline {
  display: grid;
  gap: 12px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.timeline li {
  position: relative;
  padding-left: 22px;
}

.marker {
  position: absolute;
  top: 4px;
  left: 0;
  width: 10px;
  height: 10px;
  border: 2px solid #2563eb;
  border-radius: 999px;
  background: #fff;
}

.timeline div {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #6b7280;
  font-size: 13px;
}

.timeline strong {
  color: #111827;
}

.timeline p {
  margin: 6px 0 0;
  color: #374151;
}

.empty-state {
  margin: 0;
  color: #6b7280;
}
</style>
