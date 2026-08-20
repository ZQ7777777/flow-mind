<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
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
} from "../../../api/generated/warehouse-treasury-pledge-application/warehouse-treasury-pledge-application";

const props = withDefaults(defineProps<{
  modelValue: Record<string, unknown>;
  fields: WorkflowFormField[];
  fieldPermissions: WorkflowFieldPermission[];
  mode?: "edit" | "readonly";
  disabled?: boolean;
}>(), { mode: "edit", disabled: false });

const emit = defineEmits<{ "update:modelValue": [value: Record<string, unknown>] }>();
const accounts = ref<FuturesAccount[]>([]);
const exchanges = ref<Exchange[]>([]);
const products = ref<FuturesProduct[]>([]);
const accountFunds = ref<AccountFund>();
const loading = ref(false);
const error = ref("");

function value<T>(fieldCode: string, fallback: T): T {
  return (props.modelValue[fieldCode] ?? fallback) as T;
}

function update(fieldCode: string, nextValue: unknown): void {
  emit("update:modelValue", { ...props.modelValue, [fieldCode]: nextValue });
}

function updateFromEvent(fieldCode: string, event: Event, numeric = false): void {
  const raw = (event.target as HTMLInputElement | HTMLSelectElement).value;
  update(fieldCode, numeric && raw !== "" ? Number(raw) : raw);
}

function permission(fieldCode: string): WorkflowFieldPermission | undefined {
  return props.fieldPermissions.find((item) => item.fieldCode === fieldCode);
}

function fieldLabel(fieldCode: string): string {
  return props.fields.find((item) => item.fieldCode === fieldCode)?.fieldName ?? fieldCode;
}

function visible(fieldCode: string): boolean {
  const runtime = permission(fieldCode);
  const field = props.fields.find((item) => item.fieldCode === fieldCode);
  return runtime?.visible ?? field?.visible ?? true;
}

function readonly(fieldCode: string): boolean {
  const runtime = permission(fieldCode);
  const field = props.fields.find((item) => item.fieldCode === fieldCode);
  return props.disabled || props.mode === "readonly" || field?.editable === false || runtime?.editable === false
    || ["customerName", "tradingCode", "amount", "largeAmount"].includes(fieldCode);
}

function required(fieldCode: string): boolean {
  return permission(fieldCode)?.required ?? props.fields.find((item) => item.fieldCode === fieldCode)?.required ?? false;
}

const businessTypeOptions = computed(() => {
  const field = props.fields.find((item) => item.fieldCode === "businessType");
  if (field?.options?.length) return field.options;
  return [
    { label: "仓单质押", value: "仓单质押" },
    { label: "仓单解质押", value: "仓单解质押" },
    { label: "国债质押", value: "国债质押" },
    { label: "国债解质押", value: "国债解质押" },
  ];
});

onMounted(async () => {
  loading.value = true;
  try {
    [accounts.value, exchanges.value] = await Promise.all([searchFuturesAccounts(), getExchanges()]);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "参考数据加载失败";
  } finally {
    loading.value = false;
  }
});

watch(() => value("futuresAccount", ""), async (accountNo) => {
  accountFunds.value = undefined;
  if (!accountNo) {
    emit("update:modelValue", { ...props.modelValue, customerName: "", tradingCode: "" });
    return;
  }
  const account = accounts.value.find((item) => item.accountNo === accountNo);
  if (account) update("customerName", account.customerName);
  try {
    accountFunds.value = await getAccountFunds(accountNo);
    await loadTradingCode();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "客户资金加载失败";
  }
});

watch(() => value("exchangeCode", ""), async (exchangeCode) => {
  emit("update:modelValue", {
    ...props.modelValue,
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
    products.value = await searchFuturesProducts(exchangeCode);
    await loadTradingCode();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "交易所关联数据加载失败";
  }
});

watch(() => value<string[]>("productCodes", []).join("\u0000"), (codesKey) => {
  const codes = codesKey ? codesKey.split("\u0000") : [];
  if (!codes.length) {
    emit("update:modelValue", {
      ...props.modelValue,
      contractMultiplier: undefined,
      pledgeUnitQuantity: undefined,
      previousSettlementPrice: undefined,
    });
    return;
  }
  const selected = products.value.find((item) => item.productCode === codes[codes.length - 1]);
  if (!selected) return;
  emit("update:modelValue", {
    ...props.modelValue,
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
  const businessType = value("businessType", "");
  const hasAll = [price, quantity, unit, multiplier].every((item) => Number.isFinite(item) && item > 0);
  const pledge = ["仓单质押", "国债质押"].includes(businessType);
  const release = ["仓单解质押", "国债解质押"].includes(businessType);
  const sign = pledge ? 1 : release ? -1 : 0;
  const amount = hasAll && sign !== 0 ? Number((price * quantity * unit * multiplier * 0.8 * sign).toFixed(4)) : 0;
  const large = hasAll && sign !== 0 && Math.abs(amount) >= 10000000;
  const changes: Record<string, unknown> = {};
  if (value("amount", 0) !== amount) changes.amount = amount;
  if (value("largeAmount", false) !== large) changes.largeAmount = large;
  if (Object.keys(changes).length) emit("update:modelValue", { ...props.modelValue, ...changes });
}, { immediate: true });

async function loadTradingCode(): Promise<void> {
  const accountNo = value("futuresAccount", "");
  const exchangeCode = value("exchangeCode", "");
  if (!accountNo || !exchangeCode) return;
  const codes = await getTradingCodes(accountNo, exchangeCode);
  update("tradingCode", codes[0]?.tradingCode || "");
}

async function refreshChecks(): Promise<void> {
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

type CheckStatus = "pass" | "fail" | "skip" | "missing";
const checkText: Record<CheckStatus, string> = { pass: "通过", fail: "不通过", skip: "不需核查", missing: "缺少数据，无法核查" };
function check(status: CheckStatus) { return { status, text: checkText[status] }; }

const checks = computed(() => {
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
    { name: "满足质押要求", description: "当前权益加本次金额满足质押比例，或实有货币资金满足比例。", ...evaluate(pledge, () => funds!.currentEquity + amount >= 1.25 * (funds!.pledgeAmount + amount) || funds!.actualCash >= 0.25 * (funds!.pledgeAmount + amount)) },
    { name: "满足解质押要求", description: "可用资金加本次解质押金额大于等于零。", ...evaluate(release, () => funds!.availableFunds + amount >= 0) },
    { name: "满足大商所特定要求", description: "大商所质押金额加本次金额不超过持仓保证金。", ...evaluate(pledge && exchangeCode === "DCE", () => (exchangeFund("DCE")?.pledgeAmount ?? Infinity) + amount <= (exchangeFund("DCE")?.positionMargin ?? -Infinity)) },
    { name: "满足郑商所特定要求", description: "郑商所质押金额加本次金额不超过1.2倍持仓保证金。", ...evaluate(pledge && exchangeCode === "CZCE", () => (exchangeFund("CZCE")?.pledgeAmount ?? Infinity) + amount <= 1.2 * (exchangeFund("CZCE")?.positionMargin ?? -Infinity)) },
  ];
});

function selectProducts(event: Event): void {
  const selected = Array.from((event.target as HTMLSelectElement).selectedOptions).map(({ value }) => value);
  update("productCodes", selected);
}

async function validate(): Promise<boolean> {
  error.value = "";
  for (const field of props.fields) {
    if (!visible(field.fieldCode) || !required(field.fieldCode)) continue;
    const current = props.modelValue[field.fieldCode];
    if (current === undefined || current === null || current === "" || Array.isArray(current) && current.length === 0) {
      error.value = `请填写${field.fieldName}`;
      return false;
    }
  }
  return true;
}

defineExpose({ validate });
</script>

<template>
  <section class="pledge-form" aria-label="仓单、国债（解）质押申请业务表单">
    <p v-if="loading" role="status">正在加载业务参考数据…</p>
    <p v-if="error" class="state-error" role="alert">{{ error }}</p>

    <section class="form-section">
      <h2>客户信息</h2>
      <div class="form-grid">
        <label v-if="visible('futuresAccount')">
          <span>{{ fieldLabel('futuresAccount') }}<b v-if="required('futuresAccount')"> *</b></span>
          <select :value="value('futuresAccount', '')" :disabled="readonly('futuresAccount')" @change="updateFromEvent('futuresAccount', $event)">
            <option value="">请选择</option>
            <option v-for="account in accounts" :key="account.accountNo" :value="account.accountNo">{{ account.accountNo }} - {{ account.customerName }}</option>
          </select>
        </label>
        <label v-if="visible('customerName')">
          <span>{{ fieldLabel('customerName') }}<b v-if="required('customerName')"> *</b></span>
          <input :value="value('customerName', '')" disabled>
        </label>
      </div>
    </section>

    <section class="form-section">
      <h2>业务信息</h2>
      <div class="form-grid">
        <label v-if="visible('businessType')">
          <span>{{ fieldLabel('businessType') }}<b v-if="required('businessType')"> *</b></span>
          <select :value="value('businessType', '')" :disabled="readonly('businessType')" @change="updateFromEvent('businessType', $event)">
            <option value="">请选择</option>
            <option v-for="option in businessTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <label v-if="visible('exchangeCode')">
          <span>{{ fieldLabel('exchangeCode') }}<b v-if="required('exchangeCode')"> *</b></span>
          <select :value="value('exchangeCode', '')" :disabled="readonly('exchangeCode')" @change="updateFromEvent('exchangeCode', $event)">
            <option value="">请选择</option>
            <option v-for="item in exchanges" :key="item.exchangeCode" :value="item.exchangeCode">{{ item.exchangeName }}</option>
          </select>
        </label>
        <label v-if="visible('tradingCode')">
          <span>{{ fieldLabel('tradingCode') }}<b v-if="required('tradingCode')"> *</b></span>
          <input :value="value('tradingCode', '')" disabled>
        </label>
        <label v-if="visible('productCodes')" class="wide">
          <span>{{ fieldLabel('productCodes') }}<b v-if="required('productCodes')"> *</b></span>
          <select multiple :disabled="readonly('productCodes') || !value('exchangeCode', '')" @change="selectProducts">
            <option v-for="item in products" :key="item.productCode" :value="item.productCode">{{ item.productCode }} - {{ item.productName }}</option>
          </select>
        </label>
        <label v-if="visible('quantity')">
          <span>{{ fieldLabel('quantity') }}<b v-if="required('quantity')"> *</b></span>
          <input type="number" min="1" step="1" :value="value('quantity', '')" :disabled="readonly('quantity')" @input="updateFromEvent('quantity', $event, true)">
        </label>
        <label v-if="visible('contractMultiplier')">
          <span>{{ fieldLabel('contractMultiplier') }}<b v-if="required('contractMultiplier')"> *</b></span>
          <input type="number" min="1" step="1" :value="value('contractMultiplier', '')" :disabled="readonly('contractMultiplier')" @input="updateFromEvent('contractMultiplier', $event, true)">
        </label>
        <label v-if="visible('pledgeUnitQuantity')">
          <span>{{ fieldLabel('pledgeUnitQuantity') }}<b v-if="required('pledgeUnitQuantity')"> *</b></span>
          <input type="number" min="1" step="1" :value="value('pledgeUnitQuantity', '')" :disabled="readonly('pledgeUnitQuantity')" @input="updateFromEvent('pledgeUnitQuantity', $event, true)">
        </label>
        <label v-if="visible('previousSettlementPrice')">
          <span>{{ fieldLabel('previousSettlementPrice') }}<b v-if="required('previousSettlementPrice')"> *</b></span>
          <input type="number" min="0.0001" step="0.0001" :value="value('previousSettlementPrice', '')" :disabled="readonly('previousSettlementPrice')" @input="updateFromEvent('previousSettlementPrice', $event, true)">
        </label>
        <label v-if="visible('amount')">
          <span>{{ fieldLabel('amount') }}<b v-if="required('amount')"> *</b></span>
          <input class="amount" :value="Number(value('amount', 0)).toFixed(4)" disabled>
        </label>
        <label v-if="visible('largeAmount')" class="checkbox-field">
          <input type="checkbox" :checked="Boolean(value('largeAmount', false))" disabled>
          <span>{{ fieldLabel('largeAmount') }}</span>
        </label>
      </div>
    </section>

    <section class="form-section">
      <header class="section-row">
        <h2>业务核查</h2>
        <button type="button" :disabled="disabled || mode === 'readonly'" @click="refreshChecks">刷新</button>
      </header>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>核查项</th><th>核查内容</th><th>核查结果</th></tr>
          </thead>
          <tbody>
            <tr v-for="item in checks" :key="item.name">
              <td>{{ item.name }}</td>
              <td>{{ item.description }}</td>
              <td :class="`check-${item.status}`">{{ item.text }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>

<style scoped>
.pledge-form { display: grid; gap: 18px; color: #17202a; }
.form-section { display: grid; gap: 12px; padding-top: 4px; }
.form-section + .form-section { border-top: 1px solid #d8dee8; padding-top: 16px; }
h2 { margin: 0; font-size: 17px; }
.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px 18px; }
label { display: grid; gap: 6px; }
label span { color: #475569; font-size: 13px; font-weight: 650; }
b, .state-error, .check-fail { color: #be123c; }
input, select, button { min-height: 36px; border: 1px solid #b9c3d0; border-radius: 6px; padding: 7px 10px; background: #fff; color: inherit; }
select[multiple] { min-height: 84px; }
.wide { grid-column: span 2; }
.amount { color: #9f1239; font-weight: 750; text-align: right; }
.checkbox-field { display: flex; align-items: center; gap: 8px; }
.checkbox-field input { min-height: auto; width: 18px; height: 18px; }
.checkbox-field span { color: #475569; font-size: 13px; font-weight: 650; }
.section-row { display: flex; align-items: center; justify-content: space-between; }
.section-row button { color: #1d4ed8; cursor: pointer; }
.table-wrap { overflow-x: auto; }
table { width: 100%; min-width: 680px; border-collapse: collapse; }
th, td { border: 1px solid #d8dee8; padding: 9px 10px; text-align: left; }
th { background: #f8fafc; }
.check-pass { color: #15803d; }
.check-skip { color: #64748b; }
.check-missing { color: #17202a; }
.state-error { margin: 0; border: 1px solid #fecdd3; border-radius: 6px; padding: 10px; background: #fff1f2; }
@media (max-width: 640px) {
  .form-grid { grid-template-columns: 1fr; }
  .wide { grid-column: auto; }
}
</style>
