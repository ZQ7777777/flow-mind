<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  deleteInstance, fetchActiveTasks, fetchAdminInstance, fetchAdminInstances,
  fetchDefinition, fetchInstanceTrace, terminateInstance,
} from "../../api/admin";
import ProcessGraph from "../../components/workflow/ProcessGraph.vue";
import type { AdminRecord, ProcessDefinitionDetail, ProcessInstance, ProcessInstanceDetail, TraceType } from "../../types/admin";
import { createIdempotencyKey } from "../../utils/idempotency";
import { formatDateTime, formatUnknown } from "../../utils/format";

const query = reactive({ pageNo: 1, pageSize: 10, processCode: "", instanceStatus: "", instanceTitle: "", businessKey: "", starterUserId: "", currentNodeCode: "", startedFrom: "", startedTo: "" });
const rows = ref<ProcessInstance[]>([]);
const total = ref(0);
const loading = ref(false);
const selected = ref<ProcessInstanceDetail>();
const definition = ref<ProcessDefinitionDetail>();
const activeTasks = ref<AdminRecord[]>([]);
const detailLoading = ref(false);
const traceOpen = ref(false);
const traceType = ref<TraceType>("history-tasks");
const traceRows = ref<AdminRecord[]>([]);
const tracePage = ref(1);
const traceTotal = ref(0);
const traceLoading = ref(false);

const traceTitle: Record<TraceType, string> = {
  "history-tasks": "历史任务", comments: "审批意见", "callback-logs": "回调日志",
  "read-records": "已阅记录", "audit-logs": "审计追溯",
};
const traceColumns: Record<TraceType, Array<[string, string]>> = {
  "history-tasks": [["nodeName","节点"],["assigneeUserName","办理人"],["actionType","动作"],["comment","意见"],["completedAt","完成时间"]],
  comments: [["nodeCode","节点"],["operatorUserName","用户"],["comment","意见"],["createdAt","时间"]],
  "callback-logs": [["eventType","事件"],["actionType","动作"],["callbackStatus","状态"],["retryCount","重试"],["lastError","错误"],["updatedAt","时间"]],
  "read-records": [["userName","阅读用户"],["userId","用户 ID"],["taskId","任务 ID"],["readAt","阅读时间"]],
  "audit-logs": [["targetType","对象"],["actionType","动作"],["operatorUserName","操作人"],["detail","详情"],["createdAt","时间"]],
};
const currentColumns = computed(() => traceColumns[traceType.value]);
const variableRows = computed(() => Object.entries(selected.value?.variables ?? {}));
const canTerminate = computed(() => selected.value?.instanceStatus === "RUNNING");

async function load(): Promise<void> {
  loading.value = true;
  try {
    const result = await fetchAdminInstances(query);
    rows.value = result.records ?? []; total.value = result.total ?? 0;
  } catch (error) { ElMessage.error(message(error)); }
  finally { loading.value = false; }
}

function resetQuery(): void {
  Object.assign(query, { pageNo: 1, processCode: "", instanceStatus: "", instanceTitle: "", businessKey: "", starterUserId: "", currentNodeCode: "", startedFrom: "", startedTo: "" });
  void load();
}

async function selectInstance(row: ProcessInstance): Promise<void> {
  detailLoading.value = true; selected.value = undefined; definition.value = undefined; activeTasks.value = [];
  try {
    const detail = await fetchAdminInstance(row.instanceId);
    const [tasks, graph] = await Promise.all([
      fetchActiveTasks(row.instanceId),
      detail.definitionId ? fetchDefinition(detail.definitionId) : Promise.resolve(undefined),
    ]);
    selected.value = detail; activeTasks.value = tasks; definition.value = graph;
  } catch (error) { ElMessage.error(message(error)); }
  finally { detailLoading.value = false; }
}

async function openTrace(type: TraceType): Promise<void> {
  if (!selected.value) return;
  traceType.value = type; tracePage.value = 1; traceRows.value = []; traceOpen.value = true;
  await loadTrace();
}

async function loadTrace(): Promise<void> {
  if (!selected.value) return;
  traceLoading.value = true;
  try {
    const result = await fetchInstanceTrace(selected.value.instanceId, traceType.value, tracePage.value, 20);
    traceRows.value = result.records ?? []; traceTotal.value = result.total ?? traceRows.value.length;
  } catch (error) { ElMessage.error(message(error)); }
  finally { traceLoading.value = false; }
}

async function terminateSelected(): Promise<void> {
  if (!selected.value) return;
  try {
    const result = await ElMessageBox.prompt("可填写终止原因或处理说明", `终止流程 ${selected.value.instanceId}`, { inputType: "textarea", confirmButtonText: "确认终止", type: "warning" });
    await terminateInstance(selected.value.instanceId, createIdempotencyKey("admin-terminate-instance"), result.value ?? "");
    ElMessage.success("流程已终止"); await load(); await selectInstance(selected.value);
  } catch (error) { if (error !== "cancel" && error !== "close") ElMessage.error(message(error)); }
}

async function deleteSelected(): Promise<void> {
  if (!selected.value) return;
  const id = selected.value.instanceId;
  try {
    const result = await ElMessageBox.prompt(`此操作将删除实例运行数据。请输入实例 ID ${id} 以确认。`, "删除流程实例", { confirmButtonText: "永久删除", type: "error" });
    if (result.value !== id) { ElMessage.warning("实例 ID 不匹配，已取消删除"); return; }
    await deleteInstance(id, createIdempotencyKey("admin-delete-instance"));
    selected.value = undefined; definition.value = undefined; activeTasks.value = [];
    ElMessage.success("实例已删除"); await load();
  } catch (error) { if (error !== "cancel" && error !== "close") ElMessage.error(message(error)); }
}

function cell(row: AdminRecord, key: string): string {
  const value = row[key];
  if (key.endsWith("At") && typeof value === "string") return formatDateTime(value);
  return formatUnknown(value);
}
function message(error: unknown): string { return error instanceof Error ? error.message : "请求失败"; }
onMounted(load);
</script>

<template>
  <section class="page-surface admin-page">
    <header class="page-header"><div><p class="eyebrow">管理员控制台</p><h2>流程实例</h2></div></header>
    <form class="filters" @submit.prevent="query.pageNo = 1; load()">
      <label>流程编码<input v-model="query.processCode" /></label><label>实例标题<input v-model="query.instanceTitle" /></label><label>业务键<input v-model="query.businessKey" /></label><label>发起人 ID<input v-model="query.starterUserId" /></label><label>当前节点<input v-model="query.currentNodeCode" /></label>
      <label>状态<select v-model="query.instanceStatus"><option value="">全部</option><option value="RUNNING">运行中</option><option value="COMPLETED">已完成</option><option value="TERMINATED">已终止</option><option value="CANCELLED">已取消</option></select></label>
      <label>开始时间<input v-model="query.startedFrom" type="datetime-local" /></label><label>结束时间<input v-model="query.startedTo" type="datetime-local" /></label>
      <div class="filter-actions"><button class="primary" type="submit">查询</button><button type="button" @click="resetQuery">重置</button></div>
    </form>
    <div class="table-wrap"><table><thead><tr><th>实例</th><th>流程</th><th>发起人</th><th>当前节点</th><th>状态</th><th>启动时间</th><th></th></tr></thead><tbody>
      <tr v-if="!loading && rows.length === 0"><td colspan="7" class="empty">暂无流程实例</td></tr><tr v-for="row in rows" :key="row.instanceId" :class="{ selected: selected?.instanceId === row.instanceId }"><td><strong>{{ row.instanceTitle }}</strong><small>{{ row.instanceId }}</small></td><td>{{ row.processName || row.processCode }}<small>{{ row.processCode }} v{{ row.version }}</small></td><td>{{ row.starterUserName || row.starterUserId }}</td><td>{{ row.currentNodeCodes?.join(', ') || '-' }}</td><td>{{ row.instanceStatus }}</td><td>{{ formatDateTime(row.startedAt) }}</td><td><button @click="selectInstance(row)">查看详情</button></td></tr>
    </tbody></table></div>
    <footer class="pagination"><span>共 {{ total }} 条</span><button :disabled="query.pageNo <= 1" @click="query.pageNo--; load()">上一页</button><span>第 {{ query.pageNo }} 页</span><button :disabled="rows.length < query.pageSize" @click="query.pageNo++; load()">下一页</button></footer>

    <section v-if="detailLoading" class="detail-card">正在加载实例详情...</section>
    <section v-else-if="selected" class="detail-card">
      <header class="detail-header"><div><p class="eyebrow">{{ selected.processCode }} · {{ selected.instanceStatus }}</p><h3>{{ selected.instanceTitle }}</h3><small>{{ selected.instanceId }}</small></div><div class="danger-actions"><button :disabled="!canTerminate" @click="terminateSelected">终止流程</button><button class="danger" @click="deleteSelected">删除实例</button></div></header>
      <div class="trace-actions"><button v-for="type in (Object.keys(traceTitle) as TraceType[])" :key="type" @click="openTrace(type)">{{ traceTitle[type] }}</button></div>
      <dl class="summary"><div><dt>业务键</dt><dd>{{ selected.businessKey || '-' }}</dd></div><div><dt>发起人</dt><dd>{{ selected.starterUserName || selected.starterUserId }}</dd></div><div><dt>当前节点</dt><dd>{{ selected.currentNodeCodes?.join(', ') || '-' }}</dd></div><div><dt>启动/结束</dt><dd>{{ formatDateTime(selected.startedAt) }} / {{ formatDateTime(selected.endedAt) }}</dd></div></dl>
      <section><h4>流程变量</h4><table><thead><tr><th>变量</th><th>值</th></tr></thead><tbody><tr v-if="variableRows.length === 0"><td colspan="2" class="empty">暂无变量</td></tr><tr v-for="item in variableRows" :key="item[0]"><td>{{ item[0] }}</td><td>{{ formatUnknown(item[1]) }}</td></tr></tbody></table></section>
      <section><h4>活动任务</h4><table><thead><tr><th>节点</th><th>办理人</th><th>候选人</th><th>状态</th><th>创建时间</th></tr></thead><tbody><tr v-if="activeTasks.length === 0"><td colspan="5" class="empty">暂无活动任务</td></tr><tr v-for="task in activeTasks" :key="String(task.taskId)"><td>{{ task.nodeName || task.nodeCode }}</td><td>{{ task.assigneeUserName || task.assigneeUserId || '-' }}</td><td>{{ formatUnknown(task.candidateUserIds) }}</td><td>{{ task.taskStatus }}</td><td>{{ formatDateTime(task.createdAt as string) }}</td></tr></tbody></table></section>
      <ProcessGraph v-if="definition" :nodes="definition.nodes" :edges="definition.edges" :current-node-codes="selected.currentNodeCodes ?? []" />
    </section>

    <div v-if="traceOpen" class="modal-backdrop" @click.self="traceOpen = false"><section class="trace-modal" role="dialog"><header><h3>{{ traceTitle[traceType] }}</h3><button @click="traceOpen = false">关闭</button></header><div class="table-wrap"><table><thead><tr><th v-for="column in currentColumns" :key="column[0]">{{ column[1] }}</th></tr></thead><tbody><tr v-if="!traceLoading && traceRows.length === 0"><td :colspan="currentColumns.length" class="empty">暂无记录</td></tr><tr v-for="(row,index) in traceRows" :key="index"><td v-for="column in currentColumns" :key="column[0]">{{ cell(row,column[0]) }}</td></tr></tbody></table></div><footer class="pagination"><span>共 {{ traceTotal }} 条</span><button :disabled="tracePage <= 1" @click="tracePage--; loadTrace()">上一页</button><span>第 {{ tracePage }} 页</span><button :disabled="traceRows.length < 20" @click="tracePage++; loadTrace()">下一页</button></footer></section></div>
  </section>
</template>

<style scoped>
.admin-page{display:grid;gap:16px}.page-header,.detail-header,.trace-modal>header,.pagination{display:flex;align-items:center;justify-content:space-between;gap:12px}h2,h3,h4,p{margin:0}.eyebrow{color:#0f766e;font-size:12px;font-weight:800}button{border:1px solid #cbd5e1;border-radius:5px;padding:7px 10px;background:#fff;cursor:pointer}button:disabled{opacity:.45;cursor:not-allowed}.primary{background:#0f766e;color:#fff;border-color:#0f766e}.danger,.danger-actions button{color:#b91c1c}
.filters{display:grid;grid-template-columns:repeat(3,minmax(150px,1fr));gap:10px}.filters label{display:grid;gap:4px;color:#475569;font-size:12px}.filter-actions,.trace-actions,.danger-actions{display:flex;gap:8px;align-items:end;flex-wrap:wrap}input,select{min-height:34px;border:1px solid #cbd5e1;border-radius:5px;padding:6px 8px;font:inherit}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse}th,td{padding:9px 8px;border-bottom:1px solid #e2e8f0;text-align:left;vertical-align:top}td small,.detail-header small{display:block;color:#64748b;margin-top:3px}tr.selected{background:#ecfdf5}.empty{text-align:center;color:#64748b;padding:26px}.pagination{justify-content:flex-end}
.detail-card{display:grid;gap:18px;border:1px solid #cbd5e1;border-radius:8px;padding:16px;background:#fff}.summary{display:grid;grid-template-columns:repeat(4,minmax(130px,1fr));gap:12px;margin:0;padding:12px;background:#f8fafc}.summary dt{color:#64748b;font-size:12px}.summary dd{margin:4px 0 0}.modal-backdrop{position:fixed;inset:0;z-index:20;display:grid;place-items:center;padding:24px;background:rgba(15,23,42,.55)}.trace-modal{display:grid;gap:12px;width:min(1000px,94vw);max-height:88vh;overflow:auto;padding:18px;border-radius:9px;background:#fff}.trace-modal>header{position:sticky;top:-18px;padding:4px 0 12px;background:#fff;border-bottom:1px solid #e2e8f0}
@media(max-width:900px){.filters{grid-template-columns:1fr}.summary{grid-template-columns:1fr 1fr}}
</style>
