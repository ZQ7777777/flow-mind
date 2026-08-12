<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useSvgViewport } from "../../composables/useSvgViewport";
import type { WorkflowEdgeView, WorkflowNodeView } from "../../types/workflow";

const props = defineProps<{
  nodes: WorkflowNodeView[];
  edges: WorkflowEdgeView[];
  currentNodeCodes: string[];
}>();

const nodeWidth = 132;
const nodeHeight = 44;
const viewportWidth = ref(720);
const viewportHeight = ref(260);
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
const graphBounds = computed(() => ({ x: 0, y: 0, width: viewWidth.value, height: viewHeight.value }));
const viewport = useSvgViewport(viewportWidth, viewportHeight);

function isCurrent(node: WorkflowNodeView): boolean {
  return props.currentNodeCodes.includes(node.nodeCode);
}

function fitView(): void { viewport.fitView(graphBounds.value); }
onMounted(() => nextTick(fitView));
watch(() => [props.nodes, props.edges], () => nextTick(fitView), { deep: false });
</script>

<template>
  <section class="workflow-section" aria-labelledby="graph-heading">
    <div class="section-header">
      <h2 id="graph-heading">流程图</h2>
      <div class="graph-toolbar" aria-label="流程图视图控制">
        <button type="button" aria-label="缩小" @click="viewport.zoomOut">−</button>
        <span data-testid="graph-zoom">{{ viewport.zoomPercent.value }}</span>
        <button type="button" aria-label="放大" @click="viewport.zoomIn">＋</button>
        <button type="button" @click="fitView">适应画布</button>
        <button type="button" @click="viewport.resetView">重置</button>
      </div>
    </div>
    <div v-if="graphNodes.length" class="graph-canvas">
      <svg :ref="viewport.svgRef" :viewBox="`0 0 ${viewportWidth} ${viewportHeight}`" role="img" aria-label="流程节点关系图"
           @pointerdown="viewport.beginPan" @pointermove="viewport.movePan" @pointerup="viewport.endPan"
           @pointercancel="viewport.endPan" @wheel.prevent="viewport.wheelZoom">
        <defs>
          <marker id="workflow-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
            <path d="M0,0 L8,4 L0,8 Z" fill="#94a3b8" />
          </marker>
        </defs>
        <g :transform="viewport.transform.value">
          <line
            v-for="edge in graphEdges"
            :key="edge.edgeCode ?? `${edge.sourceNodeCode}-${edge.targetNodeCode}`"
            :x1="edge.source.x + nodeWidth"
            :y1="edge.source.y + nodeHeight / 2"
            :x2="edge.target.x"
            :y2="edge.target.y + nodeHeight / 2"
            class="graph-edge"
            marker-end="url(#workflow-arrow)"
            data-graph-interactive
          />
          <g v-for="node in graphNodes" :key="node.nodeCode" :class="{ current: isCurrent(node) }" data-graph-interactive>
            <rect :x="node.x" :y="node.y" :width="nodeWidth" :height="nodeHeight" rx="6" />
            <text :x="node.x + nodeWidth / 2" :y="node.y + nodeHeight / 2 + 5">{{ node.nodeName }}</text>
          </g>
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.graph-toolbar { display: flex; align-items: center; gap: 6px; }
.graph-toolbar button { border: 1px solid #cbd5e1; border-radius: 5px; padding: 5px 9px; background: #fff; cursor: pointer; }
.graph-toolbar span { min-width: 46px; color: #64748b; font-size: 12px; text-align: center; }

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
  height: 260px;
  cursor: grab;
  touch-action: none;
}
.graph-canvas svg:active { cursor: grabbing; }

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
