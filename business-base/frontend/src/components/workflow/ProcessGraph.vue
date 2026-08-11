<script setup lang="ts">
import { computed } from "vue";
import type { WorkflowEdgeView, WorkflowNodeView } from "../../types/workflow";

const props = defineProps<{
  nodes: WorkflowNodeView[];
  edges: WorkflowEdgeView[];
  currentNodeCodes: string[];
}>();

const nodeWidth = 132;
const nodeHeight = 44;
const graphNodes = computed(() =>
  [...props.nodes]
    .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
    .map((node, index) => ({
      ...node,
      x: node.positionX ?? 30 + index * 180,
      y: node.positionY ?? 36,
    })),
);
const nodeByCode = computed(() => new Map(graphNodes.value.map((node) => [node.nodeCode, node])));
const graphEdges = computed(() => props.edges.flatMap((edge) => {
  const source = nodeByCode.value.get(edge.sourceNodeCode);
  const target = nodeByCode.value.get(edge.targetNodeCode);
  return source && target ? [{ ...edge, source, target }] : [];
}));
const viewWidth = computed(() => Math.max(480, ...graphNodes.value.map((node) => node.x + nodeWidth + 30)));
const viewHeight = computed(() => Math.max(140, ...graphNodes.value.map((node) => node.y + nodeHeight + 36)));

function isCurrent(node: WorkflowNodeView): boolean {
  return props.currentNodeCodes.includes(node.nodeCode);
}
</script>

<template>
  <section class="workflow-section" aria-labelledby="graph-heading">
    <div class="section-header">
      <h2 id="graph-heading">流程图</h2>
    </div>
    <div v-if="graphNodes.length" class="graph-canvas">
      <svg :viewBox="`0 0 ${viewWidth} ${viewHeight}`" role="img" aria-label="流程节点关系图">
        <defs>
          <marker id="workflow-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
            <path d="M0,0 L8,4 L0,8 Z" fill="#94a3b8" />
          </marker>
        </defs>
        <line
          v-for="edge in graphEdges"
          :key="edge.edgeCode ?? `${edge.sourceNodeCode}-${edge.targetNodeCode}`"
          :x1="edge.source.x + nodeWidth"
          :y1="edge.source.y + nodeHeight / 2"
          :x2="edge.target.x"
          :y2="edge.target.y + nodeHeight / 2"
          class="graph-edge"
          marker-end="url(#workflow-arrow)"
        />
        <g v-for="node in graphNodes" :key="node.nodeCode" :class="{ current: isCurrent(node) }">
          <rect :x="node.x" :y="node.y" :width="nodeWidth" :height="nodeHeight" rx="6" />
          <text :x="node.x + nodeWidth / 2" :y="node.y + nodeHeight / 2 + 5">{{ node.nodeName }}</text>
        </g>
      </svg>
    </div>
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

.graph-canvas {
  overflow: auto;
  min-height: 140px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
}

.graph-canvas svg {
  display: block;
  width: 100%;
  min-width: 480px;
  height: auto;
}

.graph-edge {
  stroke: #94a3b8;
  stroke-width: 2;
}

g rect {
  fill: #fff;
  stroke: #cbd5e1;
  stroke-width: 1.5;
}

g text {
  fill: #334155;
  font-size: 13px;
  text-anchor: middle;
}

g.current rect {
  fill: #eff6ff;
  stroke: #2563eb;
  stroke-width: 2;
}

g.current text {
  fill: #1d4ed8;
  font-weight: 700;
}

.empty-state {
  margin: 0;
  color: #6b7280;
}
</style>
