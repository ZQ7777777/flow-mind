<script setup lang="ts">
import { computed } from "vue";
import type { ProcessPreview } from "@flowmind/agent-contracts";

const props = defineProps<{ preview: ProcessPreview }>();
const nodes = computed(() => props.preview.nodes.map((node) => ({
  ...node,
  nodeCode: String(node.nodeCode),
  nodeName: String(node.nodeName),
  nodeType: String(node.nodeType),
  x: Number(node.positionX || 80),
  y: Number(node.positionY || 120),
})));
const byCode = computed(() => new Map(nodes.value.map((node) => [node.nodeCode, node])));
const edges = computed(() => props.preview.edges.map((edge) => ({
  edgeCode: String(edge.edgeCode),
  conditionExpression: edge.conditionExpression ? String(edge.conditionExpression) : "",
  source: byCode.value.get(String(edge.sourceNodeCode)),
  target: byCode.value.get(String(edge.targetNodeCode)),
})).filter((edge) => edge.source && edge.target));
const width = computed(() => Math.max(960, ...nodes.value.map((node) => node.x + 150)));
const height = computed(() => Math.max(280, ...nodes.value.map((node) => node.y + 120)));

function nodeClass(type: string): string {
  if (type === "START") return "start";
  if (type === "END") return "end";
  if (type.includes("GATEWAY")) return "gateway";
  return "task";
}
</script>

<template>
  <div class="process-preview">
    <div class="preview-heading">
      <div>
        <span class="eyebrow">平台定义 ID</span>
        <strong>{{ preview.platformDefinitionId }}</strong>
      </div>
      <div class="status-tags">
        <el-tag>{{ preview.definitionStatus || "DRAFT" }}</el-tag>
        <el-tag :type="preview.activationStatus === 'ACTIVE' ? 'success' : 'info'">
          {{ preview.activationStatus || "INACTIVE" }}
        </el-tag>
      </div>
    </div>

    <div class="graph-scroll">
      <svg class="process-graph" :viewBox="`0 0 ${width} ${height}`" role="img" aria-label="流程节点与连线预览">
        <defs>
          <marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto">
            <path d="M0,0 L0,6 L9,3 z" />
          </marker>
        </defs>
        <g v-for="edge in edges" :key="String(edge.edgeCode)">
          <line
            :x1="edge.source!.x + 58"
            :y1="edge.source!.y + 30"
            :x2="edge.target!.x - 8"
            :y2="edge.target!.y + 30"
            marker-end="url(#arrow)"
          />
          <text
            v-if="edge.conditionExpression"
            :x="(edge.source!.x + edge.target!.x) / 2"
            :y="edge.source!.y + 18"
          >{{ edge.conditionExpression }}</text>
        </g>
        <g v-for="node in nodes" :key="node.nodeCode" :class="['graph-node', nodeClass(node.nodeType)]">
          <rect :x="node.x" :y="node.y" width="120" height="60" rx="14" />
          <text :x="node.x + 60" :y="node.y + 27">{{ node.nodeName }}</text>
          <text class="node-type" :x="node.x + 60" :y="node.y + 45">{{ node.nodeType }}</text>
        </g>
      </svg>
    </div>

    <el-alert
      v-if="!preview.validation.valid"
      type="error"
      title="发布前校验未通过，门禁二已禁用"
      :closable="false"
      show-icon
    />
    <div v-if="preview.validation.issues.length" class="issue-list">
      <div v-for="issue in preview.validation.issues" :key="`${issue.code}-${issue.nodeCode}-${issue.edgeCode}`" class="issue">
        <el-tag size="small" type="danger">{{ issue.code }}</el-tag>
        <span>{{ issue.message }}</span>
        <small v-if="issue.nodeCode">节点：{{ issue.nodeCode }}</small>
        <small v-if="issue.edgeCode">连线：{{ issue.edgeCode }}</small>
      </div>
    </div>

    <div class="preview-table">
      <h3>表单字段</h3>
      <el-table :data="preview.formFields" size="small">
        <el-table-column prop="fieldCode" label="编码" />
        <el-table-column prop="fieldName" label="名称" />
        <el-table-column prop="fieldType" label="类型" />
        <el-table-column prop="controlType" label="控件" />
        <el-table-column prop="required" label="必填">
          <template #default="{ row }">{{ row.required ? "是" : "否" }}</template>
        </el-table-column>
      </el-table>
    </div>
    <div class="preview-table">
      <h3>附件模板</h3>
      <el-table :data="preview.attachmentTemplates" size="small">
        <el-table-column prop="attachmentCode" label="编码" />
        <el-table-column prop="attachmentName" label="名称" />
        <el-table-column prop="templateVersion" label="版本" />
        <el-table-column prop="allowedExtensions" label="格式" />
        <el-table-column prop="required" label="必填">
          <template #default="{ row }">{{ row.required ? "是" : "否" }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>
