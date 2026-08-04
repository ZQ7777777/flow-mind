<script setup lang="ts">
import type { ProcessPreview } from "@flowmind/agent-contracts";
import ProcessGraphDesigner from "./ProcessGraphDesigner.vue";

defineProps<{ preview: ProcessPreview }>();
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

    <ProcessGraphDesigner title="平台流程图" :nodes="preview.nodes" :edges="preview.edges" />

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
      <h3>表单字段（{{ preview.formFields.length }}）</h3>
      <div class="preview-table-scroll" tabindex="0" aria-label="全部表单字段">
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
    </div>
    <div class="preview-table">
      <h3>附件模板</h3>
      <div class="preview-table-scroll">
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
  </div>
</template>
