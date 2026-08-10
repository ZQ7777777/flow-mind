<script setup lang="ts">
import { computed, ref } from "vue";
import type { TaskActionCode } from "../../types/workflow";

const props = defineProps<{
  taskVersion: number;
  allowedActions: TaskActionCode[];
  submitting?: boolean;
}>();

const emit = defineEmits<{
  submit: [
    payload: {
      action: TaskActionCode;
      expectedTaskVersion: number;
      comment: string;
      targetNodeCode?: string;
      targetUserId?: string;
    },
  ];
}>();

const comment = ref("");
const targetNodeCode = ref("");
const targetUserId = ref("");

const actionConfig: Array<{
  code: TaskActionCode;
  label: string;
  kind: "primary" | "neutral" | "danger";
  requiresTargetNode?: boolean;
  requiresTargetUser?: boolean;
}> = [
  { code: "APPROVE", label: "通过", kind: "primary" },
  { code: "SUBMIT", label: "提交", kind: "primary" },
  { code: "REJECT", label: "驳回", kind: "danger", requiresTargetNode: true },
  { code: "RETURN", label: "退回", kind: "danger" },
  { code: "WITHDRAW", label: "撤回", kind: "danger" },
  { code: "DIRECT_SEND", label: "直送", kind: "neutral", requiresTargetNode: true },
  { code: "TRANSFER", label: "转办", kind: "neutral", requiresTargetUser: true },
  { code: "DELEGATE", label: "委托", kind: "neutral", requiresTargetUser: true },
  { code: "ADD_SIGN", label: "加签", kind: "neutral", requiresTargetUser: true },
  { code: "CLAIM", label: "认领", kind: "primary" },
  { code: "UNCLAIM", label: "取消认领", kind: "neutral" },
];

const visibleActions = computed(() =>
  actionConfig.filter((action) => props.allowedActions.includes(action.code)),
);

function submit(action: (typeof actionConfig)[number]): void {
  const payload: {
    action: TaskActionCode;
    expectedTaskVersion: number;
    comment: string;
    targetNodeCode?: string;
    targetUserId?: string;
  } = {
    action: action.code,
    expectedTaskVersion: props.taskVersion,
    comment: comment.value.trim(),
  };
  if (action.requiresTargetNode && targetNodeCode.value.trim()) {
    payload.targetNodeCode = targetNodeCode.value.trim();
  }
  if (action.requiresTargetUser && targetUserId.value.trim()) {
    payload.targetUserId = targetUserId.value.trim();
  }
  emit("submit", payload);
}
</script>

<template>
  <section class="workflow-section action-panel" aria-labelledby="actions-heading">
    <div class="section-header">
      <h2 id="actions-heading">办理动作</h2>
      <span class="version-chip">版本 {{ taskVersion }}</span>
    </div>
    <template v-if="visibleActions.length">
      <label class="field">
        <span>意见</span>
        <textarea v-model="comment" rows="3" maxlength="500" />
      </label>
      <div class="inline-fields">
        <label class="field">
          <span>目标节点</span>
          <input v-model="targetNodeCode" type="text" autocomplete="off" />
        </label>
        <label class="field">
          <span>目标用户</span>
          <input v-model="targetUserId" type="text" autocomplete="off" />
        </label>
      </div>
      <div class="action-row" role="group" aria-label="可用办理动作">
        <button
          v-for="action in visibleActions"
          :key="action.code"
          type="button"
          class="action-button"
          :class="`is-${action.kind}`"
          :data-test="`action-${action.code.toLowerCase().replace('_', '-')}`"
          :disabled="submitting"
          @click="submit(action)"
        >
          {{ action.label }}
        </button>
      </div>
    </template>
    <p v-else class="empty-state">当前任务暂无可执行动作</p>
  </section>
</template>

<style scoped>
.workflow-section {
  padding: 20px 0;
  border-top: 1px solid #e5e7eb;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

h2 {
  margin: 0;
  color: #111827;
  font-size: 18px;
  font-weight: 650;
}

.version-chip {
  padding: 3px 8px;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  color: #4b5563;
  font-size: 12px;
}

.field {
  display: grid;
  gap: 6px;
  color: #374151;
  font-size: 13px;
}

textarea,
input {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 9px 10px;
  color: #111827;
  font: inherit;
}

textarea:focus,
input:focus,
button:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}

.inline-fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.action-button {
  min-height: 36px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 0 14px;
  background: #fff;
  color: #111827;
  cursor: pointer;
  font-weight: 600;
}

.action-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.is-primary {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}

.is-danger {
  border-color: #dc2626;
  color: #b91c1c;
}

.empty-state {
  margin: 0;
  color: #6b7280;
}
</style>
