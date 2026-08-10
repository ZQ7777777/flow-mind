<script setup lang="ts">
import { computed } from "vue";
import type { WorkflowGraph, WorkflowGraphNode } from "../../types/workflow";

const props = defineProps<{
  graph?: WorkflowGraph;
}>();

const nodes = computed<WorkflowGraphNode[]>(() =>
  [...(props.graph?.nodes ?? [])].sort(
    (left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0),
  ),
);

function isCurrent(node: WorkflowGraphNode): boolean {
  return props.graph?.currentNodeCodes?.includes(node.nodeCode) ?? false;
}
</script>

<template>
  <section class="workflow-section" aria-labelledby="graph-heading">
    <div class="section-header">
      <h2 id="graph-heading">流程图</h2>
    </div>
    <ol v-if="nodes.length" class="graph-strip">
      <li
        v-for="node in nodes"
        :key="node.nodeCode"
        class="graph-node"
        :class="{ current: isCurrent(node) }"
      >
        <span>{{ node.nodeName }}</span>
      </li>
    </ol>
    <p v-else class="empty-state">暂无流程图数据</p>
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

.graph-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.graph-node {
  position: relative;
  min-width: 120px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 10px 12px;
  background: #fff;
  color: #374151;
  text-align: center;
}

.graph-node.current {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 650;
}

.empty-state {
  margin: 0;
  color: #6b7280;
}
</style>
