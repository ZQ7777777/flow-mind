<template>
  <section class="sample-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">业务申请</p>
        <h1>{{ context?.processName || "仓单、国债（解）质押申请" }}</h1>
        <p class="subtitle">填写业务信息、完成业务核查并上传发起附件。</p>
      </div>
      <span v-if="context" class="version-badge">V{{ context.definitionVersion ?? "-" }}</span>
    </header>

    <div v-if="loading" class="state-card">正在加载发起上下文…</div>
    <div v-else-if="loadError" class="state-card error">{{ loadError }}</div>
    <div v-else-if="context && !context.startable" class="state-card warning">
      {{ context.disabledReason || "当前流程不可发起" }}
    </div>

    <template v-else>
      <nav class="tabs" aria-label="申请页签">
        <button
          type="button"
          :class="{ active: activeTab === 'form' }"
          @click="activeTab = 'form'"
        >
          申请详情
        </button>
        <button
          type="button"
          :class="{ active: activeTab === 'attachments' }"
          @click="activeTab = 'attachments'"
        >
          影像资料
          <span class="count">{{ totalFileCount }}</span>
        </button>
      </nav>

      <form v-if="activeTab === 'form'" class="content-card" @submit.prevent="submit">
        <section class="section-block">
          <div class="section-title">客户信息</div>
          <div class="account-row">
            <label>
              <span class="required">*</span>期货账号
            </label>
            <div class="account-picker">
              <input
                v-model.trim="accountKeyword"
                class="input"
                placeholder="输入期货账号或客户名称"
                autocomplete="off"
                @input="handleAccountSearch"
                @focus="accountOpen = true"
              >
              <div v-if="accountOpen" class="dropdown">
                <button
                  v-for="account in accounts"
                  :key="account.accountNo"
                  type="button"
                  class="dropdown-item"
                  @click="selectAccount(account)"
                >
                  <strong>{{ account.accountNo }}</strong>
                  <span>{{ account.customerName }}</span>
                </button>
                <div v-if="!accounts.length" class="dropdown-empty">暂无匹配账号</div>
              </div>
            </div>
            <button
              type="button"
              class="text-button"
              :disabled="!selectedAccount || !accountFunds"
              @click="fundDialogOpen = true"
            >
              查看资金详情
            </button>
          </div>
        </section>

        <section class="section-block">
          <div class="section-title">业务信息</div>
          <div class="form-grid">
            <label class="field">
              <span><b class="required">*</b>业务类型</span>
              <select v-model="form.businessType" class="input" @change="recalculate">
                <option value="">请选择</option>
                <option v-for="item in businessTypes" :key="item" :value="item">{{ item }}</option>
              </select>
            </label>

            <label class="field">
              <span><b class="required">*</b>交易所</span>
              <select v-model="form.exchangeCode" class="input" @change="handleExchangeChange">
                <option value="">请选择</option>
                <option
                  v-for="exchange in exchanges"
                  :key="exchange.exchangeCode"
                  :value="exchange.exchangeCode"
                >
                  {{ exchange.exchangeName }}
                </option>
              </select>
            </label>

            <label class="field">
              <span>交易编码</span>
              <input v-model="form.tradingCode" class="input readonly" readonly placeholder="自动带出">
            </label>

            <label class="field field-wide">
              <span><b class="required">*</b>品种</span>
              <select
                v-model="form.productCodes"
                class="input multi-select"
                multiple
                :disabled="!form.exchangeCode"
                @change="handleProductChange"
              >
                <option
                  v-for="product in products"
                  :key="product.productCode"
                  :value="product.productCode"
                >
                  {{ product.productCode }} - {{ product.productName }}
                </option>
              </select>
            </label>

            <label class="field">
              <span><b class="required">*</b>数量（张）</span>
              <input
                v-model.number="form.quantity"
                class="input number"
                type="number"
                min="1"
                step="1"
                @input="recalculate"
              >
            </label>

            <label class="field">
              <span><b class="required">*</b>合约乘数</span>
              <input
                v-model.number="form.contractMultiplier"
                class="input number"
                type="number"
                min="1"
                step="1"
                @input="recalculate"
              >
            </label>

            <label class="field">
              <span><b class="required">*</b>质押品单位数量</span>
              <input
                v-model.number="form.pledgeUnitQuantity"
                class="input number"
                type="number"
                min="1"
                step="1"
                @input="recalculate"
              >
            </label>

            <label class="field">
              <span><b class="required">*</b>昨结算价</span>
              <input
                v-model.number="form.previousSettlementPrice"
                class="input number"
                type="number"
                min="0.0001"
                step="0.0001"
                @input="recalculate"
              >
            </label>

            <label class="field">
              <span>金额</span>
              <input :value="formattedAmount" class="input readonly amount" readonly>
            </label>
          </div>
        </section>

        <section class="section-block">
          <div class="section-heading-row">
            <div class="section-title">业务核查</div>
            <button type="button" class="secondary-button" @click="refreshChecks">刷新</button>
          </div>
          <div class="table-wrap">
            <table class="check-table">
              <thead>
                <tr>
                  <th>核查项</th>
                  <th>核查内容</th>
                  <th>核查结果</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in checks" :key="item.key">
                  <td>{{ item.name }}</td>
                  <td>{{ item.description }}</td>
                  <td :class="['check-result', `result-${item.status}`]">{{ item.text }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <footer class="actions">
          <button type="button" class="secondary-button" @click="activeTab = 'attachments'">
            下一步：上传附件
          </button>
          <button type="submit" class="primary-button" :disabled="submitting">
            {{ submitting ? "提交中…" : "提交申请" }}
          </button>
        </footer>
      </form>

      <section v-else class="content-card attachments-layout">
        <aside class="attachment-contracts">
          <div class="section-title">附件清单</div>
          <button
            v-for="rule in attachmentRules"
            :key="rule.attachmentCode"
            type="button"
            :class="['contract-item', { active: selectedAttachmentCode === rule.attachmentCode }]"
            @click="selectedAttachmentCode = rule.attachmentCode"
          >
            <div class="contract-name">
              <span v-if="rule.required" class="required">*</span>
              {{ rule.attachmentName || rule.attachmentCode }}
            </div>
            <div class="contract-meta">
              {{ rule.minCount }}-{{ rule.maxCount }} 个 ·
              {{ rule.allowedExtensions.join("/") }} ·
              单个 ≤ {{ formatMb(rule.maxSizeBytes) }}
            </div>
          </button>
        </aside>

        <div class="attachment-preview">
          <template v-if="selectedRule">
            <div class="upload-title-row">
              <div>
                <h2>{{ selectedRule.attachmentName || selectedRule.attachmentCode }}</h2>
                <p>{{ selectedRule.description || "请按照附件契约上传文件。" }}</p>
              </div>
              <label class="primary-button file-button">
                选择文件
                <input
                  class="file-input"
                  type="file"
                  multiple
                  :accept="acceptFor(selectedRule)"
                  @change="handleFiles"
                >
              </label>
            </div>

            <div
              class="drop-zone"
              @dragover.prevent
              @drop.prevent="handleDrop"
            >
              <div class="upload-icon">⇧</div>
              <strong>拖拽文件到此处上传</strong>
              <span>最多 {{ selectedRule.maxCount }} 个，支持 {{ selectedRule.allowedExtensions.join(" / ") }}</span>
            </div>

            <div class="file-list">
              <article
                v-for="(file, index) in filesByAttachmentCode[selectedRule.attachmentCode] || []"
                :key="`${file.name}-${file.size}-${index}`"
                class="file-card"
              >
                <div class="file-icon">{{ extensionOf(file.name).toUpperCase() || "FILE" }}</div>
                <div class="file-info">
                  <strong>{{ file.name }}</strong>
                  <span>{{ formatFileSize(file.size) }}</span>
                </div>
                <button type="button" class="delete-button" @click="removeFile(index)">删除</button>
              </article>
              <div v-if="!(filesByAttachmentCode[selectedRule.attachmentCode] || []).length" class="empty-files">
                暂无已上传文件
              </div>
            </div>
          </template>
        </div>

        <footer class="actions attachment-actions">
          <button type="button" class="secondary-button" @click="activeTab = 'form'">返回申请详情</button>
          <button type="button" class="primary-button" :disabled="submitting" @click="submit">
            {{ submitting ? "提交中…" : "提交申请" }}
          </button>
        </footer>
      </section>
    </template>

    <div v-if="fundDialogOpen && accountFunds" class="modal-backdrop" @click.self="fundDialogOpen = false">
      <section class="fund-dialog" role="dialog" aria-modal="true" aria-label="客户资金详情">
        <header>
          <h2>客户资金详情</h2>
          <button type="button" @click="fundDialogOpen = false">×</button>
        </header>
        <div class="fund-grid">
          <div><span>期货账号</span><strong>{{ accountFunds.accountNo }}</strong></div>
          <div><span>客户名称</span><strong>{{ accountFunds.customerName }}</strong></div>
          <div><span>当前权益</span><strong>{{ money(accountFunds.currentEquity) }}</strong></div>
          <div><span>可用资金</span><strong>{{ money(accountFunds.availableFunds) }}</strong></div>
          <div><span>质押金额</span><strong>{{ money(accountFunds.pledgeAmount) }}</strong></div>
          <div><span>实有货币资金</span><strong>{{ money(accountFunds.actualCash) }}</strong></div>
        </div>
      </section>
    </div>

    <div v-if="message" :class="['toast', message.type]">{{ message.text }}</div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  getAccountFunds,
  getExchanges,
  getStartContext,
  getTradingCodes,
  searchFuturesAccounts,
  searchFuturesProducts,
  startSubmit,
  type AccountFund,
  type Exchange,
  type FuturesAccount,
  type FuturesProduct,
  type WorkflowStartAttachmentRule,
  type WorkflowStartContext,
} from "../../../api/generated/sample/sample";

const PROCESS_CODE = "entry_application";
const DEFAULT_ATTACHMENT_RULE: WorkflowStartAttachmentRule = {
  attachmentCode: "银行回单",
  attachmentName: "银行回单",
  description: "银行回单",
  required: true,
  minCount: 1,
  maxCount: 5,
  maxSizeBytes: 10 * 1024 * 1024,
  allowedExtensions: ["jpg", "png", "pdf"],
  sortOrder: 0,
};

const businessTypes = ["仓单质押", "仓单解质押", "国债质押", "国债解质押"];
const loading = ref(true);
const submitting = ref(false);
const loadError = ref("");
const context = ref<WorkflowStartContext | null>(null);
const activeTab = ref<"form" | "attachments">("form");
const accounts = ref<FuturesAccount[]>([]);
const exchanges = ref<Exchange[]>([]);
const products = ref<FuturesProduct[]>([]);
const selectedAccount = ref<FuturesAccount | null>(null);
const accountFunds = ref<AccountFund | null>(null);
const accountKeyword = ref("");
const accountOpen = ref(false);
const fundDialogOpen = ref(false);
const selectedAttachmentCode = ref("银行回单");
const filesByAttachmentCode = reactive<Record<string, File[]>>({});
const message = ref<{ type: "success" | "error"; text: string } | null>(null);

const form = reactive({
  businessType: "",
  exchangeCode: "",
  tradingCode: "",
  productCodes: [] as string[],
  quantity: 1,
  contractMultiplier: 0,
  pledgeUnitQuantity: 0,
  previousSettlementPrice: 0,
  amount: 0,
});

const attachmentRules = computed(() => {
  const rules = context.value?.attachments?.length
    ? [...context.value.attachments].sort((a, b) => a.sortOrder - b.sortOrder)
    : [DEFAULT_ATTACHMENT_RULE];
  return rules;
});

const selectedRule = computed(() =>
  attachmentRules.value.find((rule) => rule.attachmentCode === selectedAttachmentCode.value)
  ?? attachmentRules.value[0],
);

const totalFileCount = computed(() =>
  Object.values(filesByAttachmentCode).reduce((sum, files) => sum + files.length, 0),
);

const formattedAmount = computed(() => {
  if (!Number.isFinite(form.amount) || form.amount === 0) return "";
  return new Intl.NumberFormat("zh-CN", {
    minimumFractionDigits: 4,
    maximumFractionDigits: 4,
  }).format(form.amount);
});

type CheckStatus = "pass" | "fail" | "skip" | "missing";

function result(status: CheckStatus): { status: CheckStatus; text: string } {
  const textMap: Record<CheckStatus, string> = {
    pass: "通过",
    fail: "不通过",
    skip: "不需核查",
    missing: "缺少数据，无法核查",
  };
  return { status, text: textMap[status] };
}

const checks = computed(() => {
  const pledge = form.businessType === "仓单质押" || form.businessType === "国债质押";
  const release = form.businessType === "仓单解质押" || form.businessType === "国债解质押";
  const funds = accountFunds.value;
  const amount = form.amount;
  const hasData = !!funds && Number.isFinite(amount) && amount !== 0;

  let pledgeResult = result("skip");
  let releaseResult = result("skip");
  let dceResult = result("skip");
  let czceResult = result("skip");

  if (pledge) {
    if (!hasData || !funds) {
      pledgeResult = result("missing");
    } else {
      const totalPledge = funds.pledgeAmount + amount;
      const passed =
        funds.currentEquity + amount >= 1.25 * totalPledge
        || funds.actualCash >= 0.25 * totalPledge;
      pledgeResult = result(passed ? "pass" : "fail");
    }
  }

  if (release) {
    releaseResult = !hasData || !funds
      ? result("missing")
      : result(funds.availableFunds + amount >= 0 ? "pass" : "fail");
  }

  if (pledge && form.exchangeCode === "DCE") {
    const exchangeFund = funds?.exchangeFunds.find((item) => item.exchangeCode === "DCE");
    dceResult = !hasData || !exchangeFund
      ? result("missing")
      : result(exchangeFund.pledgeAmount + amount <= exchangeFund.positionMargin ? "pass" : "fail");
  }

  if (pledge && form.exchangeCode === "CZCE") {
    const exchangeFund = funds?.exchangeFunds.find((item) => item.exchangeCode === "CZCE");
    czceResult = !hasData || !exchangeFund
      ? result("missing")
      : result(exchangeFund.pledgeAmount + amount <= 1.2 * exchangeFund.positionMargin ? "pass" : "fail");
  }

  return [
    {
      key: "pledge",
      name: "满足质押要求",
      description: "当前权益 + 本次金额 ≥ 1.25 ×（质押金额 + 本次金额），或实有货币资金 ≥ 1/4 ×（质押金额 + 本次金额）",
      ...pledgeResult,
    },
    {
      key: "release",
      name: "满足解质押要求",
      description: "可用资金 + 本次金额 ≥ 0",
      ...releaseResult,
    },
    {
      key: "dce",
      name: "满足大商所特定要求",
      description: "大商所质押金额 + 本次金额 ≤ 大商所持仓保证金",
      ...dceResult,
    },
    {
      key: "czce",
      name: "满足郑商所特定要求",
      description: "郑商所质押金额 + 本次金额 ≤ 1.2 × 郑商所持仓保证金",
      ...czceResult,
    },
  ];
});

onMounted(async () => {
  try {
    const [startContext, exchangeList, accountList] = await Promise.all([
      getStartContext(PROCESS_CODE),
      getExchanges(),
      searchFuturesAccounts(),
    ]);
    context.value = startContext;
    exchanges.value = exchangeList;
    accounts.value = accountList;
    selectedAttachmentCode.value =
      startContext.attachments?.[0]?.attachmentCode || DEFAULT_ATTACHMENT_RULE.attachmentCode;
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : "页面加载失败";
  } finally {
    loading.value = false;
  }
});

let accountSearchTimer: ReturnType<typeof setTimeout> | null = null;
function handleAccountSearch(): void {
  selectedAccount.value = null;
  accountFunds.value = null;
  accountOpen.value = true;
  if (accountSearchTimer) clearTimeout(accountSearchTimer);
  accountSearchTimer = setTimeout(async () => {
    try {
      accounts.value = await searchFuturesAccounts(accountKeyword.value);
    } catch (error) {
      notify("error", error instanceof Error ? error.message : "账号查询失败");
    }
  }, 180);
}

async function selectAccount(account: FuturesAccount): Promise<void> {
  selectedAccount.value = account;
  accountKeyword.value = `${account.accountNo} - ${account.customerName}`;
  accountOpen.value = false;
  try {
    accountFunds.value = await getAccountFunds(account.accountNo);
    await refreshTradingCode();
  } catch (error) {
    notify("error", error instanceof Error ? error.message : "客户资金加载失败");
  }
}

async function handleExchangeChange(): Promise<void> {
  form.tradingCode = "";
  form.productCodes = [];
  products.value = [];
  form.contractMultiplier = 0;
  form.pledgeUnitQuantity = 0;
  form.previousSettlementPrice = 0;
  recalculate();

  if (!form.exchangeCode) return;

  try {
    products.value = await searchFuturesProducts(form.exchangeCode);
    await refreshTradingCode();
  } catch (error) {
    notify("error", error instanceof Error ? error.message : "交易所关联数据加载失败");
  }
}

async function refreshTradingCode(): Promise<void> {
  if (!selectedAccount.value || !form.exchangeCode) {
    form.tradingCode = "";
    return;
  }
  const codes = await getTradingCodes(selectedAccount.value.accountNo, form.exchangeCode);
  const usable = codes.find((item) => item.tradingStatus === "NORMAL" || item.tradingStatus === "DORMANT");
  form.tradingCode = usable?.tradingCode || "";
}

function handleProductChange(): void {
  const lastCode = form.productCodes[form.productCodes.length - 1];
  const product = products.value.find((item) => item.productCode === lastCode);
  if (!product) return;
  form.contractMultiplier = product.contractMultiplier;
  form.pledgeUnitQuantity = product.pledgeUnitQuantity;
  form.previousSettlementPrice = product.previousSettlementPrice;
  recalculate();
}

function recalculate(): void {
  const quantity = Number(form.quantity);
  const multiplier = Number(form.contractMultiplier);
  const unit = Number(form.pledgeUnitQuantity);
  const price = Number(form.previousSettlementPrice);

  const valid = Number.isInteger(quantity) && quantity > 0
    && Number.isInteger(multiplier) && multiplier > 0
    && Number.isInteger(unit) && unit > 0
    && Number.isFinite(price) && price > 0;

  if (!valid) {
    form.amount = 0;
    return;
  }

  let amount = price * quantity * unit * multiplier * 0.8;
  if (form.businessType === "仓单解质押" || form.businessType === "国债解质押") {
    amount = -amount;
  }
  form.amount = Number(amount.toFixed(4));
}

async function refreshChecks(): Promise<void> {
  if (!selectedAccount.value) {
    notify("error", "请先选择期货账号");
    return;
  }
  try {
    accountFunds.value = await getAccountFunds(selectedAccount.value.accountNo);
    notify("success", "业务核查数据已刷新");
  } catch (error) {
    notify("error", error instanceof Error ? error.message : "核查数据刷新失败");
  }
}

function extensionOf(name: string): string {
  const dot = name.lastIndexOf(".");
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : "";
}

function acceptFor(rule: WorkflowStartAttachmentRule): string {
  return rule.allowedExtensions.map((item) => `.${item.replace(/^\./, "")}`).join(",");
}

function formatMb(bytes: number): string {
  return `${Math.round((bytes / 1024 / 1024) * 100) / 100}MB`;
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

function validateAndAddFiles(fileList: FileList | File[]): void {
  const rule = selectedRule.value;
  if (!rule) return;
  const current = filesByAttachmentCode[rule.attachmentCode] || [];
  const incoming = Array.from(fileList);

  for (const file of incoming) {
    const extension = extensionOf(file.name);
    const allowed = rule.allowedExtensions.map((item) => item.replace(/^\./, "").toLowerCase());
    if (!allowed.includes(extension)) {
      notify("error", `${file.name} 格式不支持`);
      continue;
    }
    if (file.size > rule.maxSizeBytes) {
      notify("error", `${file.name} 超过 ${formatMb(rule.maxSizeBytes)} 限制`);
      continue;
    }
    if (current.length >= rule.maxCount) {
      notify("error", `${rule.attachmentName || rule.attachmentCode}最多上传 ${rule.maxCount} 个文件`);
      break;
    }
    const duplicate = current.some((item) => item.name === file.name && item.size === file.size);
    if (!duplicate) current.push(file);
  }

  filesByAttachmentCode[rule.attachmentCode] = [...current];
}

function handleFiles(event: Event): void {
  const input = event.target as HTMLInputElement;
  if (input.files) validateAndAddFiles(input.files);
  input.value = "";
}

function handleDrop(event: DragEvent): void {
  if (event.dataTransfer?.files) validateAndAddFiles(event.dataTransfer.files);
}

function removeFile(index: number): void {
  const rule = selectedRule.value;
  if (!rule) return;
  const current = [...(filesByAttachmentCode[rule.attachmentCode] || [])];
  current.splice(index, 1);
  filesByAttachmentCode[rule.attachmentCode] = current;
}

function validateForm(): string | null {
  if (!selectedAccount.value) return "请选择期货账号";
  if (!form.businessType) return "请选择业务类型";
  if (!form.exchangeCode) return "请选择交易所";
  if (!form.productCodes.length) return "请选择至少一个品种";
  if (!Number.isInteger(Number(form.quantity)) || Number(form.quantity) <= 0) return "数量必须为正整数";
  if (!Number.isInteger(Number(form.contractMultiplier)) || Number(form.contractMultiplier) <= 0) return "合约乘数必须为正整数";
  if (!Number.isInteger(Number(form.pledgeUnitQuantity)) || Number(form.pledgeUnitQuantity) <= 0) return "质押品单位数量必须为正整数";
  if (!Number.isFinite(Number(form.previousSettlementPrice)) || Number(form.previousSettlementPrice) <= 0) return "昨结算价必须为正数";

  for (const rule of attachmentRules.value) {
    const count = filesByAttachmentCode[rule.attachmentCode]?.length || 0;
    const minimum = rule.required ? Math.max(1, rule.minCount) : rule.minCount;
    if (count < minimum) return `${rule.attachmentName || rule.attachmentCode}至少上传 ${minimum} 个文件`;
    if (count > rule.maxCount) return `${rule.attachmentName || rule.attachmentCode}最多上传 ${rule.maxCount} 个文件`;
  }
  return null;
}

function fieldCode(fieldName: string, fallback: string): string {
  return context.value?.formFields.find((item) => item.fieldName === fieldName)?.fieldCode || fallback;
}

function buildVariables(): Record<string, unknown> {
  const selectedExchange = exchanges.value.find((item) => item.exchangeCode === form.exchangeCode);
  return {
    [fieldCode("期货账号", "futuresAccount")]: selectedAccount.value?.accountNo,
    [fieldCode("业务类型", "businessType")]: form.businessType,
    [fieldCode("交易所", "exchange")]: selectedExchange?.exchangeName || form.exchangeCode,
    [fieldCode("交易编码", "tradingCode")]: form.tradingCode,
    [fieldCode("品种", "products")]: [...form.productCodes],
    [fieldCode("数量（张）", "quantity")]: Number(form.quantity),
    [fieldCode("合约乘数", "contractMultiplier")]: Number(form.contractMultiplier),
    [fieldCode("质押品单位数量", "pledgeUnitQuantity")]: Number(form.pledgeUnitQuantity),
    [fieldCode("昨结算价", "previousSettlementPrice")]: Number(form.previousSettlementPrice),
    [fieldCode("金额", "amount")]: form.amount,
  };
}

function makeIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `sample-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function submit(): Promise<void> {
  const error = validateForm();
  if (error) {
    notify("error", error);
    if (error.includes("上传")) activeTab.value = "attachments";
    return;
  }

  submitting.value = true;
  try {
    const response = await startSubmit(
      PROCESS_CODE,
      { variables: buildVariables() },
      filesByAttachmentCode,
      makeIdempotencyKey(),
    );
    notify("success", `提交成功${response.instanceId ? `，流程实例：${response.instanceId}` : ""}`);
  } catch (error) {
    notify("error", error instanceof Error ? error.message : "提交失败");
  } finally {
    submitting.value = false;
  }
}

function notify(type: "success" | "error", text: string): void {
  message.value = { type, text };
  window.setTimeout(() => {
    if (message.value?.text === text) message.value = null;
  }, 2600);
}

function money(value: number): string {
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    minimumFractionDigits: 2,
  }).format(value);
}
</script>

<style scoped>
.sample-page {
  --primary: #0875c9;
  --primary-dark: #075fa2;
  --border: #d8dee5;
  --soft: #f4f7fa;
  --text: #202934;
  --muted: #6f7a85;
  min-height: 100%;
  padding: 20px;
  color: var(--text);
  background: #f5f7f9;
}

.page-header,
.content-card,
.state-card,
.tabs {
  max-width: 1500px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.page-header h1 {
  margin: 2px 0 5px;
  font-size: 24px;
}

.eyebrow {
  margin: 0;
  color: var(--primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.subtitle {
  margin: 0;
  color: var(--muted);
}

.version-badge {
  padding: 6px 10px;
  border-radius: 999px;
  color: var(--primary-dark);
  background: #eaf5fc;
  font-weight: 700;
}

.state-card,
.content-card {
  border: 1px solid var(--border);
  background: #fff;
}

.state-card {
  padding: 24px;
}

.state-card.error {
  color: #b42318;
}

.state-card.warning {
  color: #9a5a00;
}

.tabs {
  display: flex;
  border: 1px solid var(--border);
  border-bottom: 0;
  background: #fff;
}

.tabs button {
  min-width: 150px;
  padding: 13px 18px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  cursor: pointer;
  font-weight: 700;
}

.tabs button.active {
  color: var(--primary);
  border-bottom-color: var(--primary);
}

.count {
  display: inline-grid;
  min-width: 19px;
  height: 19px;
  margin-left: 6px;
  place-items: center;
  border-radius: 999px;
  color: #fff;
  background: var(--primary);
  font-size: 11px;
}

.content-card {
  min-height: 520px;
  padding: 18px;
}

.section-block + .section-block {
  margin-top: 20px;
}

.section-title {
  margin-bottom: 14px;
  padding-left: 10px;
  border-left: 4px solid var(--primary);
  font-weight: 800;
}

.account-row {
  display: grid;
  grid-template-columns: 100px minmax(260px, 520px) auto;
  align-items: start;
  gap: 10px;
}

.account-row > label {
  padding-top: 9px;
}

.account-picker {
  position: relative;
}

.required {
  margin-right: 3px;
  color: #c53030;
}

.input {
  width: 100%;
  min-height: 36px;
  padding: 7px 10px;
  border: 1px solid #b9c2ca;
  border-radius: 3px;
  outline: none;
  background: #fff;
}

.input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgb(8 117 201 / 12%);
}

.readonly {
  color: #56616c;
  background: #f3f5f7;
}

.amount {
  color: #ad2222;
  font-weight: 800;
  text-align: right;
}

.number {
  text-align: right;
}

.dropdown {
  position: absolute;
  z-index: 10;
  top: calc(100% + 3px);
  right: 0;
  left: 0;
  overflow: auto;
  max-height: 250px;
  border: 1px solid #aeb8c1;
  background: #fff;
  box-shadow: 0 8px 24px rgb(23 43 59 / 14%);
}

.dropdown-item {
  display: grid;
  width: 100%;
  padding: 9px 11px;
  border: 0;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.dropdown-item:hover {
  background: #eef7fd;
}

.dropdown-item span,
.dropdown-empty {
  color: var(--muted);
  font-size: 12px;
}

.dropdown-empty {
  padding: 14px;
}

.text-button {
  padding: 8px 6px;
  border: 0;
  color: var(--primary);
  background: transparent;
  cursor: pointer;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 15px 22px;
}

.field {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.field-wide {
  grid-column: span 2;
}

.multi-select {
  min-height: 76px;
}

.section-heading-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.table-wrap {
  overflow-x: auto;
}

.check-table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
}

.check-table th,
.check-table td {
  padding: 10px 12px;
  border: 1px solid #d9dee2;
}

.check-table th {
  background: #f0f2f4;
}

.check-result {
  min-width: 130px;
  font-weight: 800;
  text-align: center;
}

.result-pass {
  color: #17834b;
}

.result-fail {
  color: #c53030;
}

.result-skip {
  color: #8a949d;
}

.result-missing {
  color: #252b30;
}

.actions {
  display: flex;
  margin-top: 22px;
  justify-content: flex-end;
  gap: 10px;
}

.primary-button,
.secondary-button {
  min-height: 36px;
  padding: 0 16px;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 700;
}

.primary-button {
  border: 1px solid var(--primary);
  color: #fff;
  background: var(--primary);
}

.secondary-button {
  border: 1px solid #aeb7bf;
  color: #37424c;
  background: #fff;
}

.attachments-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.attachment-contracts {
  min-height: 520px;
  padding: 18px 14px;
  border-right: 1px solid var(--border);
  background: #fafbfc;
}

.contract-item {
  width: 100%;
  margin-bottom: 9px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.contract-item.active {
  border-color: var(--primary);
  background: #eef7fd;
}

.contract-name {
  font-weight: 800;
}

.contract-meta {
  margin-top: 5px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}

.attachment-preview {
  min-width: 0;
  padding: 22px;
}

.upload-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.upload-title-row h2 {
  margin: 0 0 5px;
  font-size: 18px;
}

.upload-title-row p {
  margin: 0;
  color: var(--muted);
}

.file-button {
  display: inline-flex;
  align-items: center;
}

.file-input {
  display: none;
}

.drop-zone {
  display: grid;
  min-height: 160px;
  margin-top: 20px;
  place-items: center;
  align-content: center;
  gap: 8px;
  border: 1px dashed #8fb6d1;
  background: #f8fcfe;
  color: #53606a;
}

.upload-icon {
  color: var(--primary);
  font-size: 34px;
}

.file-list {
  margin-top: 18px;
}

.file-card {
  display: grid;
  margin-bottom: 8px;
  padding: 11px 12px;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: #fff;
}

.file-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 4px;
  color: var(--primary);
  background: #eaf5fc;
  font-size: 11px;
  font-weight: 800;
}

.file-info {
  display: grid;
  gap: 4px;
}

.file-info span {
  color: var(--muted);
  font-size: 12px;
}

.delete-button {
  border: 0;
  color: #c53030;
  background: transparent;
  cursor: pointer;
}

.empty-files {
  padding: 40px 12px;
  color: var(--muted);
  text-align: center;
}

.attachment-actions {
  grid-column: 1 / -1;
  margin: 0;
  padding: 14px 18px;
  border-top: 1px solid var(--border);
}

.modal-backdrop {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: grid;
  padding: 20px;
  place-items: center;
  background: rgb(16 24 40 / 42%);
}

.fund-dialog {
  width: min(760px, 100%);
  border: 1px solid #89939c;
  background: #fff;
  box-shadow: 0 20px 50px rgb(15 28 38 / 24%);
}

.fund-dialog header {
  display: flex;
  padding: 11px 14px;
  align-items: center;
  justify-content: space-between;
  color: #fff;
  background: var(--primary);
}

.fund-dialog h2 {
  margin: 0;
  font-size: 16px;
}

.fund-dialog header button {
  border: 0;
  color: #fff;
  background: transparent;
  font-size: 22px;
}

.fund-grid {
  display: grid;
  padding: 20px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 28px;
}

.fund-grid div {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid #edf0f2;
  padding-bottom: 8px;
}

.fund-grid span {
  color: var(--muted);
}

.toast {
  position: fixed;
  z-index: 150;
  top: 24px;
  left: 50%;
  min-width: 280px;
  padding: 11px 16px;
  border: 1px solid #cfd6dc;
  background: #fff;
  box-shadow: 0 8px 24px rgb(23 43 59 / 16%);
  transform: translateX(-50%);
}

.toast.success {
  border-left: 4px solid #17834b;
}

.toast.error {
  border-left: 4px solid #c53030;
}

@media (max-width: 980px) {
  .form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .field-wide {
    grid-column: span 2;
  }

  .attachments-layout {
    grid-template-columns: 240px minmax(0, 1fr);
  }
}

@media (max-width: 680px) {
  .sample-page {
    padding: 10px;
  }

  .account-row,
  .form-grid,
  .field,
  .attachments-layout,
  .fund-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .field-wide {
    grid-column: auto;
  }

  .attachment-contracts {
    min-height: auto;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }

  .attachment-actions {
    grid-column: auto;
  }
}
</style>
