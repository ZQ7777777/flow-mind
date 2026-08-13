<script setup lang="ts">
import { watch } from "vue";
import { ElMessageBox } from "element-plus";
import { useWorkflowStore } from "../stores/workflow";
import { isSessionSwitchConfirmationRequired } from "../workflow-presentation";

const store = useWorkflowStore();
const emit = defineEmits<{ opened: [] }>();
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

async function openSession(sessionId: string): Promise<void> {
  if (sessionId === store.snapshot?.sessionId) return;
  if (isSessionSwitchConfirmationRequired(store.state, store.snapshot?.sessionId, sessionId)) {
    try {
      await ElMessageBox.confirm(
        "当前会话仍在后台处理中。切换不会取消任务，稍后可从历史会话重新打开。",
        "切换历史会话",
        { confirmButtonText: "继续切换", cancelButtonText: "取消", type: "warning" },
      );
    } catch {
      return;
    }
  }
  try {
    await store.openSession(sessionId);
    emit("opened");
  } catch {
    // The store keeps the current session and exposes the loading error.
  }
}
</script>

<template>
  <div class="management-lists">
    <el-tabs>
      <el-tab-pane label="历史会话">
        <el-table :data="store.managedSessions" height="500" stripe>
          <el-table-column prop="businessName" label="业务名称" min-width="150" />
          <el-table-column prop="sessionId" label="Session ID" min-width="250" />
          <el-table-column prop="state" label="状态" min-width="150" />
          <el-table-column label="最近错误" min-width="220">
            <template #default="{ row }">
              {{ row.lastError?.message || "-" }}
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :disabled="store.busy || row.sessionId === store.snapshot?.sessionId"
                @click="openSession(row.sessionId)"
              >{{ row.sessionId === store.snapshot?.sessionId ? "当前" : "打开" }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="流程定义">
        <el-table :data="store.managedDefinitions" height="500" stripe>
          <el-table-column prop="businessCode" label="业务编码" min-width="150" />
          <el-table-column prop="businessName" label="业务名称" min-width="130" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              {{ row.activationStatus || row.status }}
            </template>
          </el-table-column>
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
