<script setup lang="ts">
import { watch } from "vue";
import { useWorkflowStore } from "../stores/workflow";

const store = useWorkflowStore();
watch(
  () => store.currentUser?.userId,
  (userId) => { if (userId) void store.loadManagementLists(); },
  { immediate: true },
);

function qualityLabel(row: { hardGatePassed: number; overrideRequired: number; canWrite: number }): string {
  if (row.canWrite) return "可写入";
  if (row.overrideRequired) return "待覆盖";
  if (row.hardGatePassed) return "软门禁";
  return "未通过";
}
</script>

<template>
  <div class="management-lists">
    <el-tabs>
      <el-tab-pane label="流程定义">
        <el-table :data="store.managedDefinitions" height="500" stripe>
          <el-table-column prop="businessCode" label="业务编码" min-width="150" />
          <el-table-column prop="businessName" label="业务名称" min-width="130" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="definitionVersion" label="版本" width="80" />
          <el-table-column prop="activatedAt" label="激活时间" min-width="180" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="代码生成">
        <el-table :data="store.managedGenerations" height="500" stripe>
          <el-table-column prop="businessCode" label="业务编码" min-width="150" />
          <el-table-column prop="businessName" label="业务名称" min-width="130" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="generationRevision" label="revision" width="90" />
          <el-table-column label="质量" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.canWrite ? 'success' : row.overrideRequired ? 'warning' : 'info'">
                {{ qualityLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="writtenAt" label="写入时间" min-width="180" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.management-lists { min-height: 540px; padding: 0 4px; }
</style>

