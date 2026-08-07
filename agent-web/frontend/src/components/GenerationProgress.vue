<script setup lang="ts">
import { computed, nextTick, watch } from "vue";
import { useWorkflowStore, type GenerationLogEntry } from "../stores/workflow";
import { useStickToBottom } from "../composables/useStickToBottom";
import { useResizableHeight } from "../composables/useResizableHeight";

const store = useWorkflowStore();

// Map the constrained generator tool names to human-readable Chinese labels so
// the progress log reads naturally; unknown tools fall back to their raw name.
const TOOL_LABELS: Record<string, string> = {
  read_generation_contract_file: "读取参考文件",
  read_staged_file: "读取暂存文件",
  list_staged_files: "列出暂存文件",
  write_staged_file: "写入暂存文件",
  delete_staged_file: "删除暂存文件",
  read_verification_diagnostic: "读取校验诊断",
  report_generation_complete: "报告生成完成",
  report_repair_complete: "报告修复完成",
};

const runningCount = computed(() => store.generationLog.filter((entry) => entry.status === "running").length);

function label(entry: GenerationLogEntry): string {
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
  <div class="generation-progress">
    <header class="gp-head">
      <span class="gp-spinner" aria-hidden="true"></span>
      <strong>Generator 正在生成</strong>
      <span class="gp-stats">{{ store.generationLog.length }} 步<span v-if="runningCount"> · {{ runningCount }} 进行中</span></span>
    </header>

    <div v-if="!store.generationLog.length" class="gp-empty">已启动，等待第一个工具调用…</div>
    <ul v-else class="gp-log">
      <li v-for="entry in store.generationLog" :key="entry.id" :class="['gp-entry', entry.status]">
        <span class="gp-dot" aria-hidden="true"></span>
        <span class="gp-label">{{ label(entry) }}</span>
        <span v-if="entry.status === 'running'" class="gp-tag gp-running">进行中</span>
        <span v-else-if="entry.status === 'error'" class="gp-tag gp-error">失败</span>
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
    <p v-else-if="store.streamingText" class="gp-stream" :title="store.streamingText">{{ store.streamingText }}<span class="gp-cursor"></span></p>
  </div>
</template>

<style scoped>
.generation-progress { height: 590px; display: flex; flex-direction: column; gap: 14px; padding: 18px; box-sizing: border-box; overflow: hidden; }
.gp-head { display: flex; align-items: center; gap: 10px; }
.gp-head strong { font-size: 15px; color: #1f2a37; }
.gp-stats { margin-left: auto; font-size: 12px; color: #7b8794; }
.gp-spinner { width: 14px; height: 14px; border-radius: 50%; border: 2px solid #dbe3f6; border-top-color: #275de7; animation: gp-spin .8s linear infinite; }
@keyframes gp-spin { to { transform: rotate(360deg); } }
.gp-empty { color: #7b8794; font-size: 13px; }
.gp-log { list-style: none; margin: 0; padding: 0; flex: 1 1 0; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 2px; }
.gp-entry { display: flex; align-items: center; gap: 10px; padding: 7px 8px; border-radius: 8px; font-size: 13px; }
.gp-entry.completed { color: #44506b; }
.gp-entry.running { color: #1f2a37; background: #f4f7ff; }
.gp-entry.error { color: #b3261e; background: #fff0ef; }
.gp-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; background: #cbd5e6; }
.gp-entry.completed .gp-dot { background: #2f9e6e; }
.gp-entry.running .gp-dot { background: #275de7; animation: gp-pulse 1.2s ease-in-out infinite; }
.gp-entry.error .gp-dot { background: #e3412f; }
@keyframes gp-pulse { 0%,100% { opacity: 1; } 50% { opacity: .35; } }
.gp-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.gp-tag { font-size: 11px; padding: 1px 7px; border-radius: 10px; flex-shrink: 0; }
.gp-running { color: #275de7; background: #e8eeff; }
.gp-error { color: #b3261e; background: #ffe7e5; }
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
