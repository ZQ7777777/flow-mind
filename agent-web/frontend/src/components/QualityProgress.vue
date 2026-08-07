<script setup lang="ts">
import { computed, nextTick, watch } from "vue";
import { useWorkflowStore, type GenerationLogEntry } from "../stores/workflow";
import { useStickToBottom } from "../composables/useStickToBottom";
import { useResizableHeight } from "../composables/useResizableHeight";

const store = useWorkflowStore();

// Same stage labels as QualityPanel.vue so the live checklist reads identically
// to the final report.
const STAGE_LABELS: Record<string, string> = {
  STATIC_VALIDATION: "静态边界",
  BACKEND_COMPILE: "Java 编译",
  BACKEND_TESTS: "JUnit",
  FRONTEND_TYPECHECK: "Vue 类型",
  FRONTEND_TESTS: "Vitest",
  FRONTEND_BUILD: "前端构建",
};

// Reviewer + repair tool names → Chinese labels; unknown tools fall back to raw.
const TOOL_LABELS: Record<string, string> = {
  read_staged_file: "读取暂存文件",
  read_staged_diff: "读取差异",
  read_quality_report: "读取质量报告",
  submit_code_review: "提交审核结论",
  read_verification_diagnostic: "读取校验诊断",
  write_staged_file: "写入暂存文件",
  delete_staged_file: "删除暂存文件",
  report_repair_complete: "报告修复完成",
};

const STAGE_TAGS: Record<string, string> = {
  PENDING: "等待",
  RUNNING: "运行中",
  PASSED: "通过",
  FAILED: "失败",
  SKIPPED: "跳过",
  INFRASTRUCTURE_FAILED: "环境失败",
  CANCELLED: "取消",
};

const subPhase = computed(() => {
  if (store.state === "CODE_REVIEWING") return "AI 审核";
  if (store.state === "CODE_REPAIRING") return "自动修复";
  return "命令校验";
});

const runningCount = computed(() => store.qualityLog.filter((entry) => entry.status === "running").length);

function stageClass(status: string): string {
  if (status === "RUNNING") return "running";
  if (status === "PASSED") return "passed";
  if (status === "FAILED" || status === "INFRASTRUCTURE_FAILED") return "failed";
  if (status === "SKIPPED" || status === "CANCELLED") return "skipped";
  return "pending";
}

function stageTag(status: string): string {
  return STAGE_TAGS[status] || status;
}

function toolLabel(entry: GenerationLogEntry): string {
  const base = (entry.toolName && TOOL_LABELS[entry.toolName]) || entry.toolName || "执行工具";
  return entry.target ? `${base} · ${entry.target}` : base;
}

// Keep the reasoning box scrolled to the latest token while it streams, unless
// the user scrolls up to read earlier reasoning.
const { el: reasoningEl, onScroll: onReasoningScroll, stick: stickReasoning, resetPin: resetReasoningPin } = useStickToBottom();
watch(() => store.reasoningText, (val) => {
  if (!val) { resetReasoningPin(); return; }
  void nextTick(() => stickReasoning());
});

// The reasoning box keeps its initial 96px height but can be dragged taller to
// read more of the model's thinking; the choice is persisted across sessions.
const { height: reasoningHeight, startResize: startReasoningResize, resizeByKeyboard: resizeReasoningByKeyboard } = useResizableHeight();
</script>

<template>
  <div class="quality-progress">
    <header class="qp-head">
      <span class="gp-spinner" aria-hidden="true"></span>
      <strong>质量门禁审查中</strong>
      <span class="gp-stats">{{ subPhase }}<span v-if="runningCount"> · {{ runningCount }} 进行中</span></span>
    </header>

    <div class="qp-stages">
      <div v-for="entry in store.verifyStages" :key="entry.stage" :class="['qp-stage', stageClass(entry.status)]">
        <span class="qp-dot" aria-hidden="true"></span>
        <span class="qp-stage-name">{{ STAGE_LABELS[entry.stage] || entry.stage }}</span>
        <span v-if="entry.hardGate" class="qp-hard">硬门禁</span>
        <span class="qp-stage-tag">{{ stageTag(entry.status) }}</span>
      </div>
    </div>

    <ul v-if="store.qualityLog.length" class="qp-log">
      <li v-for="entry in store.qualityLog" :key="entry.id" :class="['gp-entry', entry.status]">
        <span class="gp-dot" aria-hidden="true"></span>
        <span class="gp-label">{{ toolLabel(entry) }}</span>
      </li>
    </ul>

    <div v-if="store.reasoningText" class="gp-reasoning-wrap">
      <div
        class="gp-reasoning-handle"
        role="separator"
        aria-orientation="horizontal"
        aria-label="拖动调整思考内容高度"
        tabindex="0"
        @pointerdown="startReasoningResize"
        @keydown="resizeReasoningByKeyboard"
      ></div>
      <p ref="reasoningEl" class="gp-stream gp-reasoning" :style="{ maxHeight: reasoningHeight + 'px' }" :title="store.reasoningText" @scroll="onReasoningScroll">
        <span class="gp-thinking">模型思考中…</span>{{ store.reasoningText }}<span class="gp-cursor"></span>
      </p>
    </div>
    <p v-else-if="store.qualityStream" class="gp-stream" :title="store.qualityStream">{{ store.qualityStream }}<span class="gp-cursor"></span></p>
  </div>
</template>

<style scoped>
.quality-progress { height: 590px; display: flex; flex-direction: column; gap: 12px; padding: 18px; box-sizing: border-box; overflow: hidden; }
.qp-head { display: flex; align-items: center; gap: 10px; }
.qp-head strong { font-size: 15px; color: #1f2a37; }
.gp-stats { margin-left: auto; font-size: 12px; color: #7b8794; }
.gp-spinner { width: 14px; height: 14px; border-radius: 50%; border: 2px solid #dbe3f6; border-top-color: #275de7; animation: gp-spin .8s linear infinite; }
@keyframes gp-spin { to { transform: rotate(360deg); } }

.qp-stages { display: grid; gap: 6px; }
.qp-stage { display: flex; align-items: center; gap: 10px; padding: 7px 8px; border-radius: 8px; font-size: 13px; background: #f8fafc; }
.qp-stage.pending { color: #7b8794; }
.qp-stage.running { color: #1f2a37; background: #f4f7ff; }
.qp-stage.passed { color: #44506b; }
.qp-stage.failed { color: #b3261e; background: #fff0ef; }
.qp-stage.skipped { color: #9aa5b5; }
.qp-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; background: #cbd5e6; }
.qp-stage.passed .qp-dot { background: #2f9e6e; }
.qp-stage.running .qp-dot { background: #275de7; animation: gp-pulse 1.2s ease-in-out infinite; }
.qp-stage.failed .qp-dot { background: #e3412f; }
.qp-stage.skipped .qp-dot { background: #dbe1ea; }
@keyframes gp-pulse { 0%,100% { opacity: 1; } 50% { opacity: .35; } }
.qp-stage-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qp-hard { font-size: 10px; padding: 1px 6px; border-radius: 8px; color: #6b7280; background: #eef1f6; flex-shrink: 0; }
.qp-stage-tag { font-size: 11px; color: #7b8794; flex-shrink: 0; }

.qp-log { list-style: none; margin: 0; padding: 0; flex: 1 1 0; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 2px; }
.gp-entry { display: flex; align-items: center; gap: 10px; padding: 7px 8px; border-radius: 8px; font-size: 13px; }
.gp-entry.completed { color: #44506b; }
.gp-entry.running { color: #1f2a37; background: #f4f7ff; }
.gp-entry.error { color: #b3261e; background: #fff0ef; }
.gp-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; background: #cbd5e6; }
.gp-entry.completed .gp-dot { background: #2f9e6e; }
.gp-entry.running .gp-dot { background: #275de7; animation: gp-pulse 1.2s ease-in-out infinite; }
.gp-entry.error .gp-dot { background: #e3412f; }
.gp-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.gp-stream { margin: 0; padding: 10px 12px; border-radius: 8px; background: #f8fafc; color: #5a6678; font-size: 12px; line-height: 1.5; max-height: 96px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; }
.gp-reasoning { background: #eef3ff; color: #44506b; display: block; overflow-y: auto; -webkit-line-clamp: unset; white-space: pre-wrap; word-break: break-word; }
.gp-reasoning-wrap { display: flex; flex-direction: column; flex-shrink: 0; }
.gp-reasoning-handle { height: 8px; cursor: ns-resize; background: #e7edff; border-radius: 8px 8px 0 0; display: flex; align-items: center; justify-content: center; }
.gp-reasoning-handle::after { content: ""; width: 30px; height: 3px; border-radius: 3px; background: #aebbd9; transition: background .15s; }
.gp-reasoning-handle:hover::after,
.gp-reasoning-handle:focus-visible::after { background: #275de7; }
.gp-reasoning-handle:focus-visible { outline: none; }
.gp-reasoning-wrap > .gp-reasoning { border-top-left-radius: 0; border-top-right-radius: 0; }
.gp-thinking { display: inline-block; margin-right: 6px; padding: 0 6px; border-radius: 6px; background: #275de7; color: #fff; font-size: 11px; font-weight: 600; vertical-align: middle; }
.gp-cursor { display: inline-block; width: 6px; height: 12px; margin-left: 2px; background: #275de7; vertical-align: middle; animation: gp-blink 1s step-end infinite; }
@keyframes gp-blink { 50% { opacity: 0; } }
</style>
