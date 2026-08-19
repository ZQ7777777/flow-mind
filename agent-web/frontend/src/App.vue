<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import RequirementEditor from "./components/RequirementEditor.vue";
import ProcessPreview from "./components/ProcessPreview.vue";
import CodeGenerationPanel from "./components/CodeGenerationPanel.vue";
import GenerationProgress from "./components/GenerationProgress.vue";
import ManagementLists from "./components/ManagementLists.vue";
import AdminLogin from "./components/AdminLogin.vue";
import { useResizablePanels } from "./composables/useResizablePanels";
import { AUTHENTICATION_REQUIRED_EVENT } from "./api";
import { useAuthStore } from "./stores/auth";
import { useWorkflowStore } from "./stores/workflow";
import { isResetSessionDisabled, nextStepMessage } from "./workflow-presentation";

const store = useWorkflowStore();
const auth = useAuthStore();
const targetRoot = ref("");
const message = ref("");
const activeTab = ref("requirement");
const generationTargetRoot = ref("");
const compactionSummaryVisible = ref(false);
const sessionHistoryVisible = ref(false);
const {
  workspaceGrid,
  resizingPanels,
  workspaceGridStyle,
  separatorValueNow,
  startPanelResize,
  resizePanelsByKeyboard,
} = useResizablePanels();

const steps = [
  { state: "COLLECTING", title: "采集需求" },
  { state: "REQUIREMENT_REVIEW", title: "确认需求" },
  { state: "PROCESS_REVIEW", title: "预览流程" },
  { state: "PROCESS_ACTIVE", title: "发布激活" },
  { state: "CODE_REVIEW", title: "生成代码" },
];
const stepIndex = computed(() => {
  const state = store.state;
  if (state === "COLLECTING") return 0;
  if (state === "REQUIREMENT_REVIEW") return 1;
  if (["PROCESS_PROVISIONING", "PROCESS_PROVISION_FAILED", "PROCESS_REVIEW"].includes(state || "")) return 2;
  // if (["PROCESS_ACTIVATING", "PROCESS_ACTIVATION_FAILED", "PROCESS_ACTIVE"].includes(state || "")) return 3;
  // return 4;
  if (["PROCESS_ACTIVATING", "PROCESS_ACTIVATION_FAILED"].includes(state || "")) return 3;
  if (["PROCESS_ACTIVE", "CODE_GENERATING"].includes(state || "")) return 4;
  return 5;
});
const processing = computed(() => ["PROCESS_PROVISIONING", "PROCESS_ACTIVATING", "CODE_GENERATING", "CODE_VERIFYING", "CODE_REVIEWING", "CODE_REPAIRING", "WRITING_ARTIFACTS", "BUSINESS_ENTRY_CONFIGURING"].includes(store.state || ""));

watch(() => auth.user, async (user) => {
  if (!user) {
    store.clearForAuthentication();
    return;
  }
  try {
    await store.initialize(user);
  } catch (cause) {
    store.error = cause instanceof Error ? cause.message : "工作台初始化失败";
  }
});
watch(() => store.snapshot?.targetRoot, (value) => { if (value) generationTargetRoot.value = value; }, { immediate: true });
watch(() => store.defaultTargetRoot, (value) => { if (value && !targetRoot.value) targetRoot.value = value; }, { immediate: true });
watch(() => store.state, (value) => { if (["CODE_GENERATING", "CODE_VERIFYING", "CODE_REVIEWING", "CODE_REPAIRING", "CODE_REVIEW", "CODE_PIPELINE_FAILED", "WRITING_ARTIFACTS", "ARTIFACT_WRITE_FAILED", "BUSINESS_ENTRY_CONFIGURING", "BUSINESS_ENTRY_CONFIG_FAILED", "COMPLETED"].includes(value || "")) activeTab.value = "code"; });
watch(() => store.lastCompaction, (compaction) => {
  if (!compaction) return;
  const tokensBefore = formatTokens(compaction.tokensBefore);
  const tokensAfter = formatTokens(compaction.tokensAfterEstimate);
  const tokensReduced = formatTokens(compaction.tokensReducedEstimate);
  const details = tokensBefore && tokensAfter && tokensReduced
    ? `：压缩前约 ${tokensBefore} tokens，压缩后约 ${tokensAfter} tokens，减少约 ${tokensReduced} tokens`
    : tokensBefore && compaction.summaryTokens !== undefined
      ? `：压缩前约 ${tokensBefore} tokens，摘要约 ${formatTokens(compaction.summaryTokens)} tokens`
      : "";
  ElMessage.info(`会话上下文已自动压缩${details}${compaction.summary ? "，可在会话面板查看摘要" : ""}`);
});


function handleAuthenticationRequired(): void {
  auth.clear();
  store.clearForAuthentication();
}

onMounted(() => {
  window.addEventListener(AUTHENTICATION_REQUIRED_EVENT, handleAuthenticationRequired);
  void auth.initialize();
});
onBeforeUnmount(() => {
  window.removeEventListener(AUTHENTICATION_REQUIRED_EVENT, handleAuthenticationRequired);
  store.disconnect();
});

async function logout(): Promise<void> {
  await auth.logout();
  store.clearForAuthentication();
}

async function createSession(): Promise<void> {
  try { await store.createSession(targetRoot.value); } catch { /* store exposes error */ }
}

async function createQualityGateFixture(): Promise<void> {
  try { await store.createQualityGateFixture(targetRoot.value || store.defaultTargetRoot); } catch { /* store exposes error */ }
}

async function send(): Promise<void> {
  const content = message.value.trim();
  if (!content) return;
  message.value = "";
  try { await store.sendMessage(content); } catch { message.value = content; }
}

async function action(operation: () => Promise<void>, success: string): Promise<void> {
  try {
    await operation();
    ElMessage.success(success);
  } catch { /* store exposes error */ }
}

async function resetCurrentSession(): Promise<void> {
  message.value = "";
  activeTab.value = "requirement";
  try {
    await store.resetSession();
    ElMessage.success("当前会话已重置，可重新收集需求并创建流程");
  } catch { /* store exposes error */ }
}

async function reopenRequirementFromProcessFailure(): Promise<void> {
  try {
    await store.reopenRequirementFromProcessFailure();
    activeTab.value = "requirement";
    ElMessage.success("已回退到需求预览；历史会话和对话记录已保留");
  } catch { /* store exposes error */ }
}

function handleSessionOpened(): void {
  sessionHistoryVisible.value = false;
  activeTab.value = [
    "CODE_GENERATING", "CODE_VERIFYING", "CODE_REVIEWING", "CODE_REPAIRING",
    "CODE_REVIEW", "CODE_PIPELINE_FAILED", "WRITING_ARTIFACTS", "ARTIFACT_WRITE_FAILED",
    "BUSINESS_ENTRY_CONFIGURING", "BUSINESS_ENTRY_CONFIG_FAILED", "COMPLETED",
  ].includes(store.state || "") ? "code" : "requirement";
}

function formatTokens(value: number | undefined): string {
  return typeof value === "number" && Number.isFinite(value) ? value.toLocaleString() : "";
}
</script>

<template>
  <AdminLogin v-if="auth.initialized && !auth.authenticated" />
  <main v-else-if="!auth.initialized" class="auth-loading" aria-live="polite">正在验证管理员身份...</main>
  <div v-else class="app-shell">
    <header class="topbar">
      <div class="brand">
        <div class="brand-mark">FM</div>
        <div>
        <span class="eyebrow">FLOW MIND</span>
          <h1>流程生成工作台</h1>
        </div>
      </div>
      <div class="user-tools">
        <span :class="['connection-dot', { online: store.connected }]"></span>
        <span class="connection-text">{{ store.connected ? "实时连接" : "连接中" }}</span>
        <div class="authenticated-user">
          <strong>{{ auth.user?.realName }}</strong>
          <span>{{ auth.user?.departmentName }} · {{ auth.user?.username }}</span>
        </div>
        <el-button text class="logout-button" @click="logout">退出</el-button>
      </div>
    </header>

    <main v-if="!store.snapshot" class="welcome">
      <div class="welcome-copy">
        <span class="eyebrow">AGENT WEB · M0–M3</span>
        <h2>从一句业务想法，<br />到可发布的流程定义。</h2>
        <p>通过对话补齐业务规则、发布流程定义，并在受控目标工程中生成可预览的业务代码与测试。</p>
      </div>
      <div class="create-card">
        <div class="card-index">01</div>
        <h3>新建 Agent 会话</h3>
        <p>目标工程可暂时留空；流程激活后启动 M3 生成时必须通过目标契约校验。</p>
        <el-input v-model="targetRoot" placeholder="目标工程绝对路径（可选）" />
        <el-button type="primary" size="large" :loading="store.busy" @click="createSession">
          开始采集需求
        </el-button>
        <el-button aria-label="查看历史会话" size="large" :disabled="store.busy" @click="sessionHistoryVisible = true">
          查看历史会话
        </el-button>
        <el-button
          v-if="store.currentUser?.userId === 'u_admin_01'"
          type="warning"
          plain
          size="large"
          :loading="store.busy"
          @click="createQualityGateFixture"
        >创建入金申请质量门禁测试</el-button>
        <el-alert v-if="store.error" type="error" :title="store.error" :closable="false" />
      </div>
    </main>

    <main v-else class="workspace">
      <section class="workflow-header">
        <div>
          <span class="eyebrow">当前业务</span>
          <h2>{{ store.snapshot.businessName || "尚未命名的业务流程" }}</h2>
          <code>{{ store.snapshot.sessionId }}</code>
        </div>
        <el-steps :active="stepIndex" finish-status="success" align-center>
          <el-step v-for="step in steps" :key="step.state" :title="step.title" />
        </el-steps>
        <el-tag :type="store.state === 'PROCESS_ACTIVE' ? 'success' : processing ? 'warning' : 'info'" effect="dark">
          {{ store.state }}
        </el-tag>
        <el-popconfirm
          title="将清空当前需求与对话，并开始新的 Pi 会话；已创建的平台流程不会被删除。"
          confirm-button-text="确认重置"
          cancel-button-text="取消"
          @confirm="resetCurrentSession"
        >
          <template #reference>
            <el-button
              type="danger"
              plain
              size="small"
              :disabled="isResetSessionDisabled(store.allowedActions, store.busy)"
            >重置当前会话</el-button>
          </template>
        </el-popconfirm>
      </section>

      <el-alert v-if="store.error" class="global-error" type="error" :title="store.error" show-icon @close="store.error = ''" />

      <div ref="workspaceGrid" class="workspace-grid" :style="workspaceGridStyle">
        <section class="conversation-panel">
          <div class="panel-heading">
            <div>
              <span class="eyebrow">需求对话</span>
              <h3>和 Agent 一起把问题说清楚</h3>
            </div>
            <span v-if="processing" class="thinking">平台处理中…</span>
          </div>
          <div v-if="store.lastCompaction?.summary" class="compaction-summary-card">
            <div>
              <strong>最近一次上下文压缩摘要</strong>
              <span>压缩前约 {{ formatTokens(store.lastCompaction.tokensBefore) }} tokens，压缩后约 {{ formatTokens(store.lastCompaction.tokensAfterEstimate) }} tokens</span>
            </div>
            <el-button size="small" text type="primary" @click="compactionSummaryVisible = true">查看摘要</el-button>
          </div>
          <div class="messages">
            <div v-if="!store.snapshot.messages.length" class="empty-chat">
              <div class="prompt-icon">✦</div>
              <p>例如：我要做一个入金申请流程</p>
            </div>
            <div
              v-for="item in store.snapshot.messages"
              :key="item.id"
              :class="['message', item.role]"
            >
              <span class="message-role">{{ item.role === "user" ? "你" : item.role === "assistant" ? "Agent" : "工具" }}</span>
              <p>{{ item.content }}</p>
            </div>
            <div v-if="store.streamingText" class="message assistant streaming">
              <span class="message-role">Agent</span>
              <p>{{ store.streamingText }}<span class="cursor"></span></p>
            </div>
          </div>
          <div class="composer">
            <el-input
              v-model="message"
              type="textarea"
              :rows="3"
              resize="none"
              placeholder="描述业务流程，或回答 Agent 的问题…"
              :disabled="store.state !== 'COLLECTING'"
              @keydown.ctrl.enter.prevent="send"
            />
            <div class="composer-actions">
              <span>Ctrl + Enter 发送</span>
              <el-button
                type="primary"
                :loading="store.busy"
                :disabled="store.state !== 'COLLECTING' || !message.trim()"
                @click="send"
              >发送</el-button>
            </div>
          </div>
        </section>

        <div
          :class="['panel-divider', { dragging: resizingPanels }]"
          role="separator"
          aria-label="调整 Agent 对话与功能面板宽度"
          aria-orientation="vertical"
          aria-valuemin="0"
          aria-valuemax="100"
          :aria-valuenow="separatorValueNow"
          tabindex="0"
          @pointerdown="startPanelResize"
          @keydown="resizePanelsByKeyboard"
        ></div>

        <section class="review-panel">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="需求预览" name="requirement">
              <RequirementEditor
                v-if="store.snapshot.requirement"
                :revision="store.snapshot.requirement"
                :disabled="store.state !== 'REQUIREMENT_REVIEW' || store.busy"
                @save="action(() => store.saveRequirement($event), '需求修改已保存')"
              />
              <div v-else class="review-empty">
                <div class="empty-orbit"></div>
                <h3>结构化需求将在这里出现</h3>
                <p>Agent 收集完角色、字段、材料与审批规则后，会提交一份可编辑的需求快照。</p>
              </div>
            </el-tab-pane>
            <el-tab-pane label="流程预览" name="process" :disabled="!store.snapshot.processPreview">
              <ProcessPreview v-if="store.snapshot.processPreview" :preview="store.snapshot.processPreview" />
            </el-tab-pane>
            <el-tab-pane label="代码预览" name="code" :disabled="!store.snapshot.activeGeneration">
              <CodeGenerationPanel
                v-if="store.snapshot.activeGeneration?.manifest"
                :generation="store.snapshot.activeGeneration"
              />
              <GenerationProgress v-else-if="store.state === 'CODE_GENERATING'" />
              <div v-else class="review-empty">
                <div class="empty-orbit"></div>
                <h3>尚无可预览代码</h3>
                <p>生成结果只写入 Agent 暂存区，不会修改真实目标工程。</p>
              </div>
            </el-tab-pane>
            <el-tab-pane label="管理清单" name="management">
              <ManagementLists @opened="handleSessionOpened" />
            </el-tab-pane>
          </el-tabs>
        </section>
      </div>

      <footer class="action-bar">
        <div>
          <span class="eyebrow">下一步</span>
          <p>{{ nextStepMessage(store.state) }}</p>
        </div>
        <div class="primary-actions">
          <template v-if="store.state === 'REQUIREMENT_REVIEW'">
            <el-button :disabled="store.busy" @click="action(store.reopenRequirement, '已退回需求采集')">继续补充</el-button>
            <el-button
              type="primary"
              :loading="store.busy"
              :disabled="!store.snapshot.requirement?.readyForReview"
              @click="action(store.confirmRequirement, '门禁一已通过，正在创建流程')"
            >确认需求并创建流程</el-button>
          </template>
          <template v-else-if="store.state === 'PROCESS_REVIEW'">
            <el-button
              type="primary"
              :loading="store.busy"
              :disabled="!store.snapshot.processPreview?.validation.valid"
              @click="action(store.confirmProcess, '门禁二已通过，正在发布并激活')"
            >确认流程并激活</el-button>
            <el-button
              v-if="store.allowedActions.includes('REOPEN_REQUIREMENT_FROM_PROCESS_FAILURE')"
              type="warning"
              :loading="store.busy"
              @click="reopenRequirementFromProcessFailure"
            >回退需求预览</el-button>
          </template>
          <el-button
            v-else-if="store.state === 'PROCESS_PROVISION_FAILED' || (store.state === 'PROCESS_ACTIVATION_FAILED' && store.allowedActions.includes('RETRY_PROCESS'))"
            type="danger"
            :loading="store.busy"
            @click="action(store.retryProcess, '已按失败步骤重新执行')"
          >重试失败步骤</el-button>
          <el-button
            v-else-if="store.state === 'PROCESS_ACTIVATION_FAILED' && store.allowedActions.includes('REOPEN_REQUIREMENT_FROM_PROCESS_FAILURE')"
            type="warning"
            :loading="store.busy"
            @click="reopenRequirementFromProcessFailure"
          >回退需求预览</el-button>
          <template v-else-if="store.state === 'PROCESS_ACTIVE'">
            <el-input v-model="generationTargetRoot" class="target-root-input" placeholder="③ business-base 绝对路径" />
            <el-button type="success" :loading="store.busy" @click="action(() => store.startGeneration(generationTargetRoot), 'M3 代码生成已启动')">生成业务发起代码</el-button>
          </template>
          <el-button v-else-if="store.state === 'CODE_GENERATING'" type="danger" plain :loading="store.busy" @click="action(store.cancelGeneration, '已取消生成')">取消生成</el-button>
          <el-button v-else-if="['CODE_REVIEW','CODE_PIPELINE_FAILED'].includes(store.state || '')" type="primary" :loading="store.busy" @click="action(store.regenerate, '已启动全新生成任务')">重新生成</el-button>
        </div>
      </footer>

      <el-dialog v-model="compactionSummaryVisible" title="最近一次上下文压缩摘要" width="760px">
        <pre class="compaction-summary-text">{{ store.lastCompaction?.summary }}</pre>
      </el-dialog>
    </main>
    <el-dialog v-model="sessionHistoryVisible" title="历史会话" width="min(1080px, 92vw)">
      <ManagementLists @opened="handleSessionOpened" />
    </el-dialog>
  </div>
</template>

<style scoped>
.auth-loading { min-height: 100vh; display: grid; place-items: center; color: var(--muted); background: #f3f5f8; }
.authenticated-user { display: grid; gap: 2px; padding-left: 12px; border-left: 1px solid rgba(255, 255, 255, .22); text-align: right; }
.authenticated-user strong { font-size: 13px; }
.authenticated-user span { color: #aeb7ca; font-size: 11px; }
.logout-button { color: #dce3f2; }
.target-root-input { width: min(460px, 42vw); }
.compaction-summary-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--line);
  background: #f7f9ff;
  color: var(--muted);
  font-size: 12px;
}
.compaction-summary-card strong {
  display: block;
  margin-bottom: 2px;
  color: var(--ink);
  font-size: 13px;
}
.compaction-summary-text {
  max-height: 60vh;
  margin: 0;
  padding: 14px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: #f8f9fc;
  color: var(--ink);
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  line-height: 1.6;
}
</style>
