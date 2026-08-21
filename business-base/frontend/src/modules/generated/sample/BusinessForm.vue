<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import type { WorkflowFieldPermission, WorkflowFormField } from "../../../types/workflow";
import {
  getAccountFunds,
  getExchanges,
  getTradingCodes,
  searchFuturesAccounts,
  searchFuturesProducts,
  type AccountFund,
  type Exchange,
  type FuturesAccount,
  type FuturesProduct,
} from "../../../api/generated/sample/sample";

const props = withDefaults(defineProps<{
  modelValue: Record<string, unknown>;
  fields: WorkflowFormField[];
  fieldPermissions: WorkflowFieldPermission[];
  mode?: "edit" | "readonly";
  disabled?: boolean;
  currentNodeCode?: string;
  checkRefreshable?: boolean;
}>(), { mode: "edit", disabled: false, currentNodeCode: "", checkRefreshable: true });

const emit = defineEmits<{ "update:modelValue": [value: Record<string, unknown>] }>();
const draft = ref<Record<string, unknown>>({ ...props.modelValue });
const emittedModels = new Map<string, number>();
let latestEmissionSequence = 0;
const accounts = ref<FuturesAccount[]>([]);
const exchanges = ref<Exchange[]>([]);
const products = ref<FuturesProduct[]>([]);
const accountFunds = ref<AccountFund>();
const fundDetailOpen = ref(false);
const fundDetailButton = ref<HTMLButtonElement>();
const fundDetailDialog = ref<HTMLElement>();
const loading = ref(false);
const error = ref("");
const initialized = ref(false);
let accountRequestId = 0;
let exchangeRequestId = 0;
let tradingCodeRequestId = 0;
const businessTypes = ["仓单质押", "仓单解质押", "国债质押", "国债解质押"];
const frozenChecks = computed(() => props.currentNodeCode === "delivery_review" || !props.checkRefreshable);
const selectedAccount = computed(() => accounts.value.find(({ accountNo }) => accountNo === value("futuresAccount", "")));
const fundDetailReady = computed(() => Boolean(selectedAccount.value
  && accountFunds.value?.accountNo === selectedAccount.value.accountNo));

function value<T>(fieldCode: string, fallback: T): T {
  return (draft.value[fieldCode] ?? fallback) as T;
}

function update(fieldCode: string, nextValue: unknown): void {
  updateMany({ [fieldCode]: nextValue });
}

function updateMany(nextValues: Record<string, unknown>): void {
  draft.value = { ...draft.value, ...nextValues };
  const emitted = { ...draft.value };
  latestEmissionSequence += 1;
  emittedModels.set(JSON.stringify(emitted), latestEmissionSequence);
  emit("update:modelValue", emitted);
}

watch(() => props.modelValue, (next) => {
  const sequence = emittedModels.get(JSON.stringify(next));
  if (sequence !== undefined && sequence < latestEmissionSequence) return;
  draft.value = { ...next };
  if (sequence === latestEmissionSequence) emittedModels.clear();
}, { deep: true });

function updateFromEvent(fieldCode: string, event: Event, numeric = false): void {
  const raw = (event.target as HTMLInputElement | HTMLSelectElement).value;
  update(fieldCode, numeric && raw !== "" ? Number(raw) : raw);
}

function permission(fieldCode: string): WorkflowFieldPermission | undefined {
  return props.fieldPermissions.find((item) => item.fieldCode === fieldCode);
}

function visible(fieldCode: string): boolean {
  if (["largeAmount", "businessCheckSnapshot"].includes(fieldCode)) return false;
  const runtime = permission(fieldCode);
  const field = props.fields.find((item) => item.fieldCode === fieldCode);
  return runtime?.visible ?? field?.visible ?? true;
}

function readonly(fieldCode: string): boolean {
  const runtime = permission(fieldCode);
  const field = props.fields.find((item) => item.fieldCode === fieldCode);
  return props.disabled || props.mode === "readonly" || field?.editable === false || runtime?.editable === false
    || ["customerName", "tradingCode", "amount", "largeAmount", "businessCheckSnapshot"].includes(fieldCode);
}

function required(fieldCode: string): boolean {
  return permission(fieldCode)?.required ?? props.fields.find((item) => item.fieldCode === fieldCode)?.required ?? false;
}

onMounted(async () => {
  loading.value = true;
  try {
    [accounts.value, exchanges.value] = await Promise.all([searchFuturesAccounts(), getExchanges()]);
    const exchangeCode = value("exchangeCode", "");
    const accountNo = value("futuresAccount", "");
    if (exchangeCode) products.value = await searchFuturesProducts(exchangeCode);
    if (accountNo && !frozenChecks.value) accountFunds.value = await getAccountFunds(accountNo);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "参考数据加载失败";
  } finally {
    initialized.value = true;
    loading.value = false;
  }
});

watch(() => value("futuresAccount", ""), async (accountNo) => {
  if (!initialized.value) return;
  const requestId = ++accountRequestId;
  closeFundDetails(false);
  accountFunds.value = undefined;
  if (!accountNo) {
    updateMany({ customerName: "", tradingCode: "", businessCheckSnapshot: "" });
    return;
  }
  const account = accounts.value.find((item) => item.accountNo === accountNo);
  if (account) updateMany({ customerName: account.customerName, tradingCode: "" });
  try {
    const funds = await getAccountFunds(accountNo);
    if (requestId !== accountRequestId || accountNo !== value("futuresAccount", "")) return;
    accountFunds.value = funds;
    await loadTradingCode();
  } catch (reason) {
    if (requestId !== accountRequestId) return;
    error.value = reason instanceof Error ? reason.message : "客户资金加载失败";
  }
});

watch(() => value("exchangeCode", ""), async (exchangeCode) => {
  if (!initialized.value) return;
  const requestId = ++exchangeRequestId;
  updateMany({
    tradingCode: "",
    productCodes: [],
    contractMultiplier: undefined,
    pledgeUnitQuantity: undefined,
    previousSettlementPrice: undefined,
    amount: 0,
    largeAmount: false,
  });
  products.value = [];
  if (!exchangeCode) return;
  try {
    const loaded = await searchFuturesProducts(exchangeCode);
    if (requestId !== exchangeRequestId || exchangeCode !== value("exchangeCode", "")) return;
    products.value = loaded;
    await loadTradingCode();
  } catch (reason) {
    if (requestId !== exchangeRequestId) return;
    error.value = reason instanceof Error ? reason.message : "交易所关联数据加载失败";
  }
});

watch(() => value<string[]>("productCodes", []).join("\u0000"), (codesKey) => {
  const codes = codesKey ? codesKey.split("\u0000") : [];
  const selected = products.value.find((item) => item.productCode === codes.at(-1));
  if (!selected) return;
  updateMany({
    contractMultiplier: selected.contractMultiplier,
    pledgeUnitQuantity: selected.pledgeUnitQuantity,
    previousSettlementPrice: selected.previousSettlementPrice,
  });
});

watch(() => [
  value("previousSettlementPrice", 0), value("quantity", 0), value("pledgeUnitQuantity", 0),
  value("contractMultiplier", 0), value("businessType", ""),
], () => {
  const price = Number(value("previousSettlementPrice", 0));
  const quantity = Number(value("quantity", 0));
  const unit = Number(value("pledgeUnitQuantity", 0));
  const multiplier = Number(value("contractMultiplier", 0));
  if (![price, quantity, unit, multiplier].every((item) => Number.isFinite(item) && item > 0)) {
    if (value("amount", 0) !== 0 || value("largeAmount", false)) updateMany({ amount: 0, largeAmount: false });
    return;
  }
  const release = ["仓单解质押", "国债解质押"].includes(value("businessType", ""));
  const amount = Number((price * quantity * unit * multiplier * 0.8 * (release ? -1 : 1)).toFixed(4));
  const largeAmount = Math.abs(amount) >= 10_000_000;
  if (value("amount", 0) !== amount || value("largeAmount", false) !== largeAmount) {
    updateMany({ amount, largeAmount });
  }
}, { immediate: true });

async function loadTradingCode(): Promise<void> {
  const accountNo = value("futuresAccount", "");
  const exchangeCode = value("exchangeCode", "");
  if (!accountNo || !exchangeCode) return;
  const requestId = ++tradingCodeRequestId;
  const codes = await getTradingCodes(accountNo, exchangeCode);
  if (requestId !== tradingCodeRequestId || accountNo !== value("futuresAccount", "")
    || exchangeCode !== value("exchangeCode", "")) return;
  update("tradingCode", codes.find(({ tradingStatus }) => ["NORMAL", "DORMANT"].includes(tradingStatus))?.tradingCode || "");
}

async function refreshChecks(): Promise<void> {
  if (frozenChecks.value) return;
  const accountNo = value("futuresAccount", "");
  if (!accountNo) {
    error.value = "请先选择期货账号";
    return;
  }
  try {
    accountFunds.value = await getAccountFunds(accountNo);
    error.value = "";
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "业务核查刷新失败";
  }
}

async function openFundDetails(): Promise<void> {
  if (!fundDetailReady.value) return;
  fundDetailOpen.value = true;
  await nextTick();
  fundDetailDialog.value?.focus();
}

function closeFundDetails(restoreFocus = true): void {
  if (!fundDetailOpen.value) return;
  fundDetailOpen.value = false;
  if (restoreFocus) void nextTick(() => fundDetailButton.value?.focus());
}

function formatMoney(amount: number | undefined): string {
  if (amount === undefined || !Number.isFinite(amount)) return "—";
  const currency = accountFunds.value?.currency || "CNY";
  try {
    return new Intl.NumberFormat("zh-CN", {
      style: "currency",
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${amount.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${currency}`;
  }
}

function currencyName(currency: string | undefined): string {
  return currency === "CNY" ? "人民币" : currency || "—";
}

function accountStatusName(status: string | undefined): string {
  if (status === "NORMAL") return "正常";
  if (status === "DORMANT") return "休眠";
  return status || "—";
}

function exchangeFund(exchangeCode: string): AccountFund["exchangeFunds"][number] | undefined {
  return accountFunds.value?.exchangeFunds.find((item) => item.exchangeCode === exchangeCode);
}

type CheckStatus = "pass" | "fail" | "skip" | "missing";
interface CheckResult { name: string; description: string; status: CheckStatus; text: string }
const checkText: Record<CheckStatus, string> = { pass: "通过", fail: "不通过", skip: "不需核查", missing: "缺少数据，无法核查" };
function check(status: CheckStatus) { return { status, text: checkText[status] }; }

function liveChecks(): CheckResult[] {
  const businessType = value<string>("businessType", "");
  const exchangeCode = value<string>("exchangeCode", "");
  const amount = Number(value("amount", 0));
  const funds = accountFunds.value;
  const pledge = ["仓单质押", "国债质押"].includes(businessType);
  const release = ["仓单解质押", "国债解质押"].includes(businessType);
  const exchangeFund = (code: string) => funds?.exchangeFunds.find((item) => item.exchangeCode === code);
  const evaluate = (applicable: boolean, predicate: () => boolean): ReturnType<typeof check> => {
    if (!applicable) return check("skip");
    if (!funds || !Number.isFinite(amount) || !amount) return check("missing");
    return check(predicate() ? "pass" : "fail");
  };
  return [
    { name: "满足质押要求", description: "当前权益或实有货币资金满足质押比例", ...evaluate(pledge, () => funds!.currentEquity + amount >= 1.25 * (funds!.pledgeAmount + amount) || funds!.actualCash >= 0.25 * (funds!.pledgeAmount + amount)) },
    { name: "满足解质押要求", description: "可用资金 + 本次金额 ≥ 0", ...evaluate(release, () => funds!.availableFunds + amount >= 0) },
    { name: "满足大商所特定要求", description: "质押金额 + 本次金额 ≤ 持仓保证金", ...evaluate(pledge && exchangeCode === "DCE", () => (exchangeFund("DCE")?.pledgeAmount ?? Infinity) + amount <= (exchangeFund("DCE")?.positionMargin ?? -Infinity)) },
    { name: "满足郑商所特定要求", description: "质押金额 + 本次金额 ≤ 1.2 × 持仓保证金", ...evaluate(pledge && exchangeCode === "CZCE", () => (exchangeFund("CZCE")?.pledgeAmount ?? Infinity) + amount <= 1.2 * (exchangeFund("CZCE")?.positionMargin ?? -Infinity)) },
  ];
}

function snapshotChecks(): CheckResult[] | undefined {
  const snapshot = value("businessCheckSnapshot", "");
  if (!snapshot) return undefined;
  try {
    const parsed = JSON.parse(snapshot) as unknown;
    return Array.isArray(parsed) ? parsed as CheckResult[] : undefined;
  } catch {
    return undefined;
  }
}

const checks = computed<CheckResult[]>(() => frozenChecks.value
  ? snapshotChecks() ?? liveChecks()
  : liveChecks());

watch(checks, (results) => {
  if (frozenChecks.value || !accountFunds.value) return;
  const snapshot = JSON.stringify(results);
  if (value("businessCheckSnapshot", "") !== snapshot) update("businessCheckSnapshot", snapshot);
}, { deep: true });

function selectProducts(event: Event): void {
  const selected = Array.from((event.target as HTMLSelectElement).selectedOptions).map(({ value }) => value);
  update("productCodes", selected);
}

async function validate(): Promise<boolean> {
  error.value = "";
  for (const field of props.fields) {
    if (!visible(field.fieldCode) || !required(field.fieldCode)) continue;
    const current = draft.value[field.fieldCode];
    if (current === undefined || current === null || current === "" || Array.isArray(current) && current.length === 0) {
      error.value = `请填写${field.fieldName}`;
      return false;
    }
  }
  const productCodes = value<unknown[]>("productCodes", []);
  if (!Array.isArray(productCodes) || productCodes.some((item) => typeof item !== "string" || !item)) {
    error.value = "期货品种选项无效";
    return false;
  }
  for (const code of ["quantity", "contractMultiplier", "pledgeUnitQuantity"]) {
    const current = Number(value(code, 0));
    if (!Number.isInteger(current) || current < 1) {
      error.value = `${props.fields.find((field) => field.fieldCode === code)?.fieldName ?? code}必须是正整数`;
      return false;
    }
  }
  const price = Number(value("previousSettlementPrice", 0));
  if (!Number.isFinite(price) || price < 0.0001 || decimalPlaces(price) > 4) {
    error.value = "昨结算价必须是最多四位小数的正数";
    return false;
  }
  return true;
}

function decimalPlaces(value: number): number {
  const text = String(value);
  if (/e-/i.test(text)) return Number(text.split(/e-/i)[1]);
  return text.includes(".") ? text.length - text.indexOf(".") - 1 : 0;
}

defineExpose({ validate });
</script>

<template>
  <section class="pledge-form" aria-label="仓单、国债（解）质押业务表单">
    <p v-if="loading" role="status">正在加载业务参考数据…</p>
    <p v-if="error" class="state-error" role="alert">{{ error }}</p>

    <section class="form-section">
      <h2>客户信息</h2>
      <div class="account-row">
        <label v-if="visible('futuresAccount')">
          <span>期货账号<b v-if="required('futuresAccount')"> *</b></span>
          <select :value="value('futuresAccount', '')" :disabled="readonly('futuresAccount')" @change="updateFromEvent('futuresAccount', $event)">
            <option value="">请选择</option>
            <option v-for="account in accounts" :key="account.accountNo" :value="account.accountNo">{{ account.accountNo }} - {{ account.customerName }}</option>
          </select>
        </label>
        <button
          v-if="visible('futuresAccount')"
          ref="fundDetailButton"
          class="detail-link"
          type="button"
          aria-haspopup="dialog"
          :disabled="!fundDetailReady"
          @click="openFundDetails"
        >查看资金详情</button>
      </div>
    </section>

    <section class="form-section">
      <h2>业务信息</h2>
      <div class="form-grid">
        <label v-if="visible('businessType')"><span>业务类型 *</span><select :value="value('businessType', '')" :disabled="readonly('businessType')" @change="updateFromEvent('businessType', $event)"><option value="">请选择</option><option v-for="item in businessTypes" :key="item">{{ item }}</option></select></label>
        <label v-if="visible('exchangeCode')"><span>交易所 *</span><select :value="value('exchangeCode', '')" :disabled="readonly('exchangeCode')" @change="updateFromEvent('exchangeCode', $event)"><option value="">请选择</option><option v-for="item in exchanges" :key="item.exchangeCode" :value="item.exchangeCode">{{ item.exchangeName }}</option></select></label>
        <label v-if="visible('tradingCode')"><span>交易编码</span><input :value="value('tradingCode', '')" disabled></label>
        <label v-if="visible('productCodes')" class="wide"><span>品种 *</span><select multiple :value="value('productCodes', [])" :disabled="readonly('productCodes') || !value('exchangeCode', '')" @change="selectProducts"><option v-for="item in products" :key="item.productCode" :value="item.productCode">{{ item.productCode }} - {{ item.productName }}</option></select></label>
        <label v-if="visible('quantity')"><span>数量（张） *</span><input type="number" min="1" step="1" :value="value('quantity', '')" :disabled="readonly('quantity')" @input="updateFromEvent('quantity', $event, true)"></label>
        <label v-if="visible('contractMultiplier')"><span>合约乘数 *</span><input type="number" min="1" step="1" :value="value('contractMultiplier', '')" :disabled="readonly('contractMultiplier')" @input="updateFromEvent('contractMultiplier', $event, true)"></label>
        <label v-if="visible('pledgeUnitQuantity')"><span>质押品单位数量 *</span><input type="number" min="1" step="1" :value="value('pledgeUnitQuantity', '')" :disabled="readonly('pledgeUnitQuantity')" @input="updateFromEvent('pledgeUnitQuantity', $event, true)"></label>
        <label v-if="visible('previousSettlementPrice')"><span>昨结算价 *</span><input type="number" min="0.0001" step="0.0001" :value="value('previousSettlementPrice', '')" :disabled="readonly('previousSettlementPrice')" @input="updateFromEvent('previousSettlementPrice', $event, true)"></label>
        <label v-if="visible('amount')"><span>金额</span><input class="amount" :value="Number(value('amount', 0)).toFixed(4)" disabled></label>
      </div>
    </section>

    <section class="form-section">
      <header class="section-row"><h2>业务核查</h2><button type="button" :disabled="disabled || frozenChecks" @click="refreshChecks">刷新</button></header>
      <div class="table-wrap"><table><thead><tr><th>核查项</th><th>核查内容</th><th>核查结果</th></tr></thead><tbody><tr v-for="item in checks" :key="item.name"><td>{{ item.name }}</td><td>{{ item.description }}</td><td :class="`check-${item.status}`">{{ item.text }}</td></tr></tbody></table></div>
    </section>

    <div v-if="fundDetailOpen" class="dialog-backdrop" @click.self="closeFundDetails()">
      <section
        ref="fundDetailDialog"
        class="fund-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="fund-detail-title"
        tabindex="-1"
        @keydown.esc.stop.prevent="closeFundDetails()"
      >
        <header class="dialog-head">
          <h2 id="fund-detail-title">客户资金详情</h2>
          <button type="button" class="dialog-close" aria-label="关闭资金详情" @click="closeFundDetails()">×</button>
        </header>
        <div class="dialog-body">
          <dl class="detail-summary">
            <div><dt>期货账号</dt><dd>{{ accountFunds?.accountNo || '—' }}</dd></div>
            <div><dt>客户名称</dt><dd>{{ accountFunds?.customerName || selectedAccount?.customerName || '—' }}</dd></div>
            <div><dt>币种</dt><dd>{{ currencyName(accountFunds?.currency) }}</dd></div>
            <div><dt>账户状态</dt><dd class="account-status">{{ accountStatusName(selectedAccount?.accountStatus) }}</dd></div>
          </dl>
          <div class="table-wrap">
            <table class="fund-table">
              <tbody>
                <tr><th>当前权益</th><td>{{ formatMoney(accountFunds?.currentEquity) }}</td><th>可用资金</th><td>{{ formatMoney(accountFunds?.availableFunds) }}</td></tr>
                <tr><th>质押金额</th><td>{{ formatMoney(accountFunds?.pledgeAmount) }}</td><th>实有货币资金</th><td>{{ formatMoney(accountFunds?.actualCash) }}</td></tr>
                <tr><th>大商所质押金额</th><td>{{ formatMoney(exchangeFund('DCE')?.pledgeAmount) }}</td><th>大商所持仓保证金</th><td>{{ formatMoney(exchangeFund('DCE')?.positionMargin) }}</td></tr>
                <tr><th>郑商所质押金额</th><td>{{ formatMoney(exchangeFund('CZCE')?.pledgeAmount) }}</td><th>郑商所持仓保证金</th><td>{{ formatMoney(exchangeFund('CZCE')?.positionMargin) }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.pledge-form { display: grid; gap: 18px; color: #17202a; }
.form-section { display: grid; gap: 12px; padding-top: 4px; }
.form-section + .form-section { border-top: 1px solid #d8dee8; padding-top: 16px; }
h2 { margin: 0; font-size: 17px; }
.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px 18px; }
.account-row { display: flex; align-items: end; gap: 14px; }
.account-row label { flex: 1 1 360px; max-width: 560px; }
label { display: grid; gap: 6px; }
label span { color: #475569; font-size: 13px; font-weight: 650; }
b, .state-error, .check-fail { color: #be123c; }
input, select, button { min-height: 36px; border: 1px solid #b9c3d0; border-radius: 6px; padding: 7px 10px; background: #fff; color: inherit; }
select[multiple] { min-height: 84px; }
.wide { grid-column: span 2; }
.amount { color: #9f1239; font-weight: 750; text-align: right; }
.section-row { display: flex; align-items: center; justify-content: space-between; }
.section-row button { color: #1d4ed8; cursor: pointer; }
.detail-link { border: 0; padding-inline: 2px; color: #1d4ed8; background: transparent; cursor: pointer; white-space: nowrap; }
.detail-link:disabled { color: #94a3b8; cursor: not-allowed; }
.table-wrap { overflow-x: auto; }
table { width: 100%; min-width: 680px; border-collapse: collapse; }
th, td { border: 1px solid #d8dee8; padding: 9px 10px; text-align: left; }
th { background: #f8fafc; }
.check-pass { color: #15803d; }.check-skip { color: #64748b; }.check-missing { color: #17202a; }
.state-error { margin: 0; border: 1px solid #fecdd3; border-radius: 6px; padding: 10px; background: #fff1f2; }
.dialog-backdrop { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: 20px; background: rgb(15 23 42 / 52%); }
.fund-dialog { width: min(760px, 100%); max-height: calc(100vh - 40px); overflow: auto; border-radius: 10px; background: #fff; box-shadow: 0 24px 70px rgb(15 23 42 / 32%); outline: none; }
.dialog-head { display: flex; align-items: center; justify-content: space-between; min-height: 52px; padding: 8px 12px 8px 18px; color: #fff; background: #1d4ed8; }
.dialog-head h2 { font-size: 16px; }
.dialog-close { width: 36px; min-height: 36px; border: 0; padding: 0; color: #fff; background: transparent; font-size: 24px; cursor: pointer; }
.dialog-body { display: grid; gap: 18px; padding: 20px; }
.detail-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 24px; margin: 0; }
.detail-summary div { display: grid; grid-template-columns: 88px 1fr; gap: 8px; }
.detail-summary dt { color: #64748b; }
.detail-summary dd { margin: 0; font-weight: 700; }
.account-status { color: #15803d; }
.fund-table { min-width: 640px; }
.fund-table th { width: 22%; color: #475569; font-weight: 650; }
.fund-table td { width: 28%; text-align: right; font-variant-numeric: tabular-nums; }
@media (max-width: 640px) { .form-grid { grid-template-columns: 1fr; }.wide { grid-column: auto; }.account-row { align-items: stretch; flex-direction: column; }.account-row label { flex: none; width: 100%; max-width: none; }.detail-link { align-self: start; }.detail-summary { grid-template-columns: 1fr; } }
</style>
