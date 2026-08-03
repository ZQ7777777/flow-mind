<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import RequirementEditor from "./components/RequirementEditor.vue";
import ProcessPreview from "./components/ProcessPreview.vue";
import { useWorkflowStore } from "./stores/workflow";

const store = useWorkflowStore();
const targetRoot = ref("");
const message = ref("");
const activeTab = ref("requirement");

const steps = [
  { state: "COLLECTING", title: "采集需求" },
  { state: "REQUIREMENT_REVIEW", title: "确认需求" },
  { state: "PROCESS_REVIEW", title: "预览流程" },
  { state: "PROCESS_ACTIVE", title: "发布激活" },
];
const stepIndex = computed(() => {
  const state = store.state;
  if (state === "COLLECTING") return 0;
  if (state === "REQUIREMENT_REVIEW") return 1;
  if (["PROCESS_PROVISIONING", "PROCESS_PROVISION_FAILED", "PROCESS_REVIEW"].includes(state || "")) return 2;
  return 3;
});
const processing = computed(() => ["PROCESS_PROVISIONING", "PROCESS_ACTIVATING"].includes(store.state || ""));

onMounted(() => void store.initialize());
onBeforeUnmount(() => store.disconnect());

async function createSession(): Promise<void> {
  try { await store.createSession(targetRoot.value); } catch { /* store exposes error */ }
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
</script>

<template>
  <div class="app-shell">
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
        <el-select
          :model-value="store.currentUser?.userId"
          placeholder="选择演示用户"
          @change="store.selectUser"
        >
          <el-option
            v-for="user in store.users"
            :key="user.userId"
            :label="`${user.userName} · ${user.userId}`"
            :value="user.userId"
          />
        </el-select>
      </div>
    </header>

    <main v-if="!store.snapshot" class="welcome">
      <div class="welcome-copy">
        <span class="eyebrow">AGENT WEB · M0–M2</span>
        <h2>从一句业务想法，<br />到可发布的流程定义。</h2>
        <p>通过对话补齐业务角色、字段、材料和审批规则；确认后自动落地流程草稿，并在人工门禁后发布激活。</p>
      </div>
      <div class="create-card">
        <div class="card-index">01</div>
        <h3>新建 Agent 会话</h3>
        <p>目标工程在 M3 代码生成前才强制校验，本阶段可留空。</p>
        <el-input v-model="targetRoot" placeholder="目标工程绝对路径（可选）" />
        <el-button type="primary" size="large" :loading="store.busy" @click="createSession">
          开始采集需求
        </el-button>
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
            <el-button type="danger" plain size="small" :disabled="store.busy || processing">重置当前会话</el-button>
          </template>
        </el-popconfirm>
      </section>

      <el-alert v-if="store.error" class="global-error" type="error" :title="store.error" show-icon @close="store.error = ''" />

      <div class="workspace-grid">
        <section class="conversation-panel">
          <div class="panel-heading">
            <div>
              <span class="eyebrow">需求对话</span>
              <h3>和 Agent 一起把问题说清楚</h3>
            </div>
            <span v-if="processing" class="thinking">平台处理中…</span>
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
          </el-tabs>
        </section>
      </div>

      <footer class="action-bar">
        <div>
          <span class="eyebrow">下一步</span>
          <p v-if="store.state === 'COLLECTING'">继续对话，直到结构化需求准备完成。</p>
          <p v-else-if="store.state === 'REQUIREMENT_REVIEW'">检查并保存需求，然后完成人工门禁一。</p>
          <p v-else-if="store.state === 'PROCESS_REVIEW'">检查流程图、字段、附件和校验结果，然后完成人工门禁二。</p>
          <p v-else-if="processing">Agent 正在和流程平台协作，请稍候。</p>
          <p v-else-if="store.state === 'PROCESS_ACTIVE'">流程已经发布并激活，M0–M2 验收完成。</p>
          <p v-else>上一步失败，可使用原幂等操作号安全重试。</p>
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
          <el-button
            v-else-if="store.state === 'PROCESS_REVIEW'"
            type="primary"
            :loading="store.busy"
            :disabled="!store.snapshot.processPreview?.validation.valid"
            @click="action(store.confirmProcess, '门禁二已通过，正在发布并激活')"
          >确认流程并激活</el-button>
          <el-button
            v-else-if="['PROCESS_PROVISION_FAILED','PROCESS_ACTIVATION_FAILED'].includes(store.state || '')"
            type="danger"
            :loading="store.busy"
            @click="action(store.retryProcess, '已按失败步骤重新执行')"
          >重试失败步骤</el-button>
          <el-button v-else-if="store.state === 'PROCESS_ACTIVE'" type="success" disabled>✓ M0–M2 已完成</el-button>
        </div>
      </footer>
    </main>
  </div>
</template>
