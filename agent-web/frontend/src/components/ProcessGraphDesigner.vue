<script setup lang="ts">
import { computed, ref } from "vue";
import { createDefaultUserTaskConfigs } from "@flowmind/agent-contracts";

interface GraphNode {
  nodeCode?: string;
  nodeName?: string;
  nodeType?: string;
  pairedGatewayCode?: string;
  approverRule?: { type?: string; config?: Record<string, unknown> };
  approverRuleType?: string;
  approverRuleConfig?: string | Record<string, unknown> | null;
  multiInstanceMode?: string;
  listenerConfig?: string | Record<string, unknown> | null;
  timeoutConfig?: string | Record<string, unknown> | null;
  reminderConfig?: string | Record<string, unknown> | null;
  positionX?: number;
  positionY?: number;
  sortOrder?: number;
}

interface GraphEdge {
  edgeCode?: string;
  sourceNodeCode?: string;
  targetNodeCode?: string;
  conditionExpression?: string;
  defaultEdge?: boolean;
  sortOrder?: number;
}

interface DisplayNode extends GraphNode {
  nodeCode: string;
  nodeName: string;
  nodeType: string;
  x: number;
  y: number;
}

interface DisplayEdge extends GraphEdge {
  edgeCode: string;
  sourceNodeCode: string;
  targetNodeCode: string;
  conditionExpression: string;
  source: DisplayNode;
  target: DisplayNode;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  labelX: number;
  labelY: number;
}

interface GraphViewBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface TimeoutPolicy {
  enabled: boolean;
  durationMinutes: number;
  action: string;
  severity: string;
  targetNodeCode: string;
}

interface ReminderPolicy {
  enabled: boolean;
  maxCount: number;
  messageTemplate: string;
}

interface ListenerPolicy {
  rejectEnabled: boolean;
  rejectTargetNodeCodes: string[];
  directSendEnabled: boolean;
}

interface ApproverConfigEditor {
  userIdsText: string;
  departmentId: string;
  roleCode: string;
  departmentFrom: string;
  expression: string;
}

const NODE_WIDTH = 132;
const NODE_HEIGHT = 60;
const NODE_RADIUS = 8;
const NODE_STEP_X = 180;
const ZOOM_MIN = 0.5;
const ZOOM_MAX = 2;
const ZOOM_STEP = 0.1;
const PAN_VISIBLE_MARGIN = 40;

const NODE_TYPE_OPTIONS = [
  { value: "START", label: "开始节点" },
  { value: "USER_TASK", label: "用户任务" },
  { value: "EXCLUSIVE_GATEWAY", label: "排他网关" },
  { value: "PARALLEL_SPLIT_GATEWAY", label: "并行分支" },
  { value: "PARALLEL_JOIN_GATEWAY", label: "并行汇聚" },
  { value: "END", label: "结束节点" },
];
const APPROVER_OPTIONS = [
  { value: "USER", label: "指定用户" },
  { value: "STARTER", label: "发起人" },
  { value: "DEPARTMENT", label: "指定部门" },
  { value: "ROLE", label: "指定角色" },
  { value: "ROLE_IN_DEPARTMENT", label: "部门角色" },
  { value: "APPROVER_EXPRESSION", label: "表达式" },
];
const MULTI_INSTANCE_OPTIONS = [
  { value: "SINGLE", label: "单人" },
  { value: "OR_SIGN", label: "或签" },
  { value: "COUNTERSIGN", label: "会签" },
];
const TIMEOUT_ACTION_OPTIONS = [
  { value: "REMIND", label: "提醒" },
  { value: "ALERT", label: "告警" },
  { value: "JUMP", label: "跳转" },
  { value: "TERMINATE", label: "终止" },
  { value: "FORCE_COMPLETE", label: "强制完成" },
];
const SEVERITY_OPTIONS = [
  { value: "LOW", label: "低" },
  { value: "MEDIUM", label: "中" },
  { value: "HIGH", label: "高" },
];

const props = withDefaults(defineProps<{
  nodes: GraphNode[];
  edges: GraphEdge[];
  editable?: boolean;
  title?: string;
}>(), {
  editable: false,
  title: "流程图",
});

const emit = defineEmits<{
  "update:nodes": [nodes: GraphNode[]];
  "update:edges": [edges: GraphEdge[]];
}>();

const selected = ref<{ type: "node" | "edge"; code: string }>();
const drag = ref<{
  code: string;
  startClientX: number;
  startClientY: number;
  startX: number;
  startY: number;
  viewBox: GraphViewBox;
  pixelScale: number;
}>();
const canvasDrag = ref<{
  startClientX: number;
  startClientY: number;
  startOffsetX: number;
  startOffsetY: number;
  pixelScale: number;
}>();
const connectionMode = ref(false);
const connectionClickQueue = ref<string[]>([]);
const zoom = ref(1);
const viewportOffset = ref({ x: 0, y: 0 });
const markerId = `graph-arrow-${Math.random().toString(36).slice(2)}`;

const nodes = computed<DisplayNode[]>(() => props.nodes.map((node, index) => ({
  ...node,
  nodeCode: String(node.nodeCode || `node-${index + 1}`),
  nodeName: String(node.nodeName || node.nodeCode || "未命名节点"),
  nodeType: String(node.nodeType || "USER_TASK"),
  x: Number(node.positionX ?? 80 + index * NODE_STEP_X),
  y: Number(node.positionY ?? 120),
})));
const nodeByCode = computed(() => new Map(nodes.value.map((node) => [node.nodeCode, node])));
const edges = computed<DisplayEdge[]>(() => props.edges
  .map((edge, index) => {
    const sourceNodeCode = String(edge.sourceNodeCode || "");
    const targetNodeCode = String(edge.targetNodeCode || "");
    const source = nodeByCode.value.get(sourceNodeCode);
    const target = nodeByCode.value.get(targetNodeCode);
    if (!source || !target) return undefined;
    const points = connectionPoints(source, target);
    return {
      ...edge,
      edgeCode: String(edge.edgeCode || `edge-${index + 1}`),
      sourceNodeCode,
      targetNodeCode,
      conditionExpression: edge.conditionExpression ? String(edge.conditionExpression) : "",
      source,
      target,
      ...points,
      labelX: (points.x1 + points.x2) / 2,
      labelY: (points.y1 + points.y2) / 2 - 8,
    };
  })
  .filter((edge): edge is DisplayEdge => Boolean(edge)));
const userTaskNodes = computed(() => nodes.value.filter((node) => node.nodeType === "USER_TASK"));
const pendingConnectionSource = computed(() => connectionMode.value && connectionClickQueue.value.length % 2 === 1
  ? connectionClickQueue.value[connectionClickQueue.value.length - 1]
  : "");

const width = computed(() => Math.max(980, ...nodes.value.map((node) => node.x + NODE_WIDTH + 80)));
const height = computed(() => Math.max(380, ...nodes.value.map((node) => node.y + NODE_HEIGHT + 80)));
const graphBounds = computed(() => {
  if (!nodes.value.length) return undefined;
  return {
    minX: Math.min(...nodes.value.map((node) => node.x)),
    maxX: Math.max(...nodes.value.map((node) => node.x + NODE_WIDTH)),
    minY: Math.min(...nodes.value.map((node) => node.y)),
    maxY: Math.max(...nodes.value.map((node) => node.y + NODE_HEIGHT)),
  };
});
const centeredViewBox = computed<GraphViewBox>(() => {
  const bounds = graphBounds.value;
  if (!bounds) {
    return { x: 0, y: 0, width: width.value, height: height.value };
  }
  return {
    x: (bounds.minX + bounds.maxX - width.value) / 2,
    y: (bounds.minY + bounds.maxY - height.value) / 2,
    width: width.value,
    height: height.value,
  };
});
const baseViewBox = computed(() => drag.value?.viewBox || centeredViewBox.value);
const constrainedViewportOffset = computed(() => constrainViewportOffset(viewportOffset.value, baseViewBox.value, zoom.value));
const activeViewBox = computed<GraphViewBox>(() => {
  const current = baseViewBox.value;
  const zoomedWidth = current.width / zoom.value;
  const zoomedHeight = current.height / zoom.value;
  return {
    x: current.x + (current.width - zoomedWidth) / 2 + constrainedViewportOffset.value.x,
    y: current.y + (current.height - zoomedHeight) / 2 + constrainedViewportOffset.value.y,
    width: zoomedWidth,
    height: zoomedHeight,
  };
});
const viewBox = computed(() => {
  const current = activeViewBox.value;
  return `${current.x} ${current.y} ${current.width} ${current.height}`;
});
const zoomPercent = computed(() => `${Math.round(zoom.value * 100)}%`);
const viewportIsDefault = computed(() => zoom.value === 1
  && Math.abs(constrainedViewportOffset.value.x) < 0.001
  && Math.abs(constrainedViewportOffset.value.y) < 0.001);
const selectedNode = computed(() => selected.value?.type === "node"
  ? nodes.value.find((node) => node.nodeCode === selected.value?.code)
  : undefined);
const selectedEdge = computed(() => selected.value?.type === "edge"
  ? edges.value.find((edge) => edge.edgeCode === selected.value?.code)
  : undefined);
const selectedNodeIsUserTask = computed(() => selectedNode.value?.nodeType === "USER_TASK");
const selectedApproverType = computed(() => selectedNode.value?.approverRule?.type || selectedNode.value?.approverRuleType || "ROLE");
const approverConfig = computed(() => readApproverConfig(selectedNode.value));
const timeoutPolicy = computed(() => readTimeoutPolicy(selectedNode.value));
const reminderPolicy = computed(() => readReminderPolicy(selectedNode.value));
const listenerPolicy = computed(() => readListenerPolicy(selectedNode.value));

function nodeClass(type: string): string {
  if (type === "START") return "start";
  if (type === "END") return "end";
  if (type.includes("GATEWAY")) return "gateway";
  return "task";
}

function nodeTypeLabel(type: string): string {
  return NODE_TYPE_OPTIONS.find((option) => option.value === type)?.label || type;
}

function setZoom(value: number): void {
  if (drag.value || canvasDrag.value) return;
  zoom.value = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, Math.round(value * 10) / 10));
  viewportOffset.value = constrainViewportOffset(viewportOffset.value, baseViewBox.value, zoom.value);
}

function zoomIn(): void {
  setZoom(zoom.value + ZOOM_STEP);
}

function zoomOut(): void {
  setZoom(zoom.value - ZOOM_STEP);
}

function resetZoom(): void {
  if (drag.value || canvasDrag.value) return;
  zoom.value = 1;
  viewportOffset.value = { x: 0, y: 0 };
}

function handleWheel(event: WheelEvent): void {
  if ((!event.ctrlKey && !event.metaKey) || drag.value || canvasDrag.value) return;
  event.preventDefault();
  if (event.deltaY < 0) zoomIn();
  if (event.deltaY > 0) zoomOut();
}

function viewBoxPixelScale(current: GraphViewBox, rect: DOMRect): number {
  if (rect.width <= 0 || rect.height <= 0) return 1;
  return Math.min(rect.width / current.width, rect.height / current.height);
}

function constrainViewportOffset(offset: { x: number; y: number }, current: GraphViewBox, currentZoom: number): { x: number; y: number } {
  const bounds = graphBounds.value;
  if (!bounds) return { x: 0, y: 0 };
  const visibleWidth = current.width / currentZoom;
  const visibleHeight = current.height / currentZoom;
  const defaultX = current.x + (current.width - visibleWidth) / 2;
  const defaultY = current.y + (current.height - visibleHeight) / 2;
  const viewX = Math.min(
    bounds.maxX - PAN_VISIBLE_MARGIN,
    Math.max(bounds.minX - visibleWidth + PAN_VISIBLE_MARGIN, defaultX + offset.x),
  );
  const viewY = Math.min(
    bounds.maxY - PAN_VISIBLE_MARGIN,
    Math.max(bounds.minY - visibleHeight + PAN_VISIBLE_MARGIN, defaultY + offset.y),
  );
  return { x: viewX - defaultX, y: viewY - defaultY };
}

function selectNode(node: DisplayNode): void {
  selected.value = { type: "node", code: node.nodeCode };
}

function selectEdge(edge: DisplayEdge): void {
  selected.value = { type: "edge", code: edge.edgeCode };
}

function startConnectionMode(): void {
  if (!props.editable) return;
  connectionMode.value = true;
  connectionClickQueue.value = [];
}

function stopConnectionMode(): void {
  connectionMode.value = false;
  connectionClickQueue.value = [];
}

function handleCanvasNodeClick(node: DisplayNode): void {
  if (!connectionMode.value) {
    selectNode(node);
    return;
  }
  connectionClickQueue.value.push(node.nodeCode);
  selectNode(node);
  if (connectionClickQueue.value.length % 2 !== 0) return;
  const sourceNodeCode = connectionClickQueue.value[connectionClickQueue.value.length - 2];
  const targetNodeCode = connectionClickQueue.value[connectionClickQueue.value.length - 1];
  if (sourceNodeCode === targetNodeCode) return;
  const edgeCode = uniqueCode(
    `edge_${sourceNodeCode}_${targetNodeCode}_${props.edges.length}`,
    props.edges.map((edge) => edge.edgeCode),
  );
  const nextEdge: GraphEdge = {
    edgeCode,
    sourceNodeCode,
    targetNodeCode,
    conditionExpression: "",
    defaultEdge: false,
    sortOrder: props.edges.length + 1,
  };
  emitEdges([...props.edges.map(cloneValue), nextEdge]);
  selected.value = { type: "edge", code: edgeCode };
}

function startDrag(node: DisplayNode, event: MouseEvent): void {
  if (connectionMode.value) return;
  selectNode(node);
  if (!props.editable || event.button !== 0) return;
  event.preventDefault();
  const svg = (event.currentTarget as SVGGElement).ownerSVGElement;
  const lockedViewBox = { ...centeredViewBox.value };
  drag.value = {
    code: node.nodeCode,
    startClientX: event.clientX,
    startClientY: event.clientY,
    startX: node.x,
    startY: node.y,
    viewBox: lockedViewBox,
    pixelScale: svg ? viewBoxPixelScale(activeViewBox.value, svg.getBoundingClientRect()) : 1,
  };
}

function startCanvasDrag(event: MouseEvent): void {
  if (event.button !== 0 || drag.value || canvasDrag.value || !graphBounds.value) return;
  event.preventDefault();
  const currentOffset = constrainedViewportOffset.value;
  canvasDrag.value = {
    startClientX: event.clientX,
    startClientY: event.clientY,
    startOffsetX: currentOffset.x,
    startOffsetY: currentOffset.y,
    pixelScale: viewBoxPixelScale(activeViewBox.value, (event.currentTarget as SVGSVGElement).getBoundingClientRect()),
  };
}

function moveDrag(event: MouseEvent): void {
  if (drag.value) {
    const nextX = Math.max(20, Math.round(drag.value.startX + (event.clientX - drag.value.startClientX) / drag.value.pixelScale));
    const nextY = Math.max(20, Math.round(drag.value.startY + (event.clientY - drag.value.startClientY) / drag.value.pixelScale));
    emitNodes(props.nodes.map((node) => String(node.nodeCode) === drag.value?.code
      ? { ...cloneValue(node), positionX: nextX, positionY: nextY }
      : cloneValue(node)));
    return;
  }
  if (!canvasDrag.value) return;
  viewportOffset.value = constrainViewportOffset({
    x: canvasDrag.value.startOffsetX - (event.clientX - canvasDrag.value.startClientX) / canvasDrag.value.pixelScale,
    y: canvasDrag.value.startOffsetY - (event.clientY - canvasDrag.value.startClientY) / canvasDrag.value.pixelScale,
  }, baseViewBox.value, zoom.value);
}

function stopDrag(): void {
  drag.value = undefined;
  canvasDrag.value = undefined;
}

function addNode(): void {
  if (!props.editable) return;
  const code = uniqueCode("node", props.nodes.map((node) => node.nodeCode));
  const nextUserTaskNodeCodes = [...userTaskNodes.value.map((node) => node.nodeCode), code];
  const runtimeConfigs = createRuntimeDefaults(code, nextUserTaskNodeCodes);
  const maxX = nodes.value.reduce((value, node) => Math.max(value, node.x), 80);
  const nextNode: GraphNode = {
    nodeCode: code,
    nodeName: `节点${props.nodes.length + 1}`,
    nodeType: "USER_TASK",
    approverRule: { type: "ROLE", config: {} },
    multiInstanceMode: "SINGLE",
    ...runtimeConfigs,
    positionX: maxX + NODE_STEP_X,
    positionY: 120,
    sortOrder: props.nodes.length + 1,
  };
  emitNodes([...props.nodes.map(cloneValue), nextNode]);
  selected.value = { type: "node", code };
}

function deleteSelected(): void {
  if (!props.editable || !selected.value) return;
  if (selected.value.type === "node") {
    const removedCode = selected.value.code;
    emitNodes(props.nodes
      .filter((node) => String(node.nodeCode) !== removedCode)
      .map((node) => removeNodeReferences(cloneValue(node), removedCode)));
    emitEdges(props.edges
      .filter((edge) => String(edge.sourceNodeCode) !== removedCode && String(edge.targetNodeCode) !== removedCode)
      .map(cloneValue));
  } else {
    const removedCode = selected.value.code;
    emitEdges(props.edges.filter((edge) => String(edge.edgeCode) !== removedCode).map(cloneValue));
  }
  selected.value = undefined;
}

function updateSelectedNode(patch: Partial<GraphNode>, nextCode?: string): void {
  if (!props.editable || !selectedNode.value) return;
  const currentCode = selectedNode.value.nodeCode;
  const updatedCode = nextCode || String(patch.nodeCode || currentCode);
  const renamed = updatedCode !== currentCode;
  const noLongerUserTask = selectedNode.value.nodeType === "USER_TASK"
    && patch.nodeType !== undefined
    && patch.nodeType !== "USER_TASK";
  const nextNodes = props.nodes.map((node, index) => {
    const nodeCode = String(node.nodeCode || `node-${index + 1}`);
    const nextNode = nodeCode === currentCode ? { ...cloneValue(node), ...patch } : cloneValue(node);
    if (renamed) return renameNodeReferences(nextNode, currentCode, updatedCode);
    if (noLongerUserTask && nodeCode !== currentCode) return removeNodeReferences(nextNode, currentCode);
    return nextNode;
  });
  emitNodes(nextNodes);
  if (updatedCode !== currentCode) {
    emitEdges(props.edges.map((edge) => ({
      ...cloneValue(edge),
      sourceNodeCode: String(edge.sourceNodeCode) === currentCode ? updatedCode : edge.sourceNodeCode,
      targetNodeCode: String(edge.targetNodeCode) === currentCode ? updatedCode : edge.targetNodeCode,
    })));
  }
  selected.value = { type: "node", code: updatedCode };
}

function updateSelectedEdge(patch: Partial<GraphEdge>, nextCode?: string): void {
  if (!props.editable || !selectedEdge.value) return;
  const currentCode = selectedEdge.value.edgeCode;
  const updatedCode = nextCode || String(patch.edgeCode || currentCode);
  emitEdges(props.edges.map((edge, index) => {
    const edgeCode = String(edge.edgeCode || `edge-${index + 1}`);
    return edgeCode === currentCode ? { ...cloneValue(edge), ...patch } : cloneValue(edge);
  }));
  selected.value = { type: "edge", code: updatedCode };
}

function updateNodeCode(value: string): void {
  const nodeCode = value.trim();
  if (!nodeCode) return;
  updateSelectedNode({ nodeCode }, nodeCode);
}

function updateNodeType(nodeType: string): void {
  if (nodeType === "USER_TASK") {
    const nodeCode = selectedNode.value?.nodeCode || "apply";
    const nextUserTaskNodeCodes = uniqueValues([...userTaskNodes.value.map((node) => node.nodeCode), nodeCode]);
    updateSelectedNode({
      nodeType,
      approverRule: selectedNode.value?.approverRule || { type: "ROLE", config: {} },
      multiInstanceMode: selectedNode.value?.multiInstanceMode || "SINGLE",
      ...createRuntimeDefaults(nodeCode, nextUserTaskNodeCodes),
    });
    return;
  }
  updateSelectedNode({
    nodeType,
    approverRule: undefined,
    multiInstanceMode: "SINGLE",
    listenerConfig: undefined,
    timeoutConfig: undefined,
    reminderConfig: undefined,
  });
}

function updateApproverType(type: string): void {
  const config = approverConfigForType(type, readApproverConfig(selectedNode.value));
  updateSelectedNode({
    approverRule: { type, config },
    ...(type === "STARTER" ? { multiInstanceMode: "SINGLE" } : {}),
  });
}

function updateApproverConfig(patch: Partial<ApproverConfigEditor>): void {
  const type = selectedApproverType.value;
  const config = approverConfigForType(type, { ...approverConfig.value, ...patch });
  updateSelectedNode({ approverRule: { type, config } });
}

function updateTimeoutPolicy(patch: Partial<TimeoutPolicy>): void {
  const next = { ...timeoutPolicy.value, ...patch };
  const durationMinutes = Math.max(0, Math.trunc(Number(next.durationMinutes || 0)));
  const action = normalizeTimeoutAction(next.action);
  const timeoutConfig = next.enabled
    ? compactConfig({
      enabled: true,
      durationMinutes,
      action,
      severity: next.severity || "MEDIUM",
      ...(action === "JUMP" && next.targetNodeCode ? { targetNodeCode: next.targetNodeCode } : {}),
    })
    : undefined;
  updateSelectedNode({ timeoutConfig });
}

function updateReminderPolicy(patch: Partial<ReminderPolicy>): void {
  const next = { ...reminderPolicy.value, ...patch };
  const maxCount = Math.max(0, Math.trunc(Number(next.maxCount || 0)));
  const reminderConfig = next.enabled
    ? compactConfig({
      enabled: true,
      maxCount,
      ...(next.messageTemplate.trim() ? { messageTemplate: next.messageTemplate.trim() } : {}),
    })
    : undefined;
  updateSelectedNode({ reminderConfig });
}

function updateListenerPolicy(patch: Partial<ListenerPolicy>): void {
  const next = { ...listenerPolicy.value, ...patch };
  const config = parseConfigObject(selectedNode.value?.listenerConfig);
  const existingRules = isRecord(config.taskActionRules) ? cloneRecord(config.taskActionRules) : {};
  if (next.rejectEnabled) {
    const existingReject = isRecord(existingRules.reject) ? cloneRecord(existingRules.reject) : {};
    existingRules.reject = {
      ...existingReject,
      enabled: true,
      targetNodeCodes: uniqueValues(next.rejectTargetNodeCodes),
    };
  } else {
    delete existingRules.reject;
  }
  if (next.directSendEnabled) {
    const existingDirectSend = isRecord(existingRules.directSend) ? cloneRecord(existingRules.directSend) : {};
    existingRules.directSend = {
      ...existingDirectSend,
      enabled: true,
      targetMode: "REJECT_SOURCE",
    };
  } else {
    delete existingRules.directSend;
  }
  if (Object.keys(existingRules).length) {
    config.taskActionRules = existingRules;
  } else {
    delete config.taskActionRules;
  }
  updateSelectedNode({ listenerConfig: compactConfig(config) });
}

function rejectTargetSelected(nodeCode: string): boolean {
  return listenerPolicy.value.rejectTargetNodeCodes.includes(nodeCode);
}

function toggleRejectTarget(nodeCode: string, checked: boolean): void {
  const current = listenerPolicy.value.rejectTargetNodeCodes;
  const nextTargets = checked
    ? uniqueValues([...current, nodeCode])
    : current.filter((targetNodeCode) => targetNodeCode !== nodeCode);
  updateListenerPolicy({ rejectTargetNodeCodes: nextTargets });
}

function connectionPoints(source: DisplayNode, target: DisplayNode): Pick<DisplayEdge, "x1" | "y1" | "x2" | "y2"> {
  const sourceCenterX = source.x + NODE_WIDTH / 2;
  const sourceCenterY = source.y + NODE_HEIGHT / 2;
  const targetCenterX = target.x + NODE_WIDTH / 2;
  const targetCenterY = target.y + NODE_HEIGHT / 2;
  const dx = targetCenterX - sourceCenterX;
  const dy = targetCenterY - sourceCenterY;
  if (Math.abs(dx) >= Math.abs(dy)) {
    return dx >= 0
      ? { x1: source.x + NODE_WIDTH, y1: sourceCenterY, x2: target.x, y2: targetCenterY }
      : { x1: source.x, y1: sourceCenterY, x2: target.x + NODE_WIDTH, y2: targetCenterY };
  }
  return dy >= 0
    ? { x1: sourceCenterX, y1: source.y + NODE_HEIGHT, x2: targetCenterX, y2: target.y }
    : { x1: sourceCenterX, y1: source.y, x2: targetCenterX, y2: target.y + NODE_HEIGHT };
}

function readTimeoutPolicy(node: DisplayNode | undefined): TimeoutPolicy {
  const config = parseConfigObject(node?.timeoutConfig);
  return {
    enabled: config.enabled === true,
    durationMinutes: numberValue(config.durationMinutes, 0),
    action: normalizeTimeoutAction(config.action),
    severity: String(config.severity || "MEDIUM"),
    targetNodeCode: String(config.targetNodeCode || ""),
  };
}

function readReminderPolicy(node: DisplayNode | undefined): ReminderPolicy {
  const config = parseConfigObject(node?.reminderConfig);
  return {
    enabled: config.enabled === true,
    maxCount: numberValue(config.maxCount, 1),
    messageTemplate: String(config.messageTemplate || "任务已超时，请尽快处理"),
  };
}

function readListenerPolicy(node: DisplayNode | undefined): ListenerPolicy {
  const config = parseConfigObject(node?.listenerConfig);
  const rules = isRecord(config.taskActionRules) ? config.taskActionRules : {};
  const reject = isRecord(rules.reject) ? rules.reject : {};
  const directSend = isRecord(rules.directSend) ? rules.directSend : {};
  return {
    rejectEnabled: reject.enabled === true,
    rejectTargetNodeCodes: Array.isArray(reject.targetNodeCodes)
      ? reject.targetNodeCodes.map(String).filter(Boolean)
      : [],
    directSendEnabled: directSend.enabled === true,
  };
}

function readApproverConfig(node: DisplayNode | undefined): ApproverConfigEditor {
  const config = node?.approverRule?.config || parseConfigObject(node?.approverRuleConfig);
  const userIds = Array.isArray(config.userIds)
    ? config.userIds.map(String).filter(Boolean)
    : [];
  return {
    userIdsText: userIds.join(", "),
    departmentId: String(config.departmentId || ""),
    roleCode: String(config.roleCode || ""),
    departmentFrom: String(config.departmentFrom || (config.departmentId ? "fixed" : "starter")),
    expression: String(config.expression || ""),
  };
}

function approverConfigForType(type: string, config: ApproverConfigEditor): Record<string, unknown> {
  if (type === "STARTER") return {};
  if (type === "USER") return compactRecord({ userIds: csvValues(config.userIdsText) });
  if (type === "DEPARTMENT") return compactRecord({ departmentId: config.departmentId.trim() });
  if (type === "ROLE") return compactRecord({ roleCode: config.roleCode.trim() });
  if (type === "ROLE_IN_DEPARTMENT") {
    return compactRecord({
      roleCode: config.roleCode.trim(),
      departmentFrom: config.departmentFrom || "starter",
      ...(config.departmentFrom === "fixed" ? { departmentId: config.departmentId.trim() } : {}),
    });
  }
  if (type === "APPROVER_EXPRESSION") return compactRecord({ expression: config.expression.trim() });
  return {};
}

function createRuntimeDefaults(
  nodeCode: string,
  userTaskNodeCodes: string[],
): Pick<GraphNode, "listenerConfig" | "timeoutConfig" | "reminderConfig"> {
  return cloneValue(createDefaultUserTaskConfigs({
    rejectEnabled: Boolean(nodeCode && nodeCode !== userTaskNodeCodes[0]),
    rejectTargetNodeCodes: userTaskNodeCodes,
  })) as Pick<GraphNode, "listenerConfig" | "timeoutConfig" | "reminderConfig">;
}

function removeNodeReferences(node: GraphNode, removedNodeCode: string): GraphNode {
  let nextNode = pruneRejectTarget(node, removedNodeCode);
  if (nextNode.pairedGatewayCode === removedNodeCode) {
    nextNode = { ...nextNode, pairedGatewayCode: undefined };
  }
  nextNode = pruneTimeoutTarget(nextNode, removedNodeCode);
  return nextNode;
}

function renameNodeReferences(node: GraphNode, oldNodeCode: string, newNodeCode: string): GraphNode {
  let nextNode = renameRejectTarget(node, oldNodeCode, newNodeCode);
  if (nextNode.pairedGatewayCode === oldNodeCode) {
    nextNode = { ...nextNode, pairedGatewayCode: newNodeCode };
  }
  nextNode = renameTimeoutTarget(nextNode, oldNodeCode, newNodeCode);
  return nextNode;
}

function pruneRejectTarget(node: GraphNode, removedNodeCode: string): GraphNode {
  const policy = readListenerPolicy(node as DisplayNode);
  if (!policy.rejectTargetNodeCodes.includes(removedNodeCode)) return node;
  const config = parseConfigObject(node.listenerConfig);
  const rules = isRecord(config.taskActionRules) ? cloneRecord(config.taskActionRules) : {};
  const reject = isRecord(rules.reject) ? cloneRecord(rules.reject) : {};
  const nextTargets = policy.rejectTargetNodeCodes.filter((targetNodeCode) => targetNodeCode !== removedNodeCode);
  if (nextTargets.length) {
    rules.reject = { ...reject, enabled: true, targetNodeCodes: nextTargets };
  } else {
    delete rules.reject;
  }
  if (Object.keys(rules).length) {
    config.taskActionRules = rules;
  } else {
    delete config.taskActionRules;
  }
  return { ...node, listenerConfig: compactConfig(config) };
}

function renameRejectTarget(node: GraphNode, oldNodeCode: string, newNodeCode: string): GraphNode {
  const policy = readListenerPolicy(node as DisplayNode);
  if (!policy.rejectTargetNodeCodes.includes(oldNodeCode)) return node;
  const config = parseConfigObject(node.listenerConfig);
  const rules = isRecord(config.taskActionRules) ? cloneRecord(config.taskActionRules) : {};
  const reject = isRecord(rules.reject) ? cloneRecord(rules.reject) : {};
  rules.reject = {
    ...reject,
    enabled: true,
    targetNodeCodes: uniqueValues(policy.rejectTargetNodeCodes.map((targetNodeCode) =>
      targetNodeCode === oldNodeCode ? newNodeCode : targetNodeCode)),
  };
  config.taskActionRules = rules;
  return { ...node, listenerConfig: compactConfig(config) };
}

function pruneTimeoutTarget(node: GraphNode, removedNodeCode: string): GraphNode {
  const config = parseConfigObject(node.timeoutConfig);
  if (config.targetNodeCode !== removedNodeCode) return node;
  delete config.targetNodeCode;
  if (config.action === "JUMP") config.action = "REMIND";
  return { ...node, timeoutConfig: compactConfig(config) };
}

function renameTimeoutTarget(node: GraphNode, oldNodeCode: string, newNodeCode: string): GraphNode {
  const config = parseConfigObject(node.timeoutConfig);
  if (config.targetNodeCode !== oldNodeCode) return node;
  config.targetNodeCode = newNodeCode;
  return { ...node, timeoutConfig: compactConfig(config) };
}

function parseConfigObject(value: unknown): Record<string, unknown> {
  if (typeof value === "string") {
    if (!value.trim()) return {};
    try {
      const parsed = JSON.parse(value);
      return isRecord(parsed) ? parsed : {};
    } catch {
      return {};
    }
  }
  return isRecord(value) ? cloneRecord(value) : {};
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function cloneRecord(value: Record<string, unknown>): Record<string, unknown> {
  return JSON.parse(JSON.stringify(value)) as Record<string, unknown>;
}

function cloneValue<T>(value: T): T {
  return value === undefined ? value : JSON.parse(JSON.stringify(value)) as T;
}

function compactConfig(config: Record<string, unknown>): Record<string, unknown> | undefined {
  return Object.keys(config).length ? config : undefined;
}

function compactRecord(config: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(Object.entries(config).filter(([, value]) => {
    if (typeof value === "string") return value.trim().length > 0;
    if (Array.isArray(value)) return value.length > 0;
    return value !== undefined && value !== null;
  }));
}

function csvValues(value: string): string[] {
  return value.split(/[,，\s]+/).map((item) => item.trim()).filter(Boolean);
}

function normalizeTimeoutAction(value: unknown): string {
  const action = String(value || "REMIND").trim().toUpperCase().replace(/[\s-]+/g, "_");
  if (action === "WARNING" || action === "WARN") return "ALERT";
  if (["FORCE_COMPETE", "FROCE_COMPETE", "FORCE_COMPELETE", "FORCE_COMPLETED"].includes(action)) {
    return "FORCE_COMPLETE";
  }
  return TIMEOUT_ACTION_OPTIONS.some((option) => option.value === action) ? action : "REMIND";
}

function numberValue(value: unknown, fallback: number): number {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function uniqueCode(prefix: string, values: Array<string | undefined>): string {
  const used = new Set(values.map((value) => String(value || "")).filter(Boolean));
  let index = used.size + 1;
  let code = `${prefix}_${index}`;
  while (used.has(code)) {
    index += 1;
    code = `${prefix}_${index}`;
  }
  return code;
}

function uniqueValues(values: string[]): string[] {
  return Array.from(new Set(values.map((value) => value.trim()).filter(Boolean)));
}

function eventValue(event: Event): string {
  return (event.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value;
}

function eventChecked(event: Event): boolean {
  return (event.target as HTMLInputElement).checked;
}

function emitNodes(nextNodes: GraphNode[]): void {
  emit("update:nodes", nextNodes);
}

function emitEdges(nextEdges: GraphEdge[]): void {
  emit("update:edges", nextEdges);
}
</script>

<template>
  <div class="graph-designer">
    <div class="graph-designer-heading">
      <div>
        <span class="eyebrow">{{ editable ? "可编辑流程图" : "流程图" }}</span>
        <h3>{{ title }}</h3>
      </div>
      <span :class="['graph-mode-tag', { editable }]">
        {{ editable ? "拖动节点调整位置" : "只读" }}
      </span>
    </div>

    <div class="graph-toolbar" aria-label="画布工具栏">
      <div v-if="editable" class="graph-edit-tools">
        <button data-testid="add-node" type="button" @click="addNode">添加节点</button>
        <button
          data-testid="start-connection"
          type="button"
          :class="{ active: connectionMode }"
          :disabled="nodes.length < 2"
          @click="startConnectionMode"
        >
          开始连线
        </button>
        <button data-testid="stop-connection" type="button" :disabled="!connectionMode" @click="stopConnectionMode">
          退出连线
        </button>
        <button data-testid="delete-selected" type="button" :disabled="!selected" @click="deleteSelected">删除选中</button>
        <span v-if="connectionMode" class="connection-hint">
          {{ pendingConnectionSource ? `已选择来源节点：${pendingConnectionSource}` : "连线模式：按顺序点击来源节点和目标节点" }}
        </span>
      </div>
      <div class="graph-zoom-tools" aria-label="缩放工具">
        <button data-testid="zoom-out" type="button" :disabled="zoom <= ZOOM_MIN || Boolean(drag) || Boolean(canvasDrag)" title="缩小" @click="zoomOut">−</button>
        <span data-testid="zoom-percent" aria-live="polite">{{ zoomPercent }}</span>
        <button data-testid="zoom-in" type="button" :disabled="zoom >= ZOOM_MAX || Boolean(drag) || Boolean(canvasDrag)" title="放大" @click="zoomIn">＋</button>
        <button data-testid="zoom-reset" type="button" :disabled="viewportIsDefault || Boolean(drag) || Boolean(canvasDrag)" @click="resetZoom">重置</button>
      </div>
    </div>

    <div class="graph-designer-layout">
      <div class="graph-scroll graph-designer-canvas">
        <svg
          :class="['process-graph', { panning: Boolean(canvasDrag) }]"
          :viewBox="viewBox"
          role="img"
          aria-label="流程节点与连线"
          data-testid="process-graph"
          @mousedown.self="startCanvasDrag"
          @mousemove="moveDrag"
          @mouseup="stopDrag"
          @mouseleave="stopDrag"
          @wheel="handleWheel"
        >
          <defs>
            <marker :id="markerId" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto">
              <path d="M0,0 L0,6 L9,3 z" />
            </marker>
          </defs>
          <g
            v-for="edge in edges"
            :key="edge.edgeCode"
            :class="['graph-edge', { selected: selectedEdge?.edgeCode === edge.edgeCode }]"
            :data-testid="`graph-edge-${edge.edgeCode}`"
            role="button"
            tabindex="0"
            @click.stop="selectEdge(edge)"
            @keydown.enter.prevent="selectEdge(edge)"
          >
            <line
              :x1="edge.x1"
              :y1="edge.y1"
              :x2="edge.x2"
              :y2="edge.y2"
              :marker-end="`url(#${markerId})`"
            />
            <text
              v-if="edge.conditionExpression || edge.defaultEdge"
              :x="edge.labelX"
              :y="edge.labelY"
            >{{ edge.defaultEdge ? "默认" : edge.conditionExpression }}</text>
          </g>
          <g
            v-for="node in nodes"
            :key="node.nodeCode"
            :class="[
              'graph-node',
              nodeClass(node.nodeType),
              {
                selected: selectedNode?.nodeCode === node.nodeCode,
                connectionSource: pendingConnectionSource === node.nodeCode,
                draggable: editable,
              },
            ]"
            :data-testid="`graph-node-${node.nodeCode}`"
            role="button"
            tabindex="0"
            @click.stop="handleCanvasNodeClick(node)"
            @mousedown.stop="startDrag(node, $event)"
            @keydown.enter.prevent="handleCanvasNodeClick(node)"
          >
            <rect :x="node.x" :y="node.y" :width="NODE_WIDTH" :height="NODE_HEIGHT" :rx="NODE_RADIUS" />
            <text :x="node.x + NODE_WIDTH / 2" :y="node.y + 27">{{ node.nodeName }}</text>
            <text class="node-type" :x="node.x + NODE_WIDTH / 2" :y="node.y + 45">{{ nodeTypeLabel(node.nodeType) }}</text>
          </g>
        </svg>
      </div>

      <aside class="graph-detail-panel">
        <template v-if="selectedNode">
          <span class="eyebrow">节点配置</span>
          <h4>{{ selectedNode.nodeName }}</h4>

          <section class="graph-config-section">
            <h5>基础属性</h5>
            <label>
              节点编码
              <input :value="selectedNode.nodeCode" :disabled="!editable" data-testid="node-code" @change="updateNodeCode(eventValue($event))">
            </label>
            <label>
              节点名称
              <input :value="selectedNode.nodeName" :disabled="!editable" data-testid="node-name" @input="updateSelectedNode({ nodeName: eventValue($event) })">
            </label>
            <label>
              节点类型
              <select :value="selectedNode.nodeType" :disabled="!editable" data-testid="node-type" @change="updateNodeType(eventValue($event))">
                <option v-for="option in NODE_TYPE_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            <label v-if="selectedNodeIsUserTask">
              审批来源
              <select :value="selectedApproverType" :disabled="!editable" data-testid="approver-rule-type" @change="updateApproverType(eventValue($event))">
                <option v-for="option in APPROVER_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            <label v-if="selectedNodeIsUserTask && selectedApproverType === 'USER'">
              审批人 ID
              <input
                :value="approverConfig.userIdsText"
                :disabled="!editable"
                data-testid="approver-user-ids"
                placeholder="user_1, user_2"
                @change="updateApproverConfig({ userIdsText: eventValue($event) })"
              >
            </label>
            <label v-if="selectedNodeIsUserTask && selectedApproverType === 'DEPARTMENT'">
              部门 ID
              <input
                :value="approverConfig.departmentId"
                :disabled="!editable"
                data-testid="approver-department-id"
                placeholder="dept_sales"
                @change="updateApproverConfig({ departmentId: eventValue($event) })"
              >
            </label>
            <label v-if="selectedNodeIsUserTask && ['ROLE', 'ROLE_IN_DEPARTMENT'].includes(selectedApproverType)">
              角色编码
              <input
                :value="approverConfig.roleCode"
                :disabled="!editable"
                data-testid="approver-role-code"
                placeholder="finance"
                @change="updateApproverConfig({ roleCode: eventValue($event) })"
              >
            </label>
            <label v-if="selectedNodeIsUserTask && selectedApproverType === 'ROLE_IN_DEPARTMENT'">
              部门来源
              <select
                :value="approverConfig.departmentFrom"
                :disabled="!editable"
                data-testid="approver-department-from"
                @change="updateApproverConfig({ departmentFrom: eventValue($event) })"
              >
                <option value="starter">发起人部门</option>
                <option value="fixed">指定部门</option>
              </select>
            </label>
            <label v-if="selectedNodeIsUserTask && selectedApproverType === 'ROLE_IN_DEPARTMENT' && approverConfig.departmentFrom === 'fixed'">
              指定部门 ID
              <input
                :value="approverConfig.departmentId"
                :disabled="!editable"
                data-testid="approver-department-id"
                placeholder="dept_sales"
                @change="updateApproverConfig({ departmentId: eventValue($event) })"
              >
            </label>
            <label v-if="selectedNodeIsUserTask && selectedApproverType === 'APPROVER_EXPRESSION'">
              审批人表达式
              <input
                :value="approverConfig.expression"
                :disabled="!editable"
                data-testid="approver-expression"
                placeholder="departmentManager(starterDeptId)"
                @change="updateApproverConfig({ expression: eventValue($event) })"
              >
            </label>
            <label v-if="selectedNodeIsUserTask">
              多人模式
              <select :value="selectedNode.multiInstanceMode || 'SINGLE'" :disabled="!editable" data-testid="multi-instance-mode" @change="updateSelectedNode({ multiInstanceMode: eventValue($event) })">
                <option v-for="option in MULTI_INSTANCE_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            <label v-if="selectedNode.nodeType.includes('GATEWAY')">
              配对网关
              <select :value="selectedNode.pairedGatewayCode || ''" :disabled="!editable" data-testid="paired-gateway-code" @change="updateSelectedNode({ pairedGatewayCode: eventValue($event) })">
                <option value="">请选择</option>
                <option v-for="node in nodes.filter((item) => item.nodeCode !== selectedNode?.nodeCode && item.nodeType.includes('GATEWAY'))" :key="node.nodeCode" :value="node.nodeCode">{{ node.nodeName }}</option>
              </select>
            </label>
            <div class="coordinate-row">
              <label>
                X
                <input :value="selectedNode.x" :disabled="!editable" type="number" @change="updateSelectedNode({ positionX: Number(eventValue($event)) })">
              </label>
              <label>
                Y
                <input :value="selectedNode.y" :disabled="!editable" type="number" @change="updateSelectedNode({ positionY: Number(eventValue($event)) })">
              </label>
            </div>
          </section>

          <template v-if="selectedNodeIsUserTask">
            <section class="graph-config-section">
              <h5>超时策略</h5>
              <label class="checkbox-row">
                <input
                  type="checkbox"
                  :checked="timeoutPolicy.enabled"
                  :disabled="!editable"
                  data-testid="timeout-enabled"
                  @change="updateTimeoutPolicy({ enabled: eventChecked($event) })"
                >
                启用超时
              </label>
              <label>
                超时时长（分钟）
                <input
                  type="number"
                  min="0"
                  :value="timeoutPolicy.durationMinutes"
                  :disabled="!editable || !timeoutPolicy.enabled"
                  data-testid="timeout-duration-minutes"
                  @change="updateTimeoutPolicy({ durationMinutes: Number(eventValue($event)) })"
                >
              </label>
              <label>
                超时动作
                <select
                  :value="timeoutPolicy.action"
                  :disabled="!editable || !timeoutPolicy.enabled"
                  data-testid="timeout-action"
                  @change="updateTimeoutPolicy({ action: eventValue($event) })"
                >
                  <option v-for="option in TIMEOUT_ACTION_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
              </label>
              <label>
                告警级别
                <select
                  :value="timeoutPolicy.severity"
                  :disabled="!editable || !timeoutPolicy.enabled"
                  data-testid="timeout-severity"
                  @change="updateTimeoutPolicy({ severity: eventValue($event) })"
                >
                  <option v-for="option in SEVERITY_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
              </label>
              <label v-if="timeoutPolicy.action === 'JUMP'">
                跳转目标节点
                <select
                  :value="timeoutPolicy.targetNodeCode"
                  :disabled="!editable || !timeoutPolicy.enabled"
                  data-testid="timeout-target-node"
                  @change="updateTimeoutPolicy({ targetNodeCode: eventValue($event) })"
                >
                  <option value="">请选择</option>
                  <option v-for="node in userTaskNodes.filter((item) => item.nodeCode !== selectedNode?.nodeCode)" :key="node.nodeCode" :value="node.nodeCode">{{ node.nodeName }}</option>
                </select>
              </label>
            </section>

            <section class="graph-config-section">
              <h5>驳回策略</h5>
              <label class="checkbox-row">
                <input
                  type="checkbox"
                  :checked="listenerPolicy.rejectEnabled"
                  :disabled="!editable"
                  data-testid="reject-enabled"
                  @change="updateListenerPolicy({ rejectEnabled: eventChecked($event) })"
                >
                允许驳回
              </label>
              <div class="reject-target-list" data-testid="reject-target-node-codes">
                <label v-for="node in userTaskNodes" :key="node.nodeCode" class="checkbox-row reject-target-option">
                  <input
                    type="checkbox"
                    :checked="rejectTargetSelected(node.nodeCode)"
                    :disabled="!editable || !listenerPolicy.rejectEnabled"
                    :data-testid="`reject-target-${node.nodeCode}`"
                    @change="toggleRejectTarget(node.nodeCode, eventChecked($event))"
                  >
                  {{ node.nodeName }} / {{ node.nodeCode }}
                </label>
              </div>
            </section>

            <section class="graph-config-section">
              <h5>直送策略</h5>
              <label class="checkbox-row">
                <input
                  type="checkbox"
                  :checked="listenerPolicy.directSendEnabled"
                  :disabled="!editable"
                  data-testid="direct-send-enabled"
                  @change="updateListenerPolicy({ directSendEnabled: eventChecked($event) })"
                >
                启用直送
              </label>
              <label>
                直送目标
                <select :value="'REJECT_SOURCE'" :disabled="!editable || !listenerPolicy.directSendEnabled" data-testid="direct-send-target-mode">
                  <option value="REJECT_SOURCE">驳回来源节点</option>
                </select>
              </label>
            </section>

            <section class="graph-config-section">
              <h5>提醒策略</h5>
              <label class="checkbox-row">
                <input
                  type="checkbox"
                  :checked="reminderPolicy.enabled"
                  :disabled="!editable"
                  data-testid="reminder-enabled"
                  @change="updateReminderPolicy({ enabled: eventChecked($event) })"
                >
                启用提醒
              </label>
              <label>
                最大提醒次数
                <input
                  type="number"
                  min="0"
                  :value="reminderPolicy.maxCount"
                  :disabled="!editable || !reminderPolicy.enabled"
                  data-testid="reminder-max-count"
                  @change="updateReminderPolicy({ maxCount: Number(eventValue($event)) })"
                >
              </label>
              <label>
                提醒文案模板
                <input
                  :value="reminderPolicy.messageTemplate"
                  :disabled="!editable || !reminderPolicy.enabled"
                  data-testid="reminder-message-template"
                  @change="updateReminderPolicy({ messageTemplate: eventValue($event) })"
                >
              </label>
            </section>
          </template>
        </template>

        <template v-else-if="selectedEdge">
          <span class="eyebrow">连线配置</span>
          <h4>条件分支策略：{{ selectedEdge.edgeCode }}</h4>
          <section class="graph-config-section">
            <label>
              连线编码
              <input :value="selectedEdge.edgeCode" :disabled="!editable" data-testid="edge-code" @change="updateSelectedEdge({ edgeCode: eventValue($event) }, eventValue($event))">
            </label>
            <label>
              来源节点
              <select :value="selectedEdge.sourceNodeCode" :disabled="!editable" data-testid="edge-source-node" @change="updateSelectedEdge({ sourceNodeCode: eventValue($event) })">
                <option v-for="node in nodes" :key="node.nodeCode" :value="node.nodeCode">{{ node.nodeName }}</option>
              </select>
            </label>
            <label>
              目标节点
              <select :value="selectedEdge.targetNodeCode" :disabled="!editable" data-testid="edge-target-node" @change="updateSelectedEdge({ targetNodeCode: eventValue($event) })">
                <option v-for="node in nodes" :key="node.nodeCode" :value="node.nodeCode">{{ node.nodeName }}</option>
              </select>
            </label>
            <label>
              条件表达式
              <input
                :value="selectedEdge.conditionExpression"
                :disabled="!editable"
                data-testid="edge-condition-expression"
                placeholder="例如：amount > 1000"
                @input="updateSelectedEdge({ conditionExpression: eventValue($event) })"
              >
            </label>
            <label class="checkbox-row">
              <input
                type="checkbox"
                :checked="selectedEdge.defaultEdge === true"
                :disabled="!editable"
                data-testid="edge-default"
                @change="updateSelectedEdge({ defaultEdge: eventChecked($event) })"
              >
              默认出线
            </label>
          </section>
        </template>

        <template v-else>
          <span class="eyebrow">配置详情</span>
          <h4>选择节点或连线</h4>
          <p>点击画布中的节点查看超时、驳回、直送和提醒策略；点击连线查看条件分支策略。</p>
        </template>
      </aside>
    </div>
  </div>
</template>
