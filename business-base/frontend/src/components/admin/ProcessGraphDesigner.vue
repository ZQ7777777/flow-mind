<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from "vue";
import { useSvgViewport } from "../../composables/useSvgViewport";
import type {
  ProcessDefinitionDepartmentOption, ProcessDefinitionOptions, ProcessDefinitionRoleOption,
  ProcessDefinitionUserOption, ProcessEdge, ProcessNode,
} from "../../types/admin";

const props = withDefaults(defineProps<{ options?: ProcessDefinitionOptions }>(), {
  options: () => ({ users: [], departments: [], roles: [] }),
});
const nodes = defineModel<ProcessNode[]>("nodes", { required: true });
const edges = defineModel<ProcessEdge[]>("edges", { required: true });

const nodeWidth = 130;
const nodeHeight = 44;
const viewportWidth = ref(900);
const viewportHeight = ref(420);
const viewport = useSvgViewport(viewportWidth, viewportHeight);
const selectedType = ref<"node" | "edge" | "">("");
const selectedCode = ref("");
const connectionMode = ref(false);
const connectionSource = ref("");
const configError = ref("");
const selectedNode = computed(() => selectedType.value === "node"
  ? nodes.value.find((node) => node.nodeCode === selectedCode.value) : undefined);
const selectedEdge = computed(() => selectedType.value === "edge"
  ? edges.value.find((edge) => edge.edgeCode === selectedCode.value) : undefined);
const byCode = computed(() => new Map(nodes.value.map((node) => [node.nodeCode, node])));
const visibleEdges = computed(() => edges.value.flatMap((edge) => {
  const source = byCode.value.get(edge.sourceNodeCode);
  const target = byCode.value.get(edge.targetNodeCode);
  return source && target ? [{ edge, source, target }] : [];
}));
const graphBounds = computed(() => ({
  x: 0,
  y: 0,
  width: Math.max(760, ...nodes.value.map((node) => (node.positionX ?? 0) + nodeWidth + 40)),
  height: Math.max(320, ...nodes.value.map((node) => (node.positionY ?? 0) + nodeHeight + 40)),
}));
const userTaskNodes = computed(() => nodes.value.filter((node) => node.nodeType === "USER_TASK"));

const editor = reactive({
  selectedApproverIds: [] as string[],
  selectedDepartmentId: "",
  selectedRoleCode: "",
  selectedRoleDepartmentMode: "STARTER" as "STARTER" | "FIXED",
  selectedRoleDepartmentId: "",
  approverExpression: "",
  timeoutEnabled: false,
  timeoutDurationMinutes: 0,
  timeoutAction: "REMIND",
  timeoutSeverity: "MEDIUM",
  timeoutTargetNodeCode: "",
  reminderEnabled: false,
  reminderMaxCount: 1,
  reminderMessageTemplate: "任务已超时，请尽快处理",
  rejectEnabled: false,
  rejectTargetNodeCodes: [] as string[],
  directSendEnabled: false,
});

let drag: { code: string; offsetX: number; offsetY: number; moved: boolean } | undefined;

function parseObject(value: string | undefined, label: string): Record<string, unknown> | undefined {
  if (!value?.trim()) return {};
  try {
    const parsed: unknown = JSON.parse(value);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("必须是 JSON 对象");
    return parsed as Record<string, unknown>;
  } catch (error) {
    configError.value = `${label} 格式不正确：${error instanceof Error ? error.message : "未知错误"}`;
    return undefined;
  }
}

function strings(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string" && !!item) : [];
}

function loadNodeEditor(node: ProcessNode): void {
  configError.value = "";
  const approver = parseObject(node.approverRuleConfig, "审批人配置");
  const timeout = parseObject(node.timeoutConfig, "超时配置");
  const reminder = parseObject(node.reminderConfig, "提醒配置");
  const listener = parseObject(node.listenerConfig, "监听器配置");
  if (approver) {
    editor.selectedApproverIds = strings(approver.userIds);
    editor.selectedDepartmentId = typeof approver.departmentId === "string" ? approver.departmentId : "";
    editor.selectedRoleCode = typeof approver.roleCode === "string" ? approver.roleCode : "";
    editor.selectedRoleDepartmentId = editor.selectedDepartmentId;
    editor.selectedRoleDepartmentMode = editor.selectedRoleDepartmentId ? "FIXED" : "STARTER";
    editor.approverExpression = typeof approver.expression === "string" ? approver.expression : "";
  }
  if (timeout) {
    editor.timeoutEnabled = timeout.enabled === true;
    editor.timeoutDurationMinutes = Number(timeout.durationMinutes ?? 0);
    editor.timeoutAction = typeof timeout.action === "string" ? timeout.action : "REMIND";
    editor.timeoutSeverity = typeof timeout.severity === "string" ? timeout.severity : "MEDIUM";
    editor.timeoutTargetNodeCode = typeof timeout.targetNodeCode === "string" ? timeout.targetNodeCode : "";
  }
  if (reminder) {
    editor.reminderEnabled = reminder.enabled === true;
    editor.reminderMaxCount = Number(reminder.maxCount ?? 1);
    editor.reminderMessageTemplate = typeof reminder.messageTemplate === "string"
      ? reminder.messageTemplate : "任务已超时，请尽快处理";
  }
  if (listener) {
    const rules = listener.taskActionRules && typeof listener.taskActionRules === "object"
      ? listener.taskActionRules as Record<string, unknown> : {};
    const reject = rules.reject && typeof rules.reject === "object" ? rules.reject as Record<string, unknown> : {};
    const directSend = rules.directSend && typeof rules.directSend === "object"
      ? rules.directSend as Record<string, unknown> : {};
    editor.rejectEnabled = reject.enabled === true;
    editor.rejectTargetNodeCodes = strings(reject.targetNodeCodes);
    editor.directSendEnabled = directSend.enabled === true;
  }
}

function selectNode(node: ProcessNode): void {
  selectedType.value = "node";
  selectedCode.value = node.nodeCode;
  loadNodeEditor(node);
}

function selectEdge(edge: ProcessEdge): void {
  selectedType.value = "edge";
  selectedCode.value = edge.edgeCode;
  configError.value = "";
}

function addNode(type = "USER_TASK"): void {
  let index = nodes.value.length + 1;
  let code = `${type.toLowerCase()}_${index}`;
  while (nodes.value.some((node) => node.nodeCode === code)) code = `${type.toLowerCase()}_${++index}`;
  const names: Record<string, string> = {
    START: "开始", USER_TASK: "用户任务", EXCLUSIVE_GATEWAY: "排他网关",
    PARALLEL_SPLIT_GATEWAY: "并行分支", PARALLEL_JOIN_GATEWAY: "并行汇聚", END: "结束",
  };
  const node: ProcessNode = {
    nodeCode: code, nodeName: names[type] ?? "节点", nodeType: type,
    approverRuleType: type === "USER_TASK" ? "USER" : undefined,
    approverRuleConfig: type === "USER_TASK" ? '{"userIds":[]}' : undefined,
    multiInstanceMode: type === "USER_TASK" ? "SINGLE" : undefined,
    positionX: 60 + ((index - 1) % 4) * 180,
    positionY: 60 + Math.floor((index - 1) / 4) * 110,
    sortOrder: index,
  };
  nodes.value = [...nodes.value, node];
  selectNode(node);
}

function syncApprover(): void {
  const node = selectedNode.value;
  if (!node) return;
  let config: Record<string, unknown> = {};
  switch (node.approverRuleType) {
    case "USER": config = { userIds: editor.selectedApproverIds }; break;
    case "DEPARTMENT": config = { departmentId: editor.selectedDepartmentId }; break;
    case "ROLE": config = { roleCode: editor.selectedRoleCode }; break;
    case "ROLE_IN_DEPARTMENT":
      config = { roleCode: editor.selectedRoleCode };
      if (editor.selectedRoleDepartmentMode === "FIXED") config.departmentId = editor.selectedRoleDepartmentId;
      break;
    case "APPROVER_EXPRESSION": config = { expression: editor.approverExpression }; break;
    case "STARTER": node.multiInstanceMode = "SINGLE"; break;
  }
  node.approverRuleConfig = JSON.stringify(config);
  configError.value = "";
}

function syncTimeout(): void {
  const node = selectedNode.value;
  if (!node) return;
  const config: Record<string, unknown> = { enabled: editor.timeoutEnabled };
  if (editor.timeoutEnabled) {
    Object.assign(config, {
      durationMinutes: Math.max(0, Number(editor.timeoutDurationMinutes) || 0),
      action: editor.timeoutAction,
      severity: editor.timeoutSeverity,
    });
    if (editor.timeoutAction === "JUMP") config.targetNodeCode = editor.timeoutTargetNodeCode;
  }
  node.timeoutConfig = JSON.stringify(config);
}

function syncReminder(): void {
  const node = selectedNode.value;
  if (!node) return;
  node.reminderConfig = JSON.stringify({
    enabled: editor.reminderEnabled,
    maxCount: Math.max(0, Number(editor.reminderMaxCount) || 0),
    messageTemplate: editor.reminderMessageTemplate,
  });
}

function syncListener(): void {
  const node = selectedNode.value;
  if (!node) return;
  const root = parseObject(node.listenerConfig, "监听器配置");
  if (!root) return;
  const rules = root.taskActionRules && typeof root.taskActionRules === "object" && !Array.isArray(root.taskActionRules)
    ? { ...(root.taskActionRules as Record<string, unknown>) } : {};
  rules.reject = { enabled: editor.rejectEnabled, targetNodeCodes: editor.rejectTargetNodeCodes };
  rules.directSend = { enabled: editor.directSendEnabled };
  root.taskActionRules = rules;
  node.listenerConfig = JSON.stringify(root);
  configError.value = "";
}

function reloadAdvancedJson(): void {
  if (selectedNode.value) loadNodeEditor(selectedNode.value);
}

function beginNodeDrag(event: PointerEvent, node: ProcessNode): void {
  if (connectionMode.value) return;
  selectNode(node);
  const point = viewport.graphPoint(event);
  drag = {
    code: node.nodeCode,
    offsetX: point.x - (node.positionX ?? 0),
    offsetY: point.y - (node.positionY ?? 0),
    moved: false,
  };
  (event.currentTarget as Element & { setPointerCapture?: (id: number) => void }).setPointerCapture?.(event.pointerId);
}

function movePointer(event: PointerEvent): void {
  if (!drag) return viewport.movePan(event);
  const point = viewport.graphPoint(event);
  const nextX = Math.max(0, Math.round(point.x - drag.offsetX));
  const nextY = Math.max(0, Math.round(point.y - drag.offsetY));
  const node = nodes.value.find((item) => item.nodeCode === drag?.code);
  if (!node) return;
  drag.moved ||= nextX !== node.positionX || nextY !== node.positionY;
  node.positionX = nextX;
  node.positionY = nextY;
}

function finishPointer(event: PointerEvent): void {
  drag = undefined;
  viewport.endPan(event);
}

function toggleConnection(): void {
  connectionMode.value = !connectionMode.value;
  connectionSource.value = "";
}

function handleNodeClick(node: ProcessNode): void {
  if (!connectionMode.value) return;
  if (!connectionSource.value) {
    connectionSource.value = node.nodeCode;
    selectNode(node);
    return;
  }
  const source = connectionSource.value;
  connectionSource.value = "";
  if (source === node.nodeCode) return;
  if (edges.value.some((edge) => edge.sourceNodeCode === source && edge.targetNodeCode === node.nodeCode)) return;
  let index = edges.value.length + 1;
  let edgeCode = `edge_${source}_${node.nodeCode}_${index}`;
  while (edges.value.some((edge) => edge.edgeCode === edgeCode)) edgeCode = `edge_${source}_${node.nodeCode}_${++index}`;
  const edge: ProcessEdge = {
    edgeCode, sourceNodeCode: source, targetNodeCode: node.nodeCode,
    defaultEdge: false, sortOrder: edges.value.length + 1,
  };
  edges.value = [...edges.value, edge];
  selectEdge(edge);
}

function updateJsonNodeReference(node: ProcessNode, oldCode: string, newCode: string): ProcessNode {
  const next = { ...node };
  if (next.pairedGatewayCode === oldCode) next.pairedGatewayCode = newCode;
  const timeout = parseJsonSilently(next.timeoutConfig);
  if (timeout?.targetNodeCode === oldCode) {
    timeout.targetNodeCode = newCode;
    next.timeoutConfig = JSON.stringify(timeout);
  }
  const listener = parseJsonSilently(next.listenerConfig);
  const rules = listener?.taskActionRules as Record<string, unknown> | undefined;
  const reject = rules?.reject as Record<string, unknown> | undefined;
  if (Array.isArray(reject?.targetNodeCodes)) {
    reject.targetNodeCodes = reject.targetNodeCodes.map((code) => code === oldCode ? newCode : code);
    next.listenerConfig = JSON.stringify(listener);
  }
  return next;
}

function parseJsonSilently(value: string | undefined): Record<string, any> | undefined {
  try {
    const parsed = value ? JSON.parse(value) : undefined;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : undefined;
  } catch { return undefined; }
}

function renameSelected(event: Event): void {
  const oldCode = selectedCode.value;
  const newCode = (event.target as HTMLInputElement).value.trim();
  if (!newCode || newCode === oldCode || nodes.value.some((node) => node.nodeCode === newCode)) return;
  nodes.value = nodes.value.map((node) => {
    const renamed = node.nodeCode === oldCode ? { ...node, nodeCode: newCode } : node;
    return updateJsonNodeReference(renamed, oldCode, newCode);
  });
  edges.value = edges.value.map((edge) => ({
    ...edge,
    sourceNodeCode: edge.sourceNodeCode === oldCode ? newCode : edge.sourceNodeCode,
    targetNodeCode: edge.targetNodeCode === oldCode ? newCode : edge.targetNodeCode,
  }));
  selectedCode.value = newCode;
  if (connectionSource.value === oldCode) connectionSource.value = newCode;
  if (selectedNode.value) loadNodeEditor(selectedNode.value);
}

function renameSelectedEdge(event: Event): void {
  const oldCode = selectedCode.value;
  const newCode = (event.target as HTMLInputElement).value.trim();
  if (!newCode || newCode === oldCode || edges.value.some((edge) => edge.edgeCode === newCode)) return;
  edges.value = edges.value.map((edge) => edge.edgeCode === oldCode ? { ...edge, edgeCode: newCode } : edge);
  selectedCode.value = newCode;
}

function removeSelected(): void {
  if (selectedType.value === "edge") {
    edges.value = edges.value.filter((edge) => edge.edgeCode !== selectedCode.value);
  } else if (selectedType.value === "node") {
    const code = selectedCode.value;
    nodes.value = nodes.value.filter((node) => node.nodeCode !== code).map((node) => {
      const next = { ...node };
      if (next.pairedGatewayCode === code) next.pairedGatewayCode = "";
      const timeout = parseJsonSilently(next.timeoutConfig);
      if (timeout?.targetNodeCode === code) {
        delete timeout.targetNodeCode;
        next.timeoutConfig = JSON.stringify(timeout);
      }
      const listener = parseJsonSilently(next.listenerConfig);
      const reject = (listener?.taskActionRules as Record<string, any> | undefined)?.reject;
      if (Array.isArray(reject?.targetNodeCodes)) {
        reject.targetNodeCodes = reject.targetNodeCodes.filter((target: unknown) => target !== code);
        next.listenerConfig = JSON.stringify(listener);
      }
      return next;
    });
    edges.value = edges.value.filter((edge) => edge.sourceNodeCode !== code && edge.targetNodeCode !== code);
  }
  selectedType.value = "";
  selectedCode.value = "";
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === "Escape" && connectionMode.value) {
    connectionSource.value = "";
    connectionMode.value = false;
    return;
  }
  if (event.key !== "Delete" && event.key !== "Backspace") return;
  const tag = (event.target as HTMLElement | null)?.tagName;
  if (["INPUT", "TEXTAREA", "SELECT"].includes(tag ?? "")) return;
  event.preventDefault();
  removeSelected();
}

function autoLayout(): void {
  nodes.value = nodes.value.map((node, index) => ({
    ...node,
    positionX: 70 + index * 180,
    positionY: index % 2 === 0 ? 150 : 240,
    sortOrder: index + 1,
  }));
  nextTick(() => viewport.fitView(graphBounds.value));
}

function fitView(): void { viewport.fitView(graphBounds.value); }
function edgeLine(item: typeof visibleEdges.value[number]) {
  return {
    x1: (item.source.positionX ?? 0) + nodeWidth,
    y1: (item.source.positionY ?? 0) + nodeHeight / 2,
    x2: item.target.positionX ?? 0,
    y2: (item.target.positionY ?? 0) + nodeHeight / 2,
  };
}

function roleLabel(role: ProcessDefinitionRoleOption): string { return role.roleName || role.roleCode; }
function userLabel(user: ProcessDefinitionUserOption): string {
  return `${user.userName || user.userId}${user.departmentName ? `（${user.departmentName}）` : ""}`;
}
function departmentLabel(department: ProcessDefinitionDepartmentOption): string {
  return department.departmentName || department.departmentId;
}

onMounted(() => nextTick(fitView));
</script>

<template>
  <div class="designer" tabindex="0" @keydown="handleKeydown">
    <div class="toolbar">
      <button v-for="type in ['START','USER_TASK','EXCLUSIVE_GATEWAY','PARALLEL_SPLIT_GATEWAY','PARALLEL_JOIN_GATEWAY','END']"
              :key="type" type="button" @click="addNode(type)">+ {{ type }}</button>
      <button type="button" :class="{ active: connectionMode }" @click="toggleConnection">
        {{ connectionMode ? '结束连线' : '开始连线' }}
      </button>
      <button type="button" @click="autoLayout">自动布局</button>
      <button type="button" class="danger" :disabled="!selectedType" @click="removeSelected">删除选中项</button>
      <span class="toolbar-spacer"></span>
      <button type="button" aria-label="缩小" @click="viewport.zoomOut">−</button>
      <span class="zoom-label">{{ viewport.zoomPercent.value }}</span>
      <button type="button" aria-label="放大" @click="viewport.zoomIn">＋</button>
      <button type="button" @click="fitView">适应画布</button>
      <button type="button" @click="viewport.resetView">重置</button>
    </div>
    <p v-if="connectionMode" class="connection-hint">
      {{ connectionSource ? `已选择来源节点 ${connectionSource}，请点击目标节点` : '请依次点击来源节点和目标节点；按 Esc 退出' }}
    </p>
    <div class="canvas">
      <svg :ref="viewport.svgRef" :viewBox="`0 0 ${viewportWidth} ${viewportHeight}`"
           @pointerdown="viewport.beginPan" @pointermove="movePointer" @pointerup="finishPointer"
           @pointercancel="finishPointer" @wheel.prevent="viewport.wheelZoom">
        <defs><marker id="admin-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0 0 L8 4 L0 8 Z" /></marker></defs>
        <g :transform="viewport.transform.value">
          <template v-for="item in visibleEdges" :key="item.edge.edgeCode">
            <line v-bind="edgeLine(item)" class="edge-hit" data-graph-interactive @pointerdown.stop="selectEdge(item.edge)" />
            <line v-bind="edgeLine(item)" :class="['edge-line', { selected: selectedType === 'edge' && selectedCode === item.edge.edgeCode }]"
                  marker-end="url(#admin-arrow)" data-graph-interactive @pointerdown.stop="selectEdge(item.edge)" />
          </template>
          <g v-for="node in nodes" :key="node.nodeCode"
             :class="['designer-node', { selected: selectedType === 'node' && node.nodeCode === selectedCode, connectionSource: node.nodeCode === connectionSource }]"
             data-graph-interactive @pointerdown.stop="beginNodeDrag($event, node)" @click.stop="handleNodeClick(node)">
            <rect :x="node.positionX ?? 0" :y="node.positionY ?? 0" :width="nodeWidth" :height="nodeHeight" rx="7" />
            <text :x="(node.positionX ?? 0) + nodeWidth / 2" :y="(node.positionY ?? 0) + 19">{{ node.nodeName }}</text>
            <text class="node-type" :x="(node.positionX ?? 0) + nodeWidth / 2" :y="(node.positionY ?? 0) + 35">{{ node.nodeType }}</text>
          </g>
        </g>
      </svg>
    </div>

    <div v-if="selectedNode" class="property-panel">
      <div class="panel-heading"><h4>节点配置：{{ selectedNode.nodeCode }}</h4><button class="danger" @click="removeSelected">删除节点</button></div>
      <label>节点编码<input :value="selectedNode.nodeCode" @change="renameSelected" /></label>
      <label>节点名称<input v-model="selectedNode.nodeName" /></label>
      <label>节点类型<select v-model="selectedNode.nodeType"><option v-for="type in ['START','USER_TASK','EXCLUSIVE_GATEWAY','PARALLEL_SPLIT_GATEWAY','PARALLEL_JOIN_GATEWAY','END']" :key="type">{{ type }}</option></select></label>
      <label v-if="selectedNode.nodeType.includes('PARALLEL')">配对网关<select v-model="selectedNode.pairedGatewayCode"><option value="">无</option><option v-for="node in nodes.filter(item => item.nodeCode !== selectedNode?.nodeCode && item.nodeType.includes('PARALLEL'))" :key="node.nodeCode" :value="node.nodeCode">{{ node.nodeName }} / {{ node.nodeCode }}</option></select></label>

      <template v-if="selectedNode.nodeType === 'USER_TASK'">
        <label>审批来源<select v-model="selectedNode.approverRuleType" @change="syncApprover"><option value="USER">指定用户</option><option value="STARTER">发起人</option><option value="DEPARTMENT">指定部门</option><option value="ROLE">指定角色</option><option value="ROLE_IN_DEPARTMENT">部门角色</option><option value="APPROVER_EXPRESSION">表达式</option></select></label>
        <label v-if="selectedNode.approverRuleType === 'USER'">审批人<select v-model="editor.selectedApproverIds" multiple @change="syncApprover"><option v-for="user in props.options.users" :key="user.userId" :value="user.userId">{{ userLabel(user) }}</option></select></label>
        <label v-if="selectedNode.approverRuleType === 'DEPARTMENT'">指定部门<select v-model="editor.selectedDepartmentId" @change="syncApprover"><option value="">请选择</option><option v-for="department in props.options.departments" :key="department.departmentId" :value="department.departmentId">{{ departmentLabel(department) }}</option></select></label>
        <label v-if="selectedNode.approverRuleType === 'ROLE'">指定角色<select v-model="editor.selectedRoleCode" @change="syncApprover"><option value="">请选择</option><option v-for="role in props.options.roles" :key="role.roleCode" :value="role.roleCode">{{ roleLabel(role) }}</option></select></label>
        <template v-if="selectedNode.approverRuleType === 'ROLE_IN_DEPARTMENT'">
          <label>指定角色<select v-model="editor.selectedRoleCode" @change="syncApprover"><option value="">请选择</option><option v-for="role in props.options.roles" :key="role.roleCode" :value="role.roleCode">{{ roleLabel(role) }}</option></select></label>
          <label>部门来源<select v-model="editor.selectedRoleDepartmentMode" @change="syncApprover"><option value="STARTER">发起人部门</option><option value="FIXED">指定部门</option></select></label>
          <label v-if="editor.selectedRoleDepartmentMode === 'FIXED'">指定部门<select v-model="editor.selectedRoleDepartmentId" @change="syncApprover"><option value="">请选择</option><option v-for="department in props.options.departments" :key="department.departmentId" :value="department.departmentId">{{ departmentLabel(department) }}</option></select></label>
        </template>
        <label v-if="selectedNode.approverRuleType === 'APPROVER_EXPRESSION'">审批人表达式<input v-model="editor.approverExpression" placeholder="departmentManager(starterDeptId)" @change="syncApprover" /></label>
        <label>多人模式<select v-model="selectedNode.multiInstanceMode"><option value="SINGLE">单人</option><option value="OR_SIGN">或签</option><option value="COUNTERSIGN">会签</option></select></label>

        <fieldset><legend>超时与提醒</legend>
          <label class="checkbox"><input v-model="editor.timeoutEnabled" type="checkbox" @change="syncTimeout" />启用超时</label>
          <label>超时时长（分钟）<input v-model.number="editor.timeoutDurationMinutes" type="number" min="0" :disabled="!editor.timeoutEnabled" @change="syncTimeout" /></label>
          <label>超时动作<select v-model="editor.timeoutAction" :disabled="!editor.timeoutEnabled" @change="syncTimeout"><option value="REMIND">提醒</option><option value="ALERT">告警</option><option value="JUMP">跳转</option><option value="TERMINATE">终止</option><option value="FORCE_COMPLETE">强制完成</option></select></label>
          <label>告警级别<select v-model="editor.timeoutSeverity" :disabled="!editor.timeoutEnabled" @change="syncTimeout"><option value="LOW">LOW</option><option value="MEDIUM">MEDIUM</option><option value="HIGH">HIGH</option></select></label>
          <label v-if="editor.timeoutAction === 'JUMP'">跳转目标<select v-model="editor.timeoutTargetNodeCode" :disabled="!editor.timeoutEnabled" @change="syncTimeout"><option value="">请选择</option><option v-for="node in userTaskNodes.filter(item => item.nodeCode !== selectedNode?.nodeCode)" :key="node.nodeCode" :value="node.nodeCode">{{ node.nodeName }}</option></select></label>
          <label class="checkbox"><input v-model="editor.reminderEnabled" type="checkbox" @change="syncReminder" />启用提醒</label>
          <label>最大提醒次数<input v-model.number="editor.reminderMaxCount" type="number" min="0" :disabled="!editor.reminderEnabled" @change="syncReminder" /></label>
          <label class="wide">提醒文案<input v-model="editor.reminderMessageTemplate" :disabled="!editor.reminderEnabled" @change="syncReminder" /></label>
        </fieldset>

        <fieldset><legend>驳回与直送</legend>
          <label class="checkbox"><input v-model="editor.rejectEnabled" type="checkbox" @change="syncListener" />启用驳回</label>
          <label v-if="editor.rejectEnabled">允许驳回节点<select v-model="editor.rejectTargetNodeCodes" multiple @change="syncListener"><option v-for="node in userTaskNodes.filter(item => item.nodeCode !== selectedNode?.nodeCode)" :key="node.nodeCode" :value="node.nodeCode">{{ node.nodeName }}</option></select></label>
          <label class="checkbox"><input v-model="editor.directSendEnabled" type="checkbox" @change="syncListener" />启用直送（REJECT_SOURCE）</label>
        </fieldset>

        <details class="advanced wide"><summary>高级 JSON 配置</summary>
          <label>审批人配置 JSON<textarea v-model="selectedNode.approverRuleConfig" rows="3" @change="reloadAdvancedJson" /></label>
          <label>监听器配置 JSON<textarea v-model="selectedNode.listenerConfig" rows="5" @change="reloadAdvancedJson" /></label>
          <label>超时配置 JSON<textarea v-model="selectedNode.timeoutConfig" rows="3" @change="reloadAdvancedJson" /></label>
          <label>提醒配置 JSON<textarea v-model="selectedNode.reminderConfig" rows="3" @change="reloadAdvancedJson" /></label>
        </details>
        <p v-if="configError" class="field-error wide">{{ configError }}</p>
      </template>
    </div>

    <div v-else-if="selectedEdge" class="property-panel">
      <div class="panel-heading"><h4>连线配置：{{ selectedEdge.edgeCode }}</h4><button class="danger" @click="removeSelected">删除连线</button></div>
      <label>连线编码<input :value="selectedEdge.edgeCode" @change="renameSelectedEdge" /></label>
      <label>来源节点<select v-model="selectedEdge.sourceNodeCode"><option v-for="node in nodes" :key="node.nodeCode" :value="node.nodeCode">{{ node.nodeName }}</option></select></label>
      <label>目标节点<select v-model="selectedEdge.targetNodeCode"><option v-for="node in nodes" :key="node.nodeCode" :value="node.nodeCode">{{ node.nodeName }}</option></select></label>
      <label>条件表达式<input v-model="selectedEdge.conditionExpression" placeholder="仅条件网关非默认出线填写" /></label>
      <label class="checkbox"><input v-model="selectedEdge.defaultEdge" type="checkbox" />默认出线</label>
    </div>
    <p v-else class="empty-properties">选中节点或连线后维护属性。</p>
  </div>
</template>

<style scoped>
.designer { display: grid; gap: 12px; outline: none; }
.toolbar { display: flex; flex-wrap: wrap; gap: 7px; align-items: center; }
.toolbar-spacer { flex: 1; }.zoom-label { min-width: 46px; color: #64748b; font-size: 12px; text-align: center; }
button { border: 1px solid #cbd5e1; border-radius: 5px; padding: 7px 10px; background: #fff; cursor: pointer; }
button.active { color: #fff; border-color: #0f766e; background: #0f766e; } button:disabled { opacity: .45; cursor: not-allowed; }.danger { color: #b91c1c; }
.connection-hint { margin: 0; padding: 8px 10px; border-radius: 5px; color: #115e59; background: #ecfdf5; font-size: 13px; }
.canvas { overflow: hidden; border: 1px solid #cbd5e1; border-radius: 7px; background: #f8fafc; }
svg { display: block; width: 100%; height: 420px; cursor: grab; touch-action: none; } svg:active { cursor: grabbing; }
.edge-line { stroke: #94a3b8; stroke-width: 2; }.edge-line.selected { stroke: #0f766e; stroke-width: 3.5; }.edge-hit { stroke: transparent; stroke-width: 14; cursor: pointer; }
marker path { fill: #94a3b8; }.designer-node { cursor: move; }.designer-node rect { fill: #fff; stroke: #64748b; stroke-width: 1.5; }
.designer-node.selected rect { fill: #ecfeff; stroke: #0f766e; stroke-width: 2.5; }.designer-node.connectionSource rect { fill: #fef3c7; stroke: #d97706; }
text { text-anchor: middle; fill: #0f172a; font-size: 12px; pointer-events: none; }.node-type { fill: #64748b; font-size: 9px; }
.property-panel { display: grid; grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: 10px; padding: 14px; border: 1px solid #dbe4ee; border-radius: 7px; }
.panel-heading { grid-column: 1 / -1; display: flex; align-items: center; justify-content: space-between; gap: 12px; }.panel-heading h4 { margin: 0; }
label { display: grid; gap: 4px; color: #475569; font-size: 12px; } input, select, textarea { min-height: 34px; border: 1px solid #cbd5e1; border-radius: 5px; padding: 6px 8px; font: inherit; }
select[multiple] { min-height: 90px; }.checkbox { display: flex; align-items: center; gap: 7px; }.checkbox input { min-height: auto; }
fieldset { grid-column: 1 / -1; display: grid; grid-template-columns: repeat(3, minmax(160px, 1fr)); gap: 10px; border: 1px solid #dbe4ee; border-radius: 6px; } legend { color: #334155; font-weight: 700; }
.wide, .advanced { grid-column: 1 / -1; }.advanced { padding: 8px 0; }.advanced summary { cursor: pointer; color: #475569; font-weight: 700; }.advanced label { margin-top: 9px; }
.field-error { margin: 0; color: #b91c1c; }.empty-properties { margin: 0; color: #64748b; }
@media(max-width:900px){.property-panel,fieldset{grid-template-columns:1fr}.toolbar-spacer{display:none}}
</style>
