<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  copyDefinition, createAttachmentTemplate, createDefinition, deleteDefinition,
  fetchAttachmentTemplates, fetchDefinition, fetchDefinitions, fetchProcessDefinitionOptions, operateDefinition,
  saveDefinitionGraph, validateDefinition,
} from "../../api/admin";
import ProcessGraphDesigner from "../../components/admin/ProcessGraphDesigner.vue";
import type {
  AttachmentConfig, AttachmentTemplate, ProcessDefinition, ProcessDefinitionDetail,
  ProcessEdge, ProcessFormField, ProcessNode, ValidationResult,
  ProcessDefinitionOptions,
} from "../../types/admin";
import { createIdempotencyKey } from "../../utils/idempotency";
import { formatDateTime } from "../../utils/format";

interface DefinitionDraft {
  id?: string;
  processCode: string;
  processName: string;
  systemCode: string;
  remark: string;
  nodes: ProcessNode[];
  edges: ProcessEdge[];
  formFields: ProcessFormField[];
  attachmentConfigs: AttachmentConfig[];
}

const query = reactive({ pageNo: 1, pageSize: 10, processCode: "", processName: "", systemCode: "", definitionStatus: "", activationStatus: "" });
const rows = ref<ProcessDefinition[]>([]);
const total = ref(0);
const loading = ref(false);
const editorOpen = ref(false);
const saving = ref(false);
const tab = ref("basic");
const validation = ref<ValidationResult>();
const templates = ref<AttachmentTemplate[]>([]);
const definitionOptions = ref<ProcessDefinitionOptions>({ users: [], departments: [], roles: [] });
const draft = reactive<DefinitionDraft>(emptyDraft());
const templateDraft = reactive({ attachmentCode: "", attachmentName: "", description: "", allowedExtensions: "pdf,jpg,png", maxSizeBytes: 10485760 });

function emptyDraft(): DefinitionDraft {
  return {
    id: undefined,
    processCode: `entry_application_${Date.now()}`,
    processName: "入金申请测试流程",
    systemCode: "business-base",
    remark: "",
    nodes: [
      { nodeCode: "start", nodeName: "开始", nodeType: "START", positionX: 80, positionY: 160, sortOrder: 1 },
      { nodeCode: "apply", nodeName: "申请", nodeType: "USER_TASK", approverRuleType: "STARTER", approverRuleConfig: "{}", multiInstanceMode: "SINGLE", positionX: 260, positionY: 160, sortOrder: 2 },
      { nodeCode: "end", nodeName: "结束", nodeType: "END", positionX: 520, positionY: 160, sortOrder: 3 },
    ],
    edges: [
      { edgeCode: "edge_start_apply", sourceNodeCode: "start", targetNodeCode: "apply", defaultEdge: false, sortOrder: 1 },
      { edgeCode: "edge_apply_end", sourceNodeCode: "apply", targetNodeCode: "end", defaultEdge: false, sortOrder: 2 },
    ],
    formFields: [
      { fieldCode: "amount", fieldName: "金额", fieldType: "number", controlType: "number", required: true, sortOrder: 1 },
    ],
    attachmentConfigs: [],
  };
}

function definitionId(row: ProcessDefinition): string { return row.id || row.definitionId || ""; }

async function load(): Promise<void> {
  loading.value = true;
  try {
    const result = await fetchDefinitions(query);
    rows.value = result.records ?? [];
    total.value = result.total ?? 0;
  } catch (error) { ElMessage.error(message(error)); }
  finally { loading.value = false; }
}

function resetQuery(): void {
  Object.assign(query, { pageNo: 1, processCode: "", processName: "", systemCode: "", definitionStatus: "", activationStatus: "" });
  void load();
}

function newDefinition(): void {
  Object.assign(draft, emptyDraft());
  validation.value = undefined;
  tab.value = "basic";
  editorOpen.value = true;
  void loadEditorOptions();
}

async function edit(row: ProcessDefinition): Promise<void> {
  try {
    const detail = await fetchDefinition(definitionId(row));
    fillDraft(detail);
    validation.value = undefined;
    tab.value = "graph";
    editorOpen.value = true;
    await loadEditorOptions();
  } catch (error) { ElMessage.error(message(error)); }
}

function fillDraft(detail: ProcessDefinitionDetail): void {
  Object.assign(draft, {
    id: definitionId(detail), processCode: detail.processCode, processName: detail.processName,
    systemCode: detail.systemCode ?? "", remark: "",
    nodes: structuredClone(detail.nodes ?? []), edges: structuredClone(detail.edges ?? []),
    formFields: structuredClone(detail.formFields ?? []),
    attachmentConfigs: (detail.attachmentTemplates ?? []).map((item, index) => ({
      configId: item.id, attachmentConfigId: item.attachmentConfigId,
      definitionId: item.definitionId ?? detail.id, attachmentTemplateId: item.attachmentTemplateId,
      attachmentCode: item.attachmentCode, required: item.required ?? false, minCount: item.minCount ?? 0,
      maxCount: item.maxCount ?? 1, applicableNodeCodes: item.applicableNodeCodes ?? [], sortOrder: item.sortOrder ?? index + 1,
    })),
  });
}

async function loadTemplates(): Promise<void> {
  try { templates.value = await fetchAttachmentTemplates({ templateStatus: "ENABLED" }); }
  catch (error) { ElMessage.error(message(error)); }
}

async function loadEditorOptions(): Promise<void> {
  await Promise.all([
    loadTemplates(),
    fetchProcessDefinitionOptions()
      .then((result) => { definitionOptions.value = result; })
      .catch((error) => ElMessage.warning(`组织候选加载失败，可继续使用高级 JSON：${message(error)}`)),
  ]);
}

function addField(): void {
  const index = draft.formFields.length + 1;
  draft.formFields.push({ fieldCode: `field_${index}`, fieldName: `字段 ${index}`, fieldType: "string", controlType: "input", required: false, validationRule: "", defaultValue: "", sortOrder: index });
}

function addAttachment(): void {
  const first = templates.value[0];
  draft.attachmentConfigs.push({ attachmentTemplateId: first?.attachmentTemplateId ?? "", attachmentCode: first?.attachmentCode ?? "", required: false, minCount: 0, maxCount: 1, applicableNodeCodes: [], sortOrder: draft.attachmentConfigs.length + 1 });
}

function templateChanged(item: AttachmentConfig): void {
  const found = templates.value.find((template) => template.attachmentTemplateId === item.attachmentTemplateId);
  if (found) item.attachmentCode = found.attachmentCode;
}

async function addTemplate(): Promise<void> {
  if (!templateDraft.attachmentCode.trim() || !templateDraft.attachmentName.trim()) {
    ElMessage.warning("请填写附件编码和名称"); return;
  }
  try {
    await createAttachmentTemplate({
      attachmentCode: templateDraft.attachmentCode.trim(), attachmentName: templateDraft.attachmentName.trim(),
      description: templateDraft.description, allowedExtensions: templateDraft.allowedExtensions.split(",").map((value) => value.trim()).filter(Boolean),
      maxSizeBytes: templateDraft.maxSizeBytes, templateStatus: "ENABLED",
    });
    Object.assign(templateDraft, { attachmentCode: "", attachmentName: "", description: "", allowedExtensions: "pdf,jpg,png", maxSizeBytes: 10485760 });
    await loadTemplates(); ElMessage.success("附件模板版本已创建");
  } catch (error) { ElMessage.error(message(error)); }
}

function validateJsonConfigs(): boolean {
  for (const node of draft.nodes) {
    for (const [label, value] of [["审批人配置", node.approverRuleConfig], ["监听器配置", node.listenerConfig], ["超时配置", node.timeoutConfig], ["提醒配置", node.reminderConfig]] as Array<[string, string | undefined]>) {
      if (!value?.trim()) continue;
      try { JSON.parse(value); } catch { ElMessage.error(`${node.nodeCode} 的${label}不是合法 JSON`); return false; }
    }
  }
  for (const field of draft.formFields) {
    if (!field.validationRule?.trim()) continue;
    try { JSON.parse(field.validationRule); } catch { ElMessage.error(`${field.fieldCode} 的校验规则不是合法 JSON`); return false; }
  }
  return true;
}

async function save(): Promise<void> {
  if (!draft.processCode.trim() || !draft.processName.trim() || !draft.systemCode.trim()) { ElMessage.warning("请填写流程编码、名称和所属系统"); return; }
  if (!validateJsonConfigs()) return;
  saving.value = true;
  try {
    let id = draft.id;
    if (!id) {
      const created = await createDefinition({ operationId: createIdempotencyKey("admin-create-definition"), processCode: draft.processCode, processName: draft.processName, systemCode: draft.systemCode, remark: draft.remark });
      id = definitionId(created); draft.id = id;
    }
    await saveDefinitionGraph(id, {
      operationId: createIdempotencyKey("admin-save-graph"),
      nodes: draft.nodes.map((item, index) => ({ ...item, sortOrder: index + 1 })),
      edges: draft.edges.map((item, index) => ({ ...item, sortOrder: index + 1 })),
      formFields: draft.formFields.map((item, index) => ({ ...item, sortOrder: index + 1 })),
      attachmentConfigs: draft.attachmentConfigs.map((item, index) => ({ ...item, sortOrder: index + 1 })),
    });
    validation.value = await validateDefinition(id);
    await load();
    ElMessage.success(validation.value.valid ? "定义已保存，发布校验通过" : "定义已保存，但发布校验未通过");
  } catch (error) { ElMessage.error(message(error)); }
  finally { saving.value = false; }
}

async function lifecycle(row: ProcessDefinition, operation: "publish" | "activate" | "deactivate" | "archive"): Promise<void> {
  try {
    await operateDefinition(definitionId(row), operation, createIdempotencyKey(`admin-${operation}`));
    ElMessage.success("操作成功"); await load();
  } catch (error) { ElMessage.error(message(error)); }
}

async function copyRow(row: ProcessDefinition): Promise<void> {
  try {
    const code = await ElMessageBox.prompt("请输入新流程编码；留空则沿用原编码并创建新版本", "复制流程定义", { inputValue: row.processCode }).then((result) => result.value);
    await copyDefinition(definitionId(row), { operationId: createIdempotencyKey("admin-copy-definition"), processCode: code, processName: `${row.processName} 副本`, systemCode: row.systemCode });
    ElMessage.success("复制成功"); await load();
  } catch (error) { if (error !== "cancel" && error !== "close") ElMessage.error(message(error)); }
}

async function removeRow(row: ProcessDefinition): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除流程定义 ${row.processCode} v${row.version ?? "-"}？`, "危险操作", { type: "warning", confirmButtonText: "确认删除" });
    await deleteDefinition(definitionId(row), createIdempotencyKey("admin-delete-definition"));
    ElMessage.success("已删除"); await load();
  } catch (error) { if (error !== "cancel" && error !== "close") ElMessage.error(message(error)); }
}

function message(error: unknown): string { return error instanceof Error ? error.message : "请求失败"; }
onMounted(load);
</script>

<template>
  <section class="page-surface admin-page">
    <header class="page-header"><div><p class="eyebrow">管理员控制台</p><h2>流程定义</h2></div><button class="primary" type="button" @click="newDefinition">新建流程定义</button></header>
    <form class="filters" @submit.prevent="query.pageNo = 1; load()">
      <label>流程编码<input v-model="query.processCode" /></label><label>流程名称<input v-model="query.processName" /></label><label>所属系统<input v-model="query.systemCode" /></label>
      <label>发布状态<select v-model="query.definitionStatus"><option value="">全部</option><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option><option value="ARCHIVED">已归档</option></select></label>
      <label>激活状态<select v-model="query.activationStatus"><option value="">全部</option><option value="ACTIVE">已激活</option><option value="INACTIVE">未激活</option></select></label>
      <div class="filter-actions"><button class="primary" type="submit">查询</button><button type="button" @click="resetQuery">重置</button></div>
    </form>
    <div class="table-wrap"><table><thead><tr><th>编码/名称</th><th>系统</th><th>版本</th><th>发布状态</th><th>激活状态</th><th>更新时间</th><th>操作</th></tr></thead>
      <tbody><tr v-if="!loading && rows.length === 0"><td colspan="7" class="empty">暂无流程定义</td></tr><tr v-for="row in rows" :key="definitionId(row)">
        <td><strong>{{ row.processCode }}</strong><small>{{ row.processName }}</small></td><td>{{ row.systemCode || '-' }}</td><td>{{ row.version ?? '-' }}</td><td>{{ row.definitionStatus }}</td><td>{{ row.activationStatus }}</td><td>{{ formatDateTime(row.updatedAt) }}</td>
        <td class="actions"><button @click="edit(row)">编辑/流程图</button><button :disabled="row.definitionStatus !== 'DRAFT'" @click="lifecycle(row,'publish')">发布</button><button :disabled="row.definitionStatus !== 'PUBLISHED' || row.activationStatus === 'ACTIVE'" @click="lifecycle(row,'activate')">激活</button><button @click="copyRow(row)">复制</button><button :disabled="row.activationStatus !== 'ACTIVE'" @click="lifecycle(row,'deactivate')">停用</button><button :disabled="row.activationStatus === 'ACTIVE' || row.definitionStatus === 'ARCHIVED'" @click="lifecycle(row,'archive')">归档</button><button class="danger" :disabled="row.activationStatus === 'ACTIVE'" @click="removeRow(row)">删除</button></td>
      </tr></tbody></table></div>
    <footer class="pagination"><span>共 {{ total }} 条</span><button :disabled="query.pageNo <= 1" @click="query.pageNo--; load()">上一页</button><span>第 {{ query.pageNo }} 页</span><button :disabled="rows.length < query.pageSize" @click="query.pageNo++; load()">下一页</button></footer>

    <div v-if="editorOpen" class="modal-backdrop" @click.self="editorOpen = false"><section class="editor-modal" role="dialog" aria-modal="true">
      <header class="modal-header"><h3>{{ draft.id ? `编辑 ${draft.processCode}` : '新建流程定义' }}</h3><button @click="editorOpen = false">关闭</button></header>
      <nav class="tabs"><button v-for="item in [['basic','基础信息'],['graph','流程图'],['fields','表单字段'],['attachments','附件配置']]" :key="item[0]" :class="{ active: tab === item[0] }" @click="tab = item[0]">{{ item[1] }}</button></nav>
      <div v-show="tab === 'basic'" class="editor-section form-grid"><label>流程编码<input v-model="draft.processCode" :disabled="!!draft.id" /></label><label>流程名称<input v-model="draft.processName" :disabled="!!draft.id" /></label><label>所属系统<input v-model="draft.systemCode" :disabled="!!draft.id" /></label><label class="wide">备注<textarea v-model="draft.remark" rows="3" :disabled="!!draft.id" /></label></div>
      <div v-show="tab === 'graph'" class="editor-section"><ProcessGraphDesigner v-model:nodes="draft.nodes" v-model:edges="draft.edges" :options="definitionOptions" /></div>
      <div v-show="tab === 'fields'" class="editor-section"><button class="primary" @click="addField">添加字段</button><table><thead><tr><th>编码</th><th>名称</th><th>类型</th><th>控件</th><th>必填</th><th>默认值</th><th>校验规则 JSON</th><th></th></tr></thead><tbody><tr v-for="(field,index) in draft.formFields" :key="index"><td><input v-model="field.fieldCode" /></td><td><input v-model="field.fieldName" /></td><td><select v-model="field.fieldType"><option v-for="type in ['string','number','date','boolean','select']" :key="type">{{ type }}</option></select></td><td><select v-model="field.controlType"><option v-for="type in ['input','textarea','number','datePicker','select','checkbox']" :key="type">{{ type }}</option></select></td><td><input v-model="field.required" type="checkbox" /></td><td><input v-model="field.defaultValue" /></td><td><textarea v-model="field.validationRule" rows="2" /></td><td><button class="danger" @click="draft.formFields.splice(index,1)">删除</button></td></tr></tbody></table></div>
      <div v-show="tab === 'attachments'" class="editor-section"><div class="template-create"><h4>创建附件模板版本</h4><input v-model="templateDraft.attachmentCode" placeholder="附件编码" /><input v-model="templateDraft.attachmentName" placeholder="附件名称" /><input v-model="templateDraft.allowedExtensions" placeholder="扩展名，逗号分隔" /><input v-model.number="templateDraft.maxSizeBytes" type="number" placeholder="最大字节数" /><button @click="addTemplate">创建模板</button></div><button class="primary" @click="addAttachment">添加附件配置</button><table><thead><tr><th>模板</th><th>必填</th><th>最少</th><th>最多</th><th>适用节点</th><th></th></tr></thead><tbody><tr v-for="(item,index) in draft.attachmentConfigs" :key="index"><td><select v-model="item.attachmentTemplateId" @change="templateChanged(item)"><option value="">请选择</option><option v-for="template in templates" :key="template.attachmentTemplateId" :value="template.attachmentTemplateId">{{ template.attachmentName || template.attachmentCode }} v{{ template.templateVersion }}</option></select></td><td><input v-model="item.required" type="checkbox" /></td><td><input v-model.number="item.minCount" type="number" min="0" /></td><td><input v-model.number="item.maxCount" type="number" min="1" /></td><td><select v-model="item.applicableNodeCodes" multiple><option v-for="node in draft.nodes" :key="node.nodeCode" :value="node.nodeCode">{{ node.nodeName }}</option></select></td><td><button class="danger" @click="draft.attachmentConfigs.splice(index,1)">删除</button></td></tr></tbody></table></div>
      <section v-if="validation" :class="['validation', validation.valid ? 'valid' : 'invalid']"><strong>{{ validation.valid ? '发布校验通过' : `发布校验发现 ${validation.issues.length} 个问题` }}</strong><ul v-if="!validation.valid"><li v-for="issue in validation.issues" :key="`${issue.code}-${issue.nodeCode}-${issue.edgeCode}`">{{ issue.code }}：{{ issue.message }} <span v-if="issue.nodeCode">（节点 {{ issue.nodeCode }}）</span></li></ul></section>
      <footer class="modal-footer"><button @click="editorOpen = false">取消</button><button class="primary" :disabled="saving" @click="save">{{ saving ? '保存中...' : '保存并校验' }}</button></footer>
    </section></div>
  </section>
</template>

<style scoped>
.admin-page { display: grid; gap: 16px; }
.page-header,.modal-header,.modal-footer,.pagination { display:flex; align-items:center; justify-content:space-between; gap:12px; }
h2,h3,h4,p { margin:0; }.eyebrow { margin:0 0 4px;color:#0f766e;font-size:12px;font-weight:800; }
button { min-height:34px;border:1px solid #d8dee8;border-radius:6px;padding:6px 12px;background:#fff;color:#17202a;cursor:pointer;font:inherit; }button:hover:not(:disabled){border-color:#2563eb;color:#2563eb}button:disabled{opacity:.55;cursor:not-allowed}.primary{background:#0f766e;color:#fff;border-color:#0f766e}.primary:hover:not(:disabled){background:#115e59;color:#fff;border-color:#115e59}.danger{color:#b91c1c}
.filters,.form-grid { display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;align-items:end; }.filters label,.form-grid label{display:grid;gap:6px;color:#5d6978;font-size:13px}.filter-actions{display:flex;gap:8px;align-items:end}.wide{grid-column:1/-1}
input,select,textarea{min-height:34px;border:1px solid #d8dee8;border-radius:6px;padding:7px 9px;background:#fff;color:#17202a;font:inherit}textarea{resize:vertical}.table-wrap{overflow:auto;border:1px solid #d8dee8;border-radius:8px;background:#fff}table{width:100%;border-collapse:collapse}.admin-page>.table-wrap table{min-width:1100px;table-layout:fixed}.admin-page>.table-wrap th:last-child,.admin-page>.table-wrap td:last-child{width:390px}th,td{overflow-wrap:anywhere;padding:9px 10px;border-bottom:1px solid #d8dee8;text-align:left;vertical-align:middle}th{height:40px;background:#f8fafc;color:#344054;font-size:12px;font-weight:700}.admin-page>.table-wrap th{position:sticky;top:0;z-index:1}td{color:#17202a;font-size:13px}td small{display:block;margin-top:4px;color:#5d6978;font-size:12px}tbody tr:hover{background:#eef6ff}.actions{display:flex;flex-wrap:wrap;gap:6px}.empty{padding:20px;background:#f8fafc;color:#5d6978;text-align:center}.pagination{justify-content:flex-end;color:#5d6978;font-size:13px}
.modal-backdrop{position:fixed;inset:0;z-index:20;display:grid;place-items:center;padding:22px;background:rgba(15,23,42,.55)}.editor-modal{display:grid;grid-template-rows:auto auto minmax(0,1fr) auto auto;width:min(1180px,96vw);max-height:94vh;border-radius:9px;background:#fff;box-shadow:0 24px 70px rgba(0,0,0,.3)}.modal-header,.modal-footer{padding:14px 18px;border-bottom:1px solid #e2e8f0}.modal-footer{justify-content:flex-end;border-top:1px solid #e2e8f0;border-bottom:0}.tabs{display:flex;gap:4px;padding:10px 18px;border-bottom:1px solid #e2e8f0}.tabs .active{color:#0f766e;border-color:#0f766e;background:#ecfdf5}.editor-section{overflow:auto;padding:16px 18px}.editor-section>table{margin-top:12px}.editor-section table input,.editor-section table select,.editor-section table textarea{min-width:100px;max-width:220px}.template-create{display:grid;grid-template-columns:repeat(4,minmax(130px,1fr)) auto;gap:8px;align-items:end;margin-bottom:12px;padding:12px;background:#f8fafc}.template-create h4{grid-column:1/-1}.validation{margin:0 18px 12px;padding:10px 12px;border-radius:6px}.validation.valid{background:#ecfdf5;color:#166534}.validation.invalid{background:#fff7ed;color:#9a3412}.validation ul{margin:7px 0 0;padding-left:20px}
@media(max-width:900px){.filters,.form-grid{grid-template-columns:1fr}.template-create{grid-template-columns:1fr}.editor-modal{width:98vw}.actions{min-width:220px}}
</style>
