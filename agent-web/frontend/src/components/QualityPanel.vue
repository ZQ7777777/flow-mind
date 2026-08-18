<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import type {
  CodeGenerationSummary,
  GenerationQualityReport,
  QualityDiagnostic,
  QualityStageName,
} from "@flowmind/agent-contracts";
import { useWorkflowStore } from "../stores/workflow";

type OverrideScope = "BACKEND_TESTS" | "FRONTEND_TESTS" | "REVIEWER";

const props = defineProps<{ generation: CodeGenerationSummary }>();
const emit = defineEmits<{ selectDiagnostic: [path: string] }>();
const store = useWorkflowStore();
const scopes = ref<OverrideScope[]>([]);
const reason = ref("");

const quality = computed<GenerationQualityReport | undefined>(
  () => store.qualityReport || props.generation.quality,
);
const failedSoftScopes = computed<OverrideScope[]>(() => {
  const report = quality.value;
  if (!report) return [];
  const result: OverrideScope[] = [];
  if (report.stages.some(({ stage, status }) => stage === "BACKEND_TESTS" && status !== "PASSED")) result.push("BACKEND_TESTS");
  if (report.stages.some(({ stage, status }) => stage === "FRONTEND_TESTS" && status !== "PASSED")) result.push("FRONTEND_TESTS");
  if (!report.review || report.review.verdict !== "APPROVE") result.push("REVIEWER");
  return result;
});
const canOverride = computed(() =>
  quality.value?.hardGatePassed
  && failedSoftScopes.value.length > 0
  && scopes.value.length > 0
  && reason.value.trim().length >= 10,
);
const canReverify = computed(() => store.allowedActions.includes("REVERIFY"));
const canStartQuality = computed(() =>
  props.generation.status === "REVIEW" && (!quality.value || quality.value.pipelineState === "CANCELLED"),
);
const canConfirmWrite = computed(() =>
  Boolean(quality.value?.canWrite)
  && ["REVIEW", "WRITE_FAILED", "ENTRY_CONFIG_FAILED"].includes(props.generation.status),
);

watch(() => props.generation.generationRevision, () => {
  scopes.value = [];
  reason.value = "";
});
onMounted(() => {
  if (!quality.value) void store.loadGenerationQuality();
});

async function reverify(): Promise<void> {
  await store.reverifyGeneration(Boolean(quality.value?.aiReviewSkipped));
  ElMessage.success("已启动完整质量复验");
}

async function startQuality(skipAiReview: boolean): Promise<void> {
  await store.startGenerationQuality(skipAiReview);
  ElMessage.success(skipAiReview ? "已启动质量门禁，AI 审核将被跳过" : "已启动质量门禁与 AI 审核");
}

async function applyOverride(): Promise<void> {
  await store.overrideGenerationQuality(scopes.value, reason.value);
  ElMessage.success("质量覆盖已记录");
}

async function confirmWrite(): Promise<void> {
  await store.confirmGenerationWrite();
  ElMessage.success("候选代码已安全写入目标工程并登记到业务大厅");
}

function label(stage: QualityStageName): string {
  return {
    STATIC_VALIDATION: "静态边界",
    BACKEND_COMPILE: "Java 编译",
    BACKEND_TESTS: "JUnit",
    FRONTEND_TYPECHECK: "Vue 类型",
    FRONTEND_TESTS: "Vitest",
    FRONTEND_BUILD: "前端构建",
  }[stage];
}

function statusType(status: string): "success" | "warning" | "danger" | "info" {
  if (status === "PASSED") return "success";
  if (status === "FAILED" || status === "INFRASTRUCTURE_FAILED") return "danger";
  if (status === "RUNNING" || status === "PENDING") return "warning";
  return "info";
}

function hasDiagnosticDetails(item: QualityDiagnostic): boolean {
  return Boolean(
    item.actual
    || item.expected
    || item.evidence
    || item.repairHint
    || item.acceptedForms?.length
    || item.unsupportedForms?.length,
  );
}
</script>

<template>
  <aside class="quality-panel">
    <div class="quality-heading">
      <div>
        <span>质量门禁</span>
        <small v-if="quality">修复 {{ quality.repairRound }}/{{ quality.maxRepairRounds }}</small>
      </div>
      <el-tag size="small" :type="quality?.canWrite ? 'success' : quality?.hardGatePassed ? 'warning' : 'info'">
        {{ quality?.pipelineState || generation.status }}
      </el-tag>
    </div>

    <div v-if="quality" class="quality-scroll">
      <div class="stage-list">
        <div v-for="stage in quality.stages" :key="stage.stage" class="stage-row">
          <span :class="['status-dot', statusType(stage.status)]"></span>
          <span class="stage-name">{{ label(stage.stage) }}</span>
          <el-tag size="small" :type="statusType(stage.status)">{{ stage.status }}</el-tag>
        </div>
      </div>

      <div v-if="quality.repairAttempts?.length" class="quality-section repair-history">
        <strong>修复记录</strong>
        <div
          v-for="attempt in quality.repairAttempts"
          :key="`${attempt.round}-${attempt.verificationRunId}`"
          class="repair-attempt"
        >
          <div class="section-title">
            <span>第 {{ attempt.round }} 轮 · {{ attempt.outcome }}</span>
            <el-tag v-if="attempt.failureCode" size="small" type="danger">
              {{ attempt.failureCode }}
            </el-tag>
          </div>
          <p v-if="attempt.failureCode === 'REPAIR_PROTOCOL_INVALID'" class="review-summary">
            {{ attempt.changedFiles.length }} 个文件的修改未通过修复协议校验，已回滚。
          </p>
          <p v-else-if="attempt.failureCode === 'REPAIR_NO_EFFECT'" class="review-summary">
            本轮未产生实际文件修改。
          </p>
          <p v-else class="review-summary">
            已修改 {{ attempt.changedFiles.length }} 个文件。
          </p>
        </div>
      </div>

      <div v-if="quality.stages.some((stage) => stage.diagnostics.length)" class="quality-section">
        <strong>诊断</strong>
        <template v-for="stage in quality.stages" :key="stage.stage">
          <div
            v-for="item in stage.diagnostics"
            :key="item.diagnosticId || `${stage.stage}-${item.code}-${item.relativePath || ''}-${item.line || 0}`"
            class="diagnostic"
          >
            <button
              class="diagnostic-target"
              type="button"
              :disabled="!item.relativePath"
              @click="item.relativePath && emit('selectDiagnostic', item.relativePath)"
            >
              <span>{{ item.code }}</span>
              <small>{{ item.relativePath }}{{ item.line ? `:${item.line}` : "" }}</small>
              <p>{{ item.message }}</p>
            </button>
            <details v-if="hasDiagnosticDetails(item)" class="diagnostic-details">
              <summary>检查详情</summary>
              <dl>
                <template v-if="item.actual"><dt>实际识别</dt><dd>{{ item.actual }}</dd></template>
                <template v-if="item.expected"><dt>期望结果</dt><dd>{{ item.expected }}</dd></template>
                <template v-if="item.evidence"><dt>检查证据</dt><dd>{{ item.evidence }}</dd></template>
                <template v-if="item.repairHint"><dt>修复建议</dt><dd>{{ item.repairHint }}</dd></template>
                <template v-if="item.acceptedForms?.length">
                  <dt>可接受写法</dt>
                  <dd><ul><li v-for="form in item.acceptedForms" :key="form">{{ form }}</li></ul></dd>
                </template>
                <template v-if="item.unsupportedForms?.length">
                  <dt>不支持写法</dt>
                  <dd><ul><li v-for="form in item.unsupportedForms" :key="form">{{ form }}</li></ul></dd>
                </template>
              </dl>
            </details>
          </div>
        </template>
      </div>

      <div v-if="quality.review" class="quality-section">
        <div class="section-title">
          <strong>Reviewer</strong>
          <el-tag size="small" :type="quality.review.verdict === 'APPROVE' ? 'success' : 'danger'">
            {{ quality.review.verdict }}
          </el-tag>
        </div>
        <p class="review-summary">{{ quality.review.summary }}</p>
        <button
          v-for="issue in quality.review.issues"
          :key="issue.code"
          class="diagnostic"
          type="button"
          :disabled="!issue.relativePath"
          @click="issue.relativePath && emit('selectDiagnostic', issue.relativePath)"
        >
          <span>{{ issue.title }}</span>
          <small>{{ issue.relativePath }}</small>
          <p>{{ issue.message }}</p>
        </button>
      </div>

      <div v-else-if="quality.aiReviewSkipped" class="quality-section">
        <div class="section-title"><strong>AI 审核</strong><el-tag size="small" type="info">已跳过</el-tag></div>
        <p class="review-summary">由当前用户在启动质量门禁时明确跳过。</p>
      </div>

      <div v-if="quality.overrideRequired" class="quality-section override-form">
        <strong>软门禁覆盖</strong>
        <el-checkbox-group v-model="scopes">
          <el-checkbox v-for="scope in failedSoftScopes" :key="scope" :value="scope">{{ scope }}</el-checkbox>
        </el-checkbox-group>
        <el-input v-model="reason" type="textarea" :rows="2" maxlength="240" show-word-limit placeholder="覆盖原因（至少 10 个字符）" />
        <el-button :disabled="!canOverride || store.busy" @click="applyOverride">记录覆盖</el-button>
      </div>
    </div>

    <div v-else class="quality-empty">代码已生成，可选择启动质量门禁</div>

    <div class="quality-actions">
      <template v-if="canStartQuality">
        <el-button type="primary" :disabled="store.busy" @click="startQuality(false)">进入质量门禁</el-button>
        <el-button :disabled="store.busy" @click="startQuality(true)">跳过 AI 审核</el-button>
      </template>
      <el-button :disabled="store.busy || !canReverify" @click="reverify">
        重新验证
      </el-button>
      <el-popconfirm
        title="确认把当前 Manifest 的全部文件写入目标工程？"
        confirm-button-text="确认写入"
        cancel-button-text="取消"
        @confirm="confirmWrite"
      >
        <template #reference>
          <el-button type="primary" :disabled="!canConfirmWrite || store.busy">写入工程</el-button>
        </template>
      </el-popconfirm>
    </div>
  </aside>
</template>

<style scoped>
.quality-panel { min-width: 0; height: 100%; display: flex; flex-direction: column; border-left: 1px solid #dfe5ed; background: #fff; }
.quality-heading, .section-title, .stage-row, .quality-actions { display: flex; align-items: center; }
.quality-heading { min-height: 52px; padding: 0 12px; justify-content: space-between; border-bottom: 1px solid #dfe5ed; }
.quality-heading div { display: flex; flex-direction: column; gap: 2px; }
.quality-heading small { color: #6b7280; }
.quality-scroll { min-height: 0; flex: 1; overflow: auto; padding: 12px; }
.stage-list { display: grid; gap: 7px; }
.stage-row { min-height: 30px; gap: 8px; }
.stage-name { flex: 1; font-size: 12px; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; background: #94a3b8; }
.status-dot.success { background: #16805b; }
.status-dot.warning { background: #b7791f; }
.status-dot.danger { background: #c2413b; }
.quality-section { margin-top: 16px; padding-top: 12px; border-top: 1px solid #e5eaf0; }
.section-title { justify-content: space-between; }
.diagnostic { width: 100%; margin-top: 8px; border: 1px solid #dfe5ed; border-radius: 4px; background: #fff; text-align: left; }
button.diagnostic { padding: 8px; cursor: pointer; }
button.diagnostic:disabled { cursor: default; }
.diagnostic-target { width: 100%; padding: 8px; border: 0; background: transparent; text-align: left; cursor: pointer; }
.diagnostic-target:disabled { cursor: default; }
.diagnostic span, .diagnostic small { display: block; overflow-wrap: anywhere; }
.diagnostic span { font-size: 12px; font-weight: 600; color: #9f2f2b; }
.diagnostic small { margin-top: 3px; color: #64748b; }
.diagnostic p, .review-summary { margin: 5px 0 0; font-size: 12px; line-height: 1.45; color: #334155; }
.diagnostic-details { padding: 0 8px 8px; border-top: 1px solid #eef2f6; font-size: 12px; color: #334155; }
.diagnostic-details summary { padding-top: 8px; cursor: pointer; color: #315f82; }
.diagnostic-details dl { display: grid; grid-template-columns: 68px minmax(0, 1fr); gap: 6px 8px; margin: 8px 0 0; }
.diagnostic-details dt { font-weight: 600; color: #64748b; }
.diagnostic-details dd { min-width: 0; margin: 0; overflow-wrap: anywhere; }
.diagnostic-details ul { margin: 0; padding-left: 18px; }
.repair-attempt { margin-top: 8px; padding: 8px; border: 1px solid #dfe5ed; border-radius: 4px; }
.override-form { display: grid; gap: 9px; }
.override-form :deep(.el-checkbox-group) { display: grid; }
.quality-actions { gap: 8px; padding: 10px 12px; border-top: 1px solid #dfe5ed; }
.quality-actions .el-button { flex: 1; margin: 0; }
.quality-empty { flex: 1; display: grid; place-items: center; color: #64748b; font-size: 12px; }
</style>
