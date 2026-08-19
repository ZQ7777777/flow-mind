<script setup lang="ts">
import { reactive, watch } from "vue";
import {
  type BusinessRequirement,
  type ReferenceDataSource,
  type RequirementRevision,
} from "@flowmind/agent-contracts";
import ProcessGraphDesigner from "./ProcessGraphDesigner.vue";

const props = defineProps<{ revision: RequirementRevision; disabled?: boolean }>();
const emit = defineEmits<{ save: [requirement: BusinessRequirement] }>();
const draft = reactive<BusinessRequirement>(withFrontendBehavior(clone(props.revision.requirement)));

watch(
  () => props.revision,
  (value) => { Object.assign(draft, withFrontendBehavior(clone(value.requirement))); },
  { deep: true },
);

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}

function withFrontendBehavior(requirement: BusinessRequirement): BusinessRequirement {
  requirement.frontendBehavior ||= {
    sections: [{ sectionCode: "business-info", title: "业务信息", fieldCodes: requirement.formFields.map(({ fieldCode }) => fieldCode), sortOrder: 1 }],
    dataQueries: [], calculations: [], checks: [],
  };
  return requirement;
}

function frontendBehaviorText(): string {
  return JSON.stringify(draft.frontendBehavior, null, 2);
}

function updateFrontendBehavior(value: string): void {
  try {
    draft.frontendBehavior = JSON.parse(value) as NonNullable<BusinessRequirement["frontendBehavior"]>;
  } catch {
    // Keep the last structurally valid value; backend validation remains authoritative.
  }
}

function save(): void {
  emit("save", clone(draft));
}

function addParticipant(): void {
  draft.participants.push({ roleCode: "", roleName: "", responsibility: "" });
}

function addField(): void {
  draft.formFields.push({
    fieldCode: "",
    fieldName: "",
    fieldType: "string",
    controlType: "input",
    required: false,
    validation: {},
    sortOrder: draft.formFields.length + 1,
  });
}

function addAttachment(): void {
  draft.attachments.push({
    attachmentCode: "",
    attachmentName: "",
    allowedExtensions: [],
    maxSizeBytes: 10485760,
    required: false,
    minCount: 0,
    maxCount: 1,
    applicableNodeCodes: [],
    sortOrder: draft.attachments.length + 1,
  });
}

function addRule(): void {
  draft.businessRules.push({ ruleCode: "", description: "" });
}

function replaceNodes(nodes: unknown[]): void {
  const previousNodeCodes = draft.nodes.map((node) => node.nodeCode).filter(Boolean);
  const nextNodes = clone(nodes) as BusinessRequirement["nodes"];
  const nextNodeCodes = nextNodes.map((node) => node.nodeCode).filter(Boolean);
  const removedNodeCodes = previousNodeCodes.filter((nodeCode) => !nextNodeCodes.includes(nodeCode));
  const addedNodeCodes = nextNodeCodes.filter((nodeCode) => !previousNodeCodes.includes(nodeCode));
  if (draft.nodes.length === nextNodes.length && removedNodeCodes.length === 1 && addedNodeCodes.length === 1) {
    replaceAttachmentNodeCode(removedNodeCodes[0], addedNodeCodes[0]);
  } else {
    for (const removedNodeCode of removedNodeCodes) removeAttachmentNodeCode(removedNodeCode);
  }
  draft.nodes = nextNodes;
}

function replaceEdges(edges: unknown[]): void {
  draft.edges = clone(edges) as BusinessRequirement["edges"];
}

function toCsv(values: string[]): string {
  return values.join(", ");
}

function fromCsv(value: string): string[] {
  return value.split(/[,，]/).map((item) => item.trim()).filter(Boolean);
}

function jsonText(value: Record<string, unknown>): string {
  return JSON.stringify(value);
}

function parseRecord(text: string): Record<string, unknown> {
  try { return JSON.parse(text || "{}"); } catch { return {}; }
}

function parseReferenceDataSource(text: string): ReferenceDataSource | undefined {
  if (!text.trim()) return undefined;
  try { return JSON.parse(text) as ReferenceDataSource; } catch { return undefined; }
}

function replaceAttachmentNodeCode(oldNodeCode: string, newNodeCode: string): void {
  for (const attachment of draft.attachments) {
    attachment.applicableNodeCodes = uniqueValues(attachment.applicableNodeCodes.map((nodeCode) =>
      nodeCode === oldNodeCode ? newNodeCode : nodeCode));
  }
}

function removeAttachmentNodeCode(removedNodeCode: string): void {
  for (const attachment of draft.attachments) {
    attachment.applicableNodeCodes = attachment.applicableNodeCodes.filter((nodeCode) => nodeCode !== removedNodeCode);
  }
}

function uniqueValues(values: string[]): string[] {
  return Array.from(new Set(values.filter(Boolean)));
}
</script>

<template>
  <div class="requirement-editor">
    <div class="editor-meta">
      <div>
        <span class="eyebrow">需求版本</span>
        <strong>#{{ revision.revision }}</strong>
        <el-tag size="small" :type="revision.readyForReview ? 'success' : 'warning'">
          {{ revision.readyForReview ? "可确认" : "待补充" }}
        </el-tag>
      </div>
      <el-button type="primary" plain :disabled="disabled" @click="save">保存修改</el-button>
    </div>

    <el-alert
      v-if="revision.missingItems.length"
      type="warning"
      :closable="false"
      :title="`缺失：${revision.missingItems.join('；')}`"
    />
    <el-alert
      v-if="revision.ambiguities.length"
      type="error"
      :closable="false"
      :title="`需澄清：${revision.ambiguities.join('；')}`"
    />

    <section class="editor-section">
      <h3>基本信息</h3>
      <div class="field-grid">
        <el-form-item label="业务名称"><el-input v-model="draft.businessName" :disabled="disabled" /></el-form-item>
        <el-form-item label="业务编码"><el-input v-model="draft.businessCode" :disabled="disabled" /></el-form-item>
        <el-form-item label="系统编码"><el-input v-model="draft.systemCode" :disabled="disabled" /></el-form-item>
        <el-form-item class="wide" label="办理目标"><el-input v-model="draft.goal" type="textarea" :disabled="disabled" /></el-form-item>
      </div>
    </section>

    <section class="editor-section">
      <div class="section-title"><h3>参与角色</h3><el-button size="small" :disabled="disabled" @click="addParticipant">添加角色</el-button></div>
      <div v-for="(item, index) in draft.participants" :key="index" class="edit-row three">
        <el-input v-model="item.roleCode" placeholder="角色编码" :disabled="disabled" />
        <el-input v-model="item.roleName" placeholder="角色名称" :disabled="disabled" />
        <el-input v-model="item.responsibility" placeholder="职责" :disabled="disabled" />
        <el-button link type="danger" :disabled="disabled" @click="draft.participants.splice(index, 1)">删除</el-button>
      </div>
    </section>

    <section class="editor-section graph-editor-section">
      <ProcessGraphDesigner
        title="需求流程图"
        :nodes="draft.nodes"
        :edges="draft.edges"
        :editable="!disabled"
        @update:nodes="replaceNodes"
        @update:edges="replaceEdges"
      />
    </section>

    <section class="editor-section">
      <div class="section-title"><h3>表单字段（{{ draft.formFields.length }}）</h3><el-button size="small" :disabled="disabled" @click="addField">添加字段</el-button></div>
      <div class="form-fields-scroll" tabindex="0" aria-label="全部表单字段">
        <div v-for="(field, index) in draft.formFields" :key="index" class="card-row">
          <div class="edit-row four">
            <el-input v-model="field.fieldCode" placeholder="字段编码" :disabled="disabled" />
            <el-input v-model="field.fieldName" placeholder="字段名称" :disabled="disabled" />
            <el-select v-model="field.fieldType" :disabled="disabled">
              <el-option v-for="type in ['string','number','date','boolean','select']" :key="type" :value="type" />
            </el-select>
            <el-select v-model="field.controlType" :disabled="disabled">
              <el-option v-for="type in ['input','textarea','number','datePicker','checkbox','select']" :key="type" :value="type" />
            </el-select>
          </div>
          <div class="edit-row four compact">
            <el-checkbox v-model="field.required" :disabled="disabled">必填</el-checkbox>
            <el-checkbox v-model="field.multiple" :disabled="disabled">多选</el-checkbox>
            <el-checkbox v-model="field.readOnly" :disabled="disabled">只读</el-checkbox>
            <el-input v-model="field.defaultValue" placeholder="默认值" :disabled="disabled" />
            <el-input
              :model-value="jsonText(field.validation)"
              placeholder="校验 JSON"
              :disabled="disabled"
              @change="field.validation = parseRecord($event)"
            />
            <el-button link type="danger" :disabled="disabled" @click="draft.formFields.splice(index, 1)">删除</el-button>
          </div>
          <div class="edit-row two compact">
            <el-input
              :model-value="field.referenceDataSource ? jsonText(field.referenceDataSource) : ''"
              placeholder='动态数据源 JSON，例如 {"resource":"EXCHANGES"}'
              :disabled="disabled"
              @change="field.referenceDataSource = parseReferenceDataSource($event)"
            />
          </div>
        </div>
      </div>
    </section>

    <section class="editor-section">
      <div class="section-title"><h3>附件材料</h3><el-button size="small" :disabled="disabled" @click="addAttachment">添加附件</el-button></div>
      <div v-for="(item, index) in draft.attachments" :key="index" class="card-row">
        <div class="edit-row four">
          <el-input v-model="item.attachmentCode" placeholder="附件编码" :disabled="disabled" />
          <el-input v-model="item.attachmentName" placeholder="附件名称" :disabled="disabled" />
          <el-input
            :model-value="toCsv(item.allowedExtensions)"
            placeholder="pdf, jpg, png"
            :disabled="disabled"
            @change="item.allowedExtensions = fromCsv($event)"
          />
          <el-input-number v-model="item.maxSizeBytes" :min="1" :disabled="disabled" />
        </div>
        <div class="edit-row four compact">
          <el-checkbox v-model="item.required" :disabled="disabled">必填</el-checkbox>
          <el-input-number v-model="item.minCount" :min="0" :disabled="disabled" />
          <el-input-number v-model="item.maxCount" :min="1" :disabled="disabled" />
          <el-input
            :model-value="toCsv(item.applicableNodeCodes)"
            placeholder="适用节点编码"
            :disabled="disabled"
            @change="item.applicableNodeCodes = fromCsv($event)"
          />
          <el-button link type="danger" :disabled="disabled" @click="draft.attachments.splice(index, 1)">删除</el-button>
        </div>
      </div>
    </section>

    <section class="editor-section">
      <div class="section-title"><h3>业务规则</h3><el-button size="small" :disabled="disabled" @click="addRule">添加规则</el-button></div>
      <div v-for="(rule, index) in draft.businessRules" :key="index" class="edit-row three">
        <el-input v-model="rule.ruleCode" placeholder="规则编码" :disabled="disabled" />
        <el-input v-model="rule.description" placeholder="规则说明" :disabled="disabled" />
        <el-input v-model="rule.expression" placeholder="表达式（可选）" :disabled="disabled" />
        <el-button link type="danger" :disabled="disabled" @click="draft.businessRules.splice(index, 1)">删除</el-button>
      </div>
    </section>

    <section class="editor-section">
      <div class="section-title"><h3>页面行为</h3></div>
      <el-input
        type="textarea"
        :rows="12"
        :model-value="frontendBehaviorText()"
        aria-label="页面行为 JSON"
        placeholder="页面分区、只读查询、计算和核查规则"
        :disabled="disabled"
        @change="updateFrontendBehavior($event)"
      />
    </section>
  </div>
</template>
