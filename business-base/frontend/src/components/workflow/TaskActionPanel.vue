<script setup lang="ts">
import { computed, ref } from "vue";
import type { Ref } from "vue";
import { fetchWorkflowUsers } from "../../api/workflow";
import type {
  TaskActionCode,
  WorkflowNodeView,
  WorkflowUserCandidateResponse,
} from "../../types/workflow";

const props = withDefaults(defineProps<{
  taskVersion: number;
  allowedActions: TaskActionCode[];
  disabledActions?: TaskActionCode[];
  rejectTargetNodes?: WorkflowNodeView[];
  submitting?: boolean;
}>(), {
  disabledActions: () => [],
  rejectTargetNodes: () => [],
});

const emit = defineEmits<{
  submit: [
    payload: {
      action: TaskActionCode;
      expectedTaskVersion: number;
      comment: string;
      targetNodeCode?: string;
      targetUserId?: string;
      targetUserName?: string;
      addSignUserIds?: string[];
    },
  ];
}>();

const comment = ref("");
const rejectTargetNodeCode = ref("");
const directSendTargetNodeCode = ref("");
const targetUserKeyword = ref("");
const targetUserOptions = ref<WorkflowUserCandidateResponse[]>([]);
const selectedTargetUser = ref<WorkflowUserCandidateResponse | null>(null);
const addSignUserKeyword = ref("");
const addSignUserOptions = ref<WorkflowUserCandidateResponse[]>([]);
const selectedAddSignUsers = ref<WorkflowUserCandidateResponse[]>([]);
const validationError = ref("");

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
  { code: "ADD_SIGN", label: "加签", kind: "neutral" },
  { code: "CLAIM", label: "认领", kind: "primary" },
  { code: "UNCLAIM", label: "取消认领", kind: "neutral" },
];

const visibleActions = computed(() =>
  actionConfig.filter((action) => props.allowedActions.includes(action.code)),
);
const needsRejectTarget = computed(() => props.allowedActions.includes("REJECT"));
const needsDirectSendTarget = computed(() => props.allowedActions.includes("DIRECT_SEND"));
const needsTargetUser = computed(() =>
  props.allowedActions.some((action) => action === "TRANSFER" || action === "DELEGATE"),
);
const needsAddSignUsers = computed(() => props.allowedActions.includes("ADD_SIGN"));
const visibleAddSignOptions = computed(() =>
  addSignUserOptions.value.filter((user) =>
    !selectedAddSignUsers.value.some((selected) => selected.userId === user.userId),
  ),
);

async function searchTargetUsers(): Promise<void> {
  selectedTargetUser.value = null;
  await loadUserOptions(targetUserKeyword.value, targetUserOptions);
}

async function searchAddSignUsers(): Promise<void> {
  await loadUserOptions(addSignUserKeyword.value, addSignUserOptions);
}

async function loadUserOptions(
  keyword: string,
  options: Ref<WorkflowUserCandidateResponse[]>,
): Promise<void> {
  const trimmed = keyword.trim();
  if (!trimmed) {
    options.value = [];
    return;
  }
  try {
    options.value = await fetchWorkflowUsers(trimmed, 20);
  } catch {
    options.value = [];
  }
}

function selectTargetUser(user: WorkflowUserCandidateResponse): void {
  selectedTargetUser.value = user;
  targetUserKeyword.value = userLabel(user);
  targetUserOptions.value = [];
}

function selectAddSignUser(user: WorkflowUserCandidateResponse): void {
  if (!selectedAddSignUsers.value.some((selected) => selected.userId === user.userId)) {
    selectedAddSignUsers.value = [...selectedAddSignUsers.value, user];
  }
  addSignUserKeyword.value = "";
  addSignUserOptions.value = [];
}

function removeAddSignUser(userId: string): void {
  selectedAddSignUsers.value = selectedAddSignUsers.value.filter((user) => user.userId !== userId);
}

function userLabel(user: WorkflowUserCandidateResponse): string {
  return `${user.userName}（${user.userId}）`;
}

function userMeta(user: WorkflowUserCandidateResponse): string {
  return [user.userId, user.departmentName].filter(Boolean).join(" / ");
}

function isActionDisabled(action: (typeof actionConfig)[number]): boolean {
  return Boolean(props.submitting) || props.disabledActions.includes(action.code);
}

function submit(action: (typeof actionConfig)[number]): void {
  if (isActionDisabled(action)) return;
  const payload: {
    action: TaskActionCode;
    expectedTaskVersion: number;
    comment: string;
    targetNodeCode?: string;
    targetUserId?: string;
    targetUserName?: string;
    addSignUserIds?: string[];
  } = {
    action: action.code,
    expectedTaskVersion: props.taskVersion,
    comment: comment.value.trim(),
  };
  validationError.value = "";
  if (action.code === "REJECT") {
    if (!rejectTargetNodeCode.value) {
      validationError.value = "请选择驳回节点";
      return;
    }
    payload.targetNodeCode = rejectTargetNodeCode.value;
  } else if (action.requiresTargetNode) {
    if (!directSendTargetNodeCode.value.trim()) {
      validationError.value = "请输入目标节点编码";
      return;
    }
    payload.targetNodeCode = directSendTargetNodeCode.value.trim();
  }
  if (action.requiresTargetUser) {
    if (!selectedTargetUser.value) {
      validationError.value = "请选择目标用户";
      return;
    }
    payload.targetUserId = selectedTargetUser.value.userId;
    if (action.code === "DELEGATE") {
      payload.targetUserName = selectedTargetUser.value.userName;
    }
  }
  if (action.code === "ADD_SIGN") {
    if (!selectedAddSignUsers.value.length) {
      validationError.value = "请选择至少一个加签用户";
      return;
    }
    payload.addSignUserIds = selectedAddSignUsers.value.map((user) => user.userId);
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
        <label v-if="needsRejectTarget" class="field">
          <span>驳回节点</span>
          <select v-model="rejectTargetNodeCode" data-test="reject-target-node">
            <option value="">请选择</option>
            <option
              v-for="node in rejectTargetNodes"
              :key="node.nodeCode"
              :value="node.nodeCode"
            >
              {{ node.nodeName || node.nodeCode }}
            </option>
          </select>
        </label>
        <label v-if="needsDirectSendTarget" class="field">
          <span>目标节点</span>
          <input v-model="directSendTargetNodeCode" type="text" autocomplete="off" />
        </label>
        <div v-if="needsTargetUser" class="field user-picker">
          <span>目标用户</span>
          <input
            v-model="targetUserKeyword"
            data-test="target-user-search"
            type="text"
            autocomplete="off"
            role="combobox"
            aria-label="目标用户"
            placeholder="姓名 / 用户 ID"
            @input="searchTargetUsers"
          />
          <div v-if="targetUserOptions.length" class="user-options" role="listbox">
            <button
              v-for="user in targetUserOptions"
              :key="user.userId"
              type="button"
              class="user-option"
              :data-test="`target-user-option-${user.userId}`"
              @click="selectTargetUser(user)"
            >
              <span>{{ user.userName }}</span>
              <small>{{ userMeta(user) }}</small>
            </button>
          </div>
          <p v-if="selectedTargetUser" class="selected-user" data-test="selected-target-user">
            {{ userLabel(selectedTargetUser) }}
          </p>
        </div>
        <div v-if="needsAddSignUsers" class="field user-picker">
          <span>加签用户</span>
          <div v-if="selectedAddSignUsers.length" class="selected-users">
            <span v-for="user in selectedAddSignUsers" :key="user.userId" class="user-chip">
              {{ userLabel(user) }}
              <button type="button" aria-label="移除加签用户" @click="removeAddSignUser(user.userId)">×</button>
            </span>
          </div>
          <input
            v-model="addSignUserKeyword"
            data-test="add-sign-user-search"
            type="text"
            autocomplete="off"
            role="combobox"
            aria-label="加签用户"
            placeholder="姓名 / 用户 ID"
            @input="searchAddSignUsers"
          />
          <div v-if="visibleAddSignOptions.length" class="user-options" role="listbox">
            <button
              v-for="user in visibleAddSignOptions"
              :key="user.userId"
              type="button"
              class="user-option"
              :data-test="`add-sign-user-option-${user.userId}`"
              @click="selectAddSignUser(user)"
            >
              <span>{{ user.userName }}</span>
              <small>{{ userMeta(user) }}</small>
            </button>
          </div>
        </div>
      </div>
      <p v-if="validationError" class="validation-error" role="alert">{{ validationError }}</p>
      <div class="action-row" role="group" aria-label="可用办理动作">
        <button
          v-for="action in visibleActions"
          :key="action.code"
          type="button"
          class="action-button"
          :class="`is-${action.kind}`"
          :data-test="`action-${action.code.toLowerCase().replace('_', '-')}`"
          :disabled="isActionDisabled(action)"
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
  position: relative;
  display: grid;
  gap: 6px;
  color: #374151;
  font-size: 13px;
}

textarea,
input,
select {
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
select:focus,
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

.user-picker {
  min-width: 0;
}

.user-options {
  display: grid;
  max-height: 220px;
  overflow-y: auto;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(17, 24, 39, 0.12);
  z-index: 2;
}

.user-option {
  display: grid;
  gap: 2px;
  border: 0;
  border-bottom: 1px solid #eef2f7;
  padding: 9px 10px;
  background: #fff;
  color: #111827;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.user-option:hover {
  background: #f8fafc;
}

.user-option small {
  color: #6b7280;
  font-size: 12px;
}

.selected-user,
.selected-users {
  margin: 0;
}

.selected-user {
  color: #2563eb;
  font-weight: 600;
}

.selected-users {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 26px;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 2px 8px;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 600;
}

.user-chip button {
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
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

.validation-error {
  margin: 10px 0 0;
  color: #b91c1c;
  font-size: 13px;
}
</style>