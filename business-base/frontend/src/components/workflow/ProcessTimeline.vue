<script setup lang="ts">
import { computed } from "vue";
import type {
  WorkflowCommentView,
  WorkflowHistoryTaskResponse,
  WorkflowNodeView,
} from "../../types/workflow";
import { formatDateTime } from "../../utils/format";
import { taskActionLabel, workflowEmptyText, workflowNodeLabel } from "../../utils/workflowDisplay";

const props = defineProps<{
  items: WorkflowHistoryTaskResponse[];
  nodes?: WorkflowNodeView[];
  currentNodeCodes?: string[];
  comments?: WorkflowCommentView[];
  updatedAt?: string;
}>();

type TimelineStatus = "completed" | "active";

interface TimelineRow {
  key: string;
  nodeCode?: string;
  nodeName: string;
  operatorName: string;
  handledAt?: string;
  comment: string;
  status: TimelineStatus;
  statusLabel: string;
}

const currentCodes = computed(() => new Set(props.currentNodeCodes ?? []));
const nodeNameByCode = computed(() => new Map(
  (props.nodes ?? []).map((node) => [node.nodeCode, node.nodeName]),
));
const startNodeCodes = computed(() => new Set(
  (props.nodes ?? [])
    .filter((node) => node.nodeType?.toUpperCase() === "START")
    .map((node) => node.nodeCode),
));
const commentByNode = computed(() => {
  const result = new Map<string, WorkflowCommentView>();
  for (const comment of props.comments ?? []) {
    if (!comment.nodeCode) continue;
    result.set(comment.nodeCode, comment);
  }
  return result;
});
const reachedCodes = computed(() => new Set(
  props.items
    .filter((item) => !isStartHistory(item))
    .flatMap((item) => item.nodeCode ? [item.nodeCode] : []),
));
const timelineRows = computed<TimelineRow[]>(() => {
  const historyRows = props.items
    .filter((item) => !isStartHistory(item))
    .sort((left, right) => timestamp(left) - timestamp(right))
    .map((item) => rowFromHistory(item));
  const activeRows = (props.nodes ?? [])
    .filter((node) => !isStartNode(node))
    .filter((node) => currentCodes.value.has(node.nodeCode))
    .filter((node) => !reachedCodes.value.has(node.nodeCode))
    .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
    .map((node) => rowFromActiveNode(node));
  return [...historyRows, ...activeRows];
});
const latestStatusAt = computed(() => {
  if (props.updatedAt) return formatDateTime(props.updatedAt);
  const times = [
    ...props.items.map((item) => item.completedAt ?? item.startedAt),
    ...(props.comments ?? []).map((comment) => comment.createdAt),
  ].filter(Boolean) as string[];
  return times.length ? formatDateTime(times.sort().at(-1)) : "--";
});

function timestamp(item: WorkflowHistoryTaskResponse): number {
  return new Date(item.completedAt ?? item.startedAt ?? "").getTime() || 0;
}

function isStartNode(node: WorkflowNodeView): boolean {
  return node.nodeType?.toUpperCase() === "START";
}

function isStartHistory(item: WorkflowHistoryTaskResponse): boolean {
  const nodeCode = item.nodeCode ?? "";
  if (startNodeCodes.value.has(nodeCode)) return true;
  return nodeCode.toLowerCase() === "start";
}

function rowFromHistory(item: WorkflowHistoryTaskResponse): TimelineRow {
  const comment = item.nodeCode ? commentByNode.value.get(item.nodeCode) : undefined;
  return {
    key: item.historyTaskId,
    nodeCode: item.nodeCode,
    nodeName: displayNodeName(item.nodeCode, item.nodeName),
    operatorName: item.assigneeUserName ?? item.delegateFromUserName ?? comment?.operatorUserName ?? "--",
    handledAt: item.completedAt ?? item.startedAt,
    comment: mergedComment(item.comment, comment?.comment),
    status: "completed",
    statusLabel: taskActionLabel(item.actionType ?? item.handleType, "已完成"),
  };
}

function rowFromActiveNode(node: WorkflowNodeView): TimelineRow {
  const comment = commentByNode.value.get(node.nodeCode);
  return {
    key: "active:" + node.nodeCode,
    nodeCode: node.nodeCode,
    nodeName: workflowNodeLabel(node.nodeCode, node.nodeName),
    operatorName: comment?.operatorUserName ?? "--",
    handledAt: comment?.createdAt,
    comment: comment?.comment || "-",
    status: "active",
    statusLabel: "\u5ba1\u6279\u4e2d",
  };
}

function displayNodeName(nodeCode: string | undefined, fallbackName: string | undefined): string {
  if (nodeCode && nodeNameByCode.value.has(nodeCode)) {
    return nodeNameByCode.value.get(nodeCode) ?? workflowEmptyText;
  }
  return workflowNodeLabel(nodeCode, fallbackName);
}

function mergedComment(historyComment: string | undefined, approvalComment: string | undefined): string {
  const comments = [historyComment, approvalComment]
    .map((comment) => comment?.trim())
    .filter((comment): comment is string => Boolean(comment) && comment !== "-");
  return [...new Set(comments)].join("；") || "-";
}


</script>

<template>
  <section class="workflow-section timeline-panel" aria-labelledby="timeline-heading">
    <div class="section-header">
      <h2 id="timeline-heading">&#27969;&#31243;&#36712;&#36857;</h2>
    </div>
    <ol v-if="timelineRows.length" class="timeline">
      <li
        v-for="row in timelineRows"
        :key="row.key"
        class="timeline-item"
        :class="'is-' + row.status"
      >
        <span class="marker" aria-hidden="true" />
        <div class="timeline-content">
          <div class="node-row">
            <strong>{{ row.nodeName }}</strong>
            <span class="status-chip" :class="'is-' + row.status">{{ row.statusLabel }}</span>
          </div>
          <div v-if="row.operatorName !== '--' || row.handledAt" class="meta-row">
            <span v-if="row.operatorName !== '--'" class="operator">{{ row.operatorName }}</span>
            <time v-if="row.handledAt">{{ formatDateTime(row.handledAt) }}</time>
          </div>
          <p class="comment-box">&#23457;&#25209;&#24847;&#35265;&#65306;{{ row.comment }}</p>
        </div>
      </li>
    </ol>
    <p v-else class="empty-state">&#26242;&#26080;&#27969;&#31243;&#36712;&#36857;</p>
    <footer class="timeline-footer">
      <span>&#27969;&#31243;&#21160;&#24577;&#23454;&#26102;&#26356;&#26032;&#65292;&#26368;&#26032;&#29366;&#24577;&#65306;{{ latestStatusAt }}</span>
    </footer>
  </section>
</template>

<style scoped>
.workflow-section {
  padding: 20px 0;
  border-top: 1px solid #e5e7eb;
}

.section-header {
  margin-bottom: 18px;
}

h2 {
  margin: 0;
  color: #17202a;
  font-size: 20px;
  font-weight: 750;
}

.timeline {
  display: grid;
  gap: 0;
  padding: 0;
  margin: 0;
  list-style: none;
}

.timeline-item {
  position: relative;
  min-height: 88px;
  padding: 0 0 22px 34px;
}

.timeline-item::before {
  content: "";
  position: absolute;
  top: 18px;
  bottom: -2px;
  left: 8px;
  width: 2px;
  background: #d8dee8;
}

.timeline-item:last-child::before {
  display: none;
}

.marker {
  position: absolute;
  top: 2px;
  left: 0;
  width: 18px;
  height: 18px;
  border: 3px solid #b9c2cf;
  border-radius: 999px;
  background: #fff;
}

.timeline-item.is-completed .marker {
  border-color: #10b981;
  background: #10b981;
}

.timeline-item.is-completed .marker::after {
  content: "";
  position: absolute;
  left: 4px;
  top: 2px;
  width: 5px;
  height: 9px;
  border: solid #fff;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

.timeline-item.is-active .marker {
  border-color: #bfdbfe;
  background: #2563eb;
  box-shadow: inset 0 0 0 4px #fff;
}

.timeline-content {
  display: grid;
  gap: 10px;
}

.node-row,
.meta-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.node-row {
  justify-content: space-between;
}

.node-row strong {
  overflow-wrap: anywhere;
  color: #17202a;
  font-size: 16px;
  line-height: 1.4;
}

.timeline-item.is-active .node-row strong {
  color: #2563eb;
}

.meta-row {
  color: #5d6978;
  font-size: 13px;
}

.operator::before {
  content: "";
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 999px;
  background: #5d6978;
  vertical-align: 1px;
}

.comment-box {
  margin: 0;
  border: 1px solid #d8dee8;
  border-radius: 6px;
  padding: 10px 12px;
  background: #f8fafc;
  color: #344054;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  border: 1px solid #d8dee8;
  border-radius: 6px;
  padding: 2px 9px;
  background: #f8fafc;
  color: #5d6978;
  font-size: 12px;
  font-weight: 800;
}

.status-chip.is-completed {
  border-color: #bbf7d0;
  background: #ecfdf5;
  color: #15803d;
}

.status-chip.is-active {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.empty-state {
  margin: 0;
  color: #5d6978;
}

.timeline-footer {
  margin-top: 8px;
  border-top: 1px solid #d8dee8;
  padding-top: 12px;
  color: #5d6978;
  font-size: 13px;
}
</style>
