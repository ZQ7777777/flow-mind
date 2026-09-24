import type {
  EvaluationAssertion,
  EvaluationCatalog,
  EvaluationTask,
  RequirementIrDraft,
  RequirementIrField,
} from "@flowmind/agent-contracts";

type IrOptions = {
  code: string;
  name: string;
  goal: string;
  fields: RequirementIrField[];
  mode?: RequirementIrDraft["generationMode"];
  sections?: RequirementIrDraft["sections"];
  dataQueries?: RequirementIrDraft["dataQueries"];
  calculations?: RequirementIrDraft["calculations"];
  checks?: RequirementIrDraft["checks"];
  ambiguities?: RequirementIrDraft["ambiguities"];
};

const text = (fieldCode: string, fieldName: string, required = true, validation: Record<string, unknown> = {}): RequirementIrField => ({
  fieldCode, fieldName, valueType: "STRING", control: "INPUT", required, readOnly: false, multiple: false, validation,
});

const textarea = (fieldCode: string, fieldName: string, required = true): RequirementIrField => ({
  fieldCode, fieldName, valueType: "STRING", control: "TEXTAREA", required, readOnly: false, multiple: false, validation: {},
});

const number = (fieldCode: string, fieldName: string, validation: Record<string, unknown> = { minimum: 0 }): RequirementIrField => ({
  fieldCode, fieldName, valueType: "NUMBER", control: "NUMBER", required: true, readOnly: false, multiple: false, validation,
});

const date = (fieldCode: string, fieldName: string): RequirementIrField => ({
  fieldCode, fieldName, valueType: "DATE", control: "DATE_PICKER", required: true, readOnly: false, multiple: false, validation: {},
});

const boolean = (fieldCode: string, fieldName: string, readOnly = false): RequirementIrField => ({
  fieldCode, fieldName, valueType: "BOOLEAN", control: "CHECKBOX", required: false, readOnly, multiple: false, validation: {},
});

const select = (fieldCode: string, fieldName: string, values: string[], multiple = false): RequirementIrField => ({
  fieldCode, fieldName, valueType: "ENUM", control: "SELECT", required: true, readOnly: false, multiple, validation: {},
  options: values.map((value) => ({ label: value, value })),
});

const readonlyNumber = (fieldCode: string, fieldName: string): RequirementIrField => ({
  fieldCode, fieldName, valueType: "NUMBER", control: "NUMBER", required: false, readOnly: true, multiple: false, validation: {},
});

const referenceSelect = (
  fieldCode: string,
  fieldName: string,
  resource: NonNullable<RequirementIrField["referenceData"]>["resource"],
  parameterBindings: Record<string, string> = {},
  autofillBindings: Record<string, string> = {},
): RequirementIrField => ({
  fieldCode, fieldName, valueType: "ENUM", control: "SELECT", required: true, readOnly: false, multiple: false, validation: {},
  referenceData: { resource, parameterBindings, autofillBindings },
});

function ir(options: IrOptions): RequirementIrDraft {
  return {
    irVersion: "0.1",
    generationMode: options.mode ?? "CREATE_STANDARD_MODULE",
    identity: {
      businessCode: options.code,
      businessName: options.name,
      pageTitle: `发起${options.name}`,
      goal: options.goal,
    },
    fields: options.fields,
    attachments: [],
    sections: options.sections ?? [{ sectionCode: "basic", title: "基本信息", fieldCodes: options.fields.map(({ fieldCode }) => fieldCode) }],
    dataQueries: options.dataQueries ?? [],
    calculations: options.calculations ?? [],
    checks: options.checks ?? [],
    submission: { processCode: options.code, action: "START_AND_SUBMIT", payloadFieldCodes: options.fields.map(({ fieldCode }) => fieldCode) },
    ambiguities: options.ambiguities ?? [],
    sourceRefs: [`eval://${options.code}/user-request`],
  };
}

function assertion(
  assertionId: string,
  severity: EvaluationAssertion["severity"],
  area: EvaluationAssertion["area"],
  description: string,
  kind: EvaluationAssertion["oracle"]["kind"],
  target: string,
  operator: EvaluationAssertion["oracle"]["operator"],
  expected: EvaluationAssertion["oracle"]["expected"],
): EvaluationAssertion {
  return { assertionId, severity, area, description, oracle: { kind, target, operator, expected } };
}

type TaskOptions = Omit<EvaluationTask, "assertions"> & { assertions?: EvaluationAssertion[] };

function task(options: TaskOptions): EvaluationTask {
  const kebab = options.requirementIr.identity.businessCode.replaceAll("_", "-");
  const baseline = options.expectedOutcome === "BLOCKED_REQUIREMENT"
    ? [assertion("requirement-blocked", "CRITICAL", "BOUNDARY", "重大歧义必须阻止代码生成", "GENERATION_BLOCKED", "generation", "TRUTHY", true)]
    : [
      assertion("ir-business-code", "CRITICAL", "IR", "IR 保留稳定业务编码", "IR_PATH", "identity.businessCode", "EQUALS", options.requirementIr.identity.businessCode),
      assertion("apply-view", "CRITICAL", "FILES", "生成标准申请入口", "FILE_EXISTS", `src/modules/generated/${kebab}/Apply.vue`, "EXISTS", true),
      assertion("start-submit", "CRITICAL", "SUBMISSION", "通过共享 Shell 发起并提交流程", "UI_BEHAVIOR", "submission.action", "EQUALS", "START_AND_SUBMIT"),
    ];
  return { ...options, assertions: [...baseline, ...(options.assertions ?? [])] };
}

const developmentTasks: EvaluationTask[] = [
  task({
    taskId: "dev-basic-01-travel-expense", split: "DEVELOPMENT", title: "差旅报销基础表单", difficulty: "BASIC",
    tags: ["basic", "number", "validation"],
    input: { userRequest: "生成差旅报销申请，填写出差人、报销金额和事由；金额必须大于 0。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "travel_expense", name: "差旅报销", goal: "员工提交差旅费用报销。", fields: [text("travelerName", "出差人"), number("amount", "报销金额", { exclusiveMinimum: 0 }), textarea("reason", "报销事由")] }),
    assertions: [assertion("positive-amount", "CRITICAL", "VALIDATION", "报销金额必须大于零", "IR_PATH", "fields.amount.validation.exclusiveMinimum", "EQUALS", 0)],
  }),
  task({
    taskId: "dev-basic-02-leave-request", split: "DEVELOPMENT", title: "请假申请日期字段", difficulty: "BASIC",
    tags: ["basic", "date", "select"],
    input: { userRequest: "生成请假申请，包含请假类型、开始日期、结束日期和原因。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "leave_request", name: "请假申请", goal: "员工提交请假申请。", fields: [select("leaveType", "请假类型", ["年假", "事假", "病假"]), date("startDate", "开始日期"), date("endDate", "结束日期"), textarea("reason", "请假原因")] }),
    assertions: [assertion("leave-options", "NON_CRITICAL", "UI", "展示三个请假类型", "IR_PATH", "fields.leaveType.options", "CONTAINS", "病假")],
  }),
  task({
    taskId: "dev-basic-03-supplier-onboarding", split: "DEVELOPMENT", title: "供应商准入", difficulty: "BASIC",
    tags: ["basic", "text", "required"],
    input: { userRequest: "生成供应商准入申请，供应商名称、统一社会信用代码和联系人必填，备注选填。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "supplier_onboarding", name: "供应商准入", goal: "登记并提交新供应商准入。", fields: [text("supplierName", "供应商名称"), text("creditCode", "统一社会信用代码", true, { minLength: 18, maxLength: 18 }), text("contactName", "联系人"), textarea("remark", "备注", false)] }),
    assertions: [assertion("credit-code-length", "CRITICAL", "VALIDATION", "信用代码固定十八位", "IR_PATH", "fields.creditCode.validation.minLength", "EQUALS", 18)],
  }),
  task({
    taskId: "dev-basic-04-asset-purchase", split: "DEVELOPMENT", title: "资产采购申请", difficulty: "BASIC",
    tags: ["basic", "number", "textarea"],
    input: { userRequest: "生成资产采购申请，包含资产名称、数量、预算和采购理由。数量为正整数。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "asset_purchase", name: "资产采购申请", goal: "提交办公资产采购需求。", fields: [text("assetName", "资产名称"), number("quantity", "数量", { minimum: 1, integer: true }), number("budget", "预算", { minimum: 0 }), textarea("reason", "采购理由")] }),
    assertions: [assertion("integer-quantity", "CRITICAL", "VALIDATION", "数量必须为正整数", "IR_PATH", "fields.quantity.validation.integer", "EQUALS", true)],
  }),
  task({
    taskId: "dev-basic-05-seal-use", split: "DEVELOPMENT", title: "印章使用申请", difficulty: "BASIC",
    tags: ["basic", "select", "date"],
    input: { userRequest: "生成印章使用申请，选择公章或合同章，填写使用日期和用途。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "seal_use", name: "印章使用申请", goal: "申请使用公司印章。", fields: [select("sealType", "印章类型", ["公章", "合同章"]), date("useDate", "使用日期"), textarea("purpose", "用途")] }),
    assertions: [assertion("seal-options", "CRITICAL", "UI", "印章类型只允许约定选项", "IR_PATH", "fields.sealType.options", "CONTAINS", "合同章")],
  }),
  task({
    taskId: "dev-basic-06-customer-visit", split: "DEVELOPMENT", title: "客户拜访登记", difficulty: "BASIC",
    tags: ["basic", "date", "optional"],
    input: { userRequest: "生成客户拜访登记，客户名称、拜访日期和拜访目的必填，后续计划选填。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "customer_visit", name: "客户拜访登记", goal: "记录客户拜访安排。", fields: [text("customerName", "客户名称"), date("visitDate", "拜访日期"), textarea("purpose", "拜访目的"), textarea("followUpPlan", "后续计划", false)] }),
    assertions: [assertion("optional-follow-up", "NON_CRITICAL", "VALIDATION", "后续计划允许为空", "IR_PATH", "fields.followUpPlan.required", "EQUALS", false)],
  }),
  task({
    taskId: "dev-basic-07-contract-filing", split: "DEVELOPMENT", title: "合同归档登记", difficulty: "BASIC",
    tags: ["basic", "date", "text"],
    input: { userRequest: "生成合同归档登记，包含合同编号、合同名称、签署日期和保管位置。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "contract_filing", name: "合同归档登记", goal: "登记已签署合同的归档位置。", fields: [text("contractNo", "合同编号"), text("contractName", "合同名称"), date("signedDate", "签署日期"), text("storageLocation", "保管位置")] }),
  }),
  task({
    taskId: "dev-basic-08-invoice-request", split: "DEVELOPMENT", title: "开票申请", difficulty: "BASIC",
    tags: ["basic", "number", "select"],
    input: { userRequest: "生成开票申请，填写客户名称、发票类型、开票金额和税号。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "invoice_request", name: "开票申请", goal: "提交客户开票申请。", fields: [text("customerName", "客户名称"), select("invoiceType", "发票类型", ["增值税专用发票", "增值税普通发票"]), number("invoiceAmount", "开票金额", { exclusiveMinimum: 0 }), text("taxNumber", "税号")] }),
    assertions: [assertion("invoice-positive", "CRITICAL", "VALIDATION", "开票金额必须为正数", "IR_PATH", "fields.invoiceAmount.validation.exclusiveMinimum", "EQUALS", 0)],
  }),
  task({
    taskId: "dev-basic-09-account-change", split: "DEVELOPMENT", title: "账户信息变更", difficulty: "BASIC",
    tags: ["basic", "readonly", "text"],
    input: { userRequest: "生成账户信息变更申请，原账号只读，新账号和变更原因必填。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "account_change", name: "账户信息变更", goal: "申请变更业务账户信息。", fields: [{ ...text("oldAccountNo", "原账号"), readOnly: true }, text("newAccountNo", "新账号"), textarea("changeReason", "变更原因")] }),
    assertions: [assertion("old-account-readonly", "CRITICAL", "UI", "原账号不可编辑", "IR_PATH", "fields.oldAccountNo.readOnly", "EQUALS", true)],
  }),
  task({
    taskId: "dev-basic-10-event-registration", split: "DEVELOPMENT", title: "活动报名", difficulty: "BASIC",
    tags: ["basic", "boolean", "optional"],
    input: { userRequest: "生成活动报名，姓名、手机号必填，可勾选是否需要餐食并填写备注。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "event_registration", name: "活动报名", goal: "登记活动参与者信息。", fields: [text("participantName", "姓名"), text("mobile", "手机号", true, { pattern: "^1[3-9]\\d{9}$" }), boolean("mealRequired", "需要餐食"), textarea("remark", "备注", false)] }),
    assertions: [assertion("mobile-pattern", "CRITICAL", "VALIDATION", "手机号执行格式校验", "IR_PATH", "fields.mobile.validation.pattern", "MATCHES", "1[3-9]")],
  }),
  task({
    taskId: "dev-composite-01-account-autofill", split: "DEVELOPMENT", title: "期货账户动态带出客户", difficulty: "COMPOSITE",
    tags: ["composite", "reference-data", "autofill"],
    input: { userRequest: "期货账号从账户接口选择，选择后自动带出只读的客户名称。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "account_profile", name: "账户资料申请", goal: "选择期货账户并带出客户资料。", fields: [referenceSelect("futuresAccount", "期货账号", "FUTURES_ACCOUNTS", {}, { customerName: "customerName" }), { ...text("customerName", "客户名称"), readOnly: true }] }),
    assertions: [assertion("customer-autofill", "CRITICAL", "DATA", "选择账户后带出客户名称", "IR_PATH", "fields.futuresAccount.referenceData.autofillBindings.customerName", "EQUALS", "customerName")],
  }),
  task({
    taskId: "dev-composite-02-cascading-trading-code", split: "DEVELOPMENT", title: "交易所与交易编码级联", difficulty: "COMPOSITE",
    tags: ["composite", "reference-data", "cascade"],
    input: { userRequest: "先选择期货账号，再按账号选择交易所，最后按账号和交易所加载交易编码。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "trading_code_apply", name: "交易编码申请", goal: "按账户和交易所选择交易编码。", fields: [referenceSelect("futuresAccount", "期货账号", "FUTURES_ACCOUNTS"), referenceSelect("exchangeCode", "交易所", "EXCHANGES", { account: "futuresAccount" }), referenceSelect("tradingCode", "交易编码", "TRADING_CODES", { account: "futuresAccount", exchange: "exchangeCode" })] }),
    assertions: [assertion("trading-code-dependencies", "CRITICAL", "DATA", "交易编码同时依赖账号和交易所", "IR_PATH", "fields.tradingCode.referenceData.parameterBindings", "CONTAINS", "exchangeCode")],
  }),
  task({
    taskId: "dev-composite-03-multiple-products", split: "DEVELOPMENT", title: "期货品种多选", difficulty: "COMPOSITE",
    tags: ["composite", "multi-select", "reference-data"],
    input: { userRequest: "按交易所加载期货品种，允许选择多个品种，至少选择一个。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "product_subscription", name: "品种订阅申请", goal: "选择需要订阅的期货品种。", fields: [{ ...referenceSelect("productCodes", "期货品种", "FUTURES_PRODUCTS", { exchange: "exchangeCode" }), multiple: true }, referenceSelect("exchangeCode", "交易所", "EXCHANGES")] }),
    assertions: [assertion("products-multiple", "CRITICAL", "UI", "期货品种支持多选", "IR_PATH", "fields.productCodes.multiple", "EQUALS", true)],
  }),
  task({
    taskId: "dev-composite-04-pledge-amount", split: "DEVELOPMENT", title: "质押金额计算", difficulty: "COMPOSITE",
    tags: ["composite", "calculation", "readonly"],
    input: { userRequest: "数量、合约乘数、单位数量和昨结算价输入后，金额自动计算为四者乘积，保留四位小数且只读。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "pledge_amount", name: "质押金额申请", goal: "自动计算质押金额。", fields: [number("quantity", "数量", { minimum: 1, integer: true }), number("contractMultiplier", "合约乘数", { minimum: 1 }), number("unitQuantity", "单位数量", { minimum: 1 }), number("settlementPrice", "昨结算价", { exclusiveMinimum: 0 }), readonlyNumber("amount", "金额")], calculations: [{ calculationCode: "calculateAmount", targetFieldCode: "amount", expression: "quantity * contractMultiplier * unitQuantity * settlementPrice", dependencyFieldCodes: ["quantity", "contractMultiplier", "unitQuantity", "settlementPrice"], decimalPlaces: 4 }] }),
    assertions: [assertion("amount-formula", "CRITICAL", "CALCULATION", "金额使用约定乘积公式", "IR_PATH", "calculations.calculateAmount.expression", "EQUALS", "quantity * contractMultiplier * unitQuantity * settlementPrice")],
  }),
  task({
    taskId: "dev-composite-05-funds-check", split: "DEVELOPMENT", title: "可用资金核查", difficulty: "COMPOSITE",
    tags: ["composite", "query", "check"],
    input: { userRequest: "选择账号后允许手动刷新可用资金；提交前校验申请金额不能超过可用资金。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "funds_usage", name: "资金使用申请", goal: "在可用资金范围内提交用款。", fields: [referenceSelect("futuresAccount", "期货账号", "FUTURES_ACCOUNTS"), number("requestAmount", "申请金额", { exclusiveMinimum: 0 }), readonlyNumber("availableFunds", "可用资金")], dataQueries: [{ queryCode: "loadFunds", resource: "ACCOUNT_FUNDS", parameterBindings: { account: "futuresAccount" }, loadMode: "MANUAL", refreshable: true }], checks: [{ checkCode: "withinAvailableFunds", description: "申请金额不得超过可用资金", passWhen: "requestAmount <= loadFunds.availableFunds", dependencyFieldCodes: ["requestAmount"], dataQueryCodes: ["loadFunds"] }] }),
    assertions: [assertion("funds-check", "CRITICAL", "DATA", "提交前比较申请金额与可用资金", "IR_PATH", "checks.withinAvailableFunds.passWhen", "CONTAINS", "availableFunds")],
  }),
  task({
    taskId: "dev-composite-06-conditional-tax-number", split: "DEVELOPMENT", title: "企业客户条件校验", difficulty: "COMPOSITE",
    tags: ["composite", "conditional-validation", "select"],
    input: { userRequest: "客户类型为企业时税号必填，个人客户无需填写税号。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "customer_registration", name: "客户登记", goal: "按客户类型登记必要资料。", fields: [select("customerType", "客户类型", ["企业", "个人"]), text("customerName", "客户名称"), text("taxNumber", "税号", false)], checks: [{ checkCode: "enterpriseTaxNumber", description: "企业客户必须填写税号", appliesWhen: "customerType == '企业'", passWhen: "taxNumber != ''", dependencyFieldCodes: ["customerType", "taxNumber"], dataQueryCodes: [] }] }),
    assertions: [assertion("enterprise-tax-check", "CRITICAL", "VALIDATION", "企业客户触发税号校验", "IR_PATH", "checks.enterpriseTaxNumber.appliesWhen", "CONTAINS", "企业")],
  }),
  task({
    taskId: "dev-composite-07-section-layout", split: "DEVELOPMENT", title: "客户与业务分区", difficulty: "COMPOSITE",
    tags: ["composite", "sections", "layout"],
    input: { userRequest: "页面分为客户信息和业务信息两个区域，客户名称与账号在前，业务类型与说明在后。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "business_instruction", name: "业务指令申请", goal: "分区填写客户与业务指令。", fields: [text("customerName", "客户名称"), text("accountNo", "账号"), select("businessType", "业务类型", ["新增", "变更"]), textarea("instruction", "业务说明")], sections: [{ sectionCode: "customer", title: "客户信息", fieldCodes: ["customerName", "accountNo"] }, { sectionCode: "business", title: "业务信息", fieldCodes: ["businessType", "instruction"] }] }),
    assertions: [assertion("two-sections", "NON_CRITICAL", "UI", "页面展示两个有序分区", "IR_PATH", "sections.length", "EQUALS", 2)],
  }),
  task({
    taskId: "dev-composite-08-large-amount-flag", split: "DEVELOPMENT", title: "大额标记计算", difficulty: "COMPOSITE",
    tags: ["composite", "calculation", "boolean"],
    input: { userRequest: "金额达到一千万元时自动勾选只读的大额业务标记。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "large_payment", name: "大额付款申请", goal: "识别并提交大额付款。", fields: [number("amount", "金额", { exclusiveMinimum: 0 }), boolean("largeAmount", "大额业务", true)], calculations: [{ calculationCode: "markLargeAmount", targetFieldCode: "largeAmount", expression: "amount >= 10000000", dependencyFieldCodes: ["amount"] }] }),
    assertions: [assertion("large-threshold", "CRITICAL", "CALCULATION", "一千万元触发大额标记", "IR_PATH", "calculations.markLargeAmount.expression", "EQUALS", "amount >= 10000000")],
  }),
  task({
    taskId: "dev-composite-09-account-balance", split: "DEVELOPMENT", title: "账户选择与余额刷新", difficulty: "COMPOSITE",
    tags: ["composite", "reference-data", "query", "readonly"],
    input: { userRequest: "从期货账户中选择账号并自动带出客户名称；切换账号时查询余额，也允许手动刷新。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "account_balance", name: "账户余额核查", goal: "选择账户并核查最新余额。", fields: [referenceSelect("futuresAccount", "期货账号", "FUTURES_ACCOUNTS", {}, { customerName: "customerName" }), { ...text("customerName", "客户名称"), readOnly: true }, readonlyNumber("availableFunds", "可用资金")], dataQueries: [{ queryCode: "loadFunds", resource: "ACCOUNT_FUNDS", parameterBindings: { account: "futuresAccount" }, loadMode: "ON_CHANGE", refreshable: true }] }),
    assertions: [assertion("refreshable-balance", "CRITICAL", "DATA", "账号变化加载余额且允许刷新", "IR_PATH", "dataQueries.loadFunds.refreshable", "EQUALS", true)],
  }),
  task({
    taskId: "dev-composite-10-warehouse-pledge", split: "DEVELOPMENT", title: "仓单质押综合表单", difficulty: "COMPOSITE",
    tags: ["composite", "cascade", "multi-select", "calculation", "check"],
    input: { userRequest: "生成仓单质押申请：账号带出客户，交易所级联交易编码和多选品种，计算质押金额，大额时标记并校验不超过可用资金。" },
    expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "warehouse_pledge", name: "仓单质押申请", goal: "完成仓单质押资料录入和资金核查。", fields: [referenceSelect("futuresAccount", "期货账号", "FUTURES_ACCOUNTS", {}, { customerName: "customerName" }), { ...text("customerName", "客户名称"), readOnly: true }, referenceSelect("exchangeCode", "交易所", "EXCHANGES", { account: "futuresAccount" }), referenceSelect("tradingCode", "交易编码", "TRADING_CODES", { account: "futuresAccount", exchange: "exchangeCode" }), { ...referenceSelect("productCodes", "期货品种", "FUTURES_PRODUCTS", { exchange: "exchangeCode" }), multiple: true }, number("quantity", "数量", { minimum: 1, integer: true }), number("price", "昨结算价", { exclusiveMinimum: 0 }), readonlyNumber("amount", "质押金额"), boolean("largeAmount", "大额业务", true)], dataQueries: [{ queryCode: "loadFunds", resource: "ACCOUNT_FUNDS", parameterBindings: { account: "futuresAccount" }, loadMode: "ON_CHANGE", refreshable: true }], calculations: [{ calculationCode: "calculateAmount", targetFieldCode: "amount", expression: "quantity * price", dependencyFieldCodes: ["quantity", "price"], decimalPlaces: 4 }, { calculationCode: "markLargeAmount", targetFieldCode: "largeAmount", expression: "amount >= 10000000", dependencyFieldCodes: ["amount"] }], checks: [{ checkCode: "withinFunds", description: "质押金额不得超过可用资金", passWhen: "amount <= loadFunds.availableFunds", dependencyFieldCodes: ["amount"], dataQueryCodes: ["loadFunds"] }] }),
    assertions: [assertion("full-chain", "CRITICAL", "DATA", "综合场景保留查询、计算与核查链路", "IR_PATH", "checks.withinFunds.dataQueryCodes", "CONTAINS", "loadFunds")],
  }),
];

const regressionTasks: EvaluationTask[] = [
  task({
    taskId: "hidden-regression-01-add-contact-mobile", split: "HIDDEN_REGRESSION", title: "已有模块增加联系人手机", difficulty: "EXTENSION",
    tags: ["extension", "field-addition", "regression"], input: { userRequest: "在供应商准入模块增加必填联系人手机号并校验格式，其他行为不变。", existingModule: "supplier_onboarding" }, expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "supplier_onboarding", name: "供应商准入", goal: "在现有准入表单增加联系人手机。", mode: "MODIFY_EXISTING_MODULE", fields: [text("supplierName", "供应商名称"), text("contactName", "联系人"), text("contactMobile", "联系人手机号", true, { pattern: "^1[3-9]\\d{9}$" })] }),
    assertions: [assertion("mobile-added", "CRITICAL", "VALIDATION", "新增手机号字段并校验", "IR_PATH", "fields.contactMobile.validation.pattern", "MATCHES", "1[3-9]")],
  }),
  task({
    taskId: "hidden-regression-02-tighten-amount", split: "HIDDEN_REGRESSION", title: "已有模块收紧金额规则", difficulty: "EXTENSION",
    tags: ["extension", "validation", "regression"], input: { userRequest: "将差旅报销金额上限改为五万元，保留原有大于零规则。", existingModule: "travel_expense" }, expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "travel_expense", name: "差旅报销", goal: "收紧现有报销金额范围。", mode: "MODIFY_EXISTING_MODULE", fields: [text("travelerName", "出差人"), number("amount", "报销金额", { exclusiveMinimum: 0, maximum: 50000 }), textarea("reason", "报销事由")] }),
    assertions: [assertion("amount-upper-bound", "CRITICAL", "VALIDATION", "金额不得超过五万元", "IR_PATH", "fields.amount.validation.maximum", "EQUALS", 50000)],
  }),
  task({
    taskId: "hidden-regression-03-add-section", split: "HIDDEN_REGRESSION", title: "已有模块新增补充信息分区", difficulty: "EXTENSION",
    tags: ["extension", "sections", "regression"], input: { userRequest: "在客户拜访登记中增加补充信息分区，放置后续计划和备注，原基础信息分区不变。", existingModule: "customer_visit" }, expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "customer_visit", name: "客户拜访登记", goal: "为现有拜访登记增加补充信息。", mode: "MODIFY_EXISTING_MODULE", fields: [text("customerName", "客户名称"), date("visitDate", "拜访日期"), textarea("purpose", "拜访目的"), textarea("followUpPlan", "后续计划", false), textarea("remark", "备注", false)], sections: [{ sectionCode: "basic", title: "基础信息", fieldCodes: ["customerName", "visitDate", "purpose"] }, { sectionCode: "additional", title: "补充信息", fieldCodes: ["followUpPlan", "remark"] }] }),
    assertions: [assertion("additional-section", "NON_CRITICAL", "UI", "新增补充信息分区", "IR_PATH", "sections.additional.fieldCodes", "CONTAINS", "remark")],
  }),
  task({
    taskId: "hidden-regression-04-add-funds-query", split: "HIDDEN_REGRESSION", title: "已有模块增加资金查询", difficulty: "EXTENSION",
    tags: ["extension", "query", "regression"], input: { userRequest: "在账户资料申请中增加可用资金展示，账号切换时自动刷新。", existingModule: "account_profile" }, expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "account_profile", name: "账户资料申请", goal: "为现有账户资料增加资金查询。", mode: "MODIFY_EXISTING_MODULE", fields: [referenceSelect("futuresAccount", "期货账号", "FUTURES_ACCOUNTS"), readonlyNumber("availableFunds", "可用资金")], dataQueries: [{ queryCode: "loadFunds", resource: "ACCOUNT_FUNDS", parameterBindings: { account: "futuresAccount" }, loadMode: "ON_CHANGE", refreshable: true }] }),
    assertions: [assertion("query-added", "CRITICAL", "DATA", "账号变化触发资金查询", "IR_PATH", "dataQueries.loadFunds.loadMode", "EQUALS", "ON_CHANGE")],
  }),
  task({
    taskId: "hidden-regression-05-add-tax-calculation", split: "HIDDEN_REGRESSION", title: "已有模块增加含税金额", difficulty: "EXTENSION",
    tags: ["extension", "calculation", "regression"], input: { userRequest: "在开票申请中增加税率和只读含税金额，含税金额等于开票金额乘以一加税率。", existingModule: "invoice_request" }, expectedOutcome: "GENERATION_READY",
    requirementIr: ir({ code: "invoice_request", name: "开票申请", goal: "为现有开票表单计算含税金额。", mode: "MODIFY_EXISTING_MODULE", fields: [number("invoiceAmount", "开票金额", { exclusiveMinimum: 0 }), number("taxRate", "税率", { minimum: 0, maximum: 1 }), readonlyNumber("taxIncludedAmount", "含税金额")], calculations: [{ calculationCode: "calculateTaxIncluded", targetFieldCode: "taxIncludedAmount", expression: "invoiceAmount * (1 + taxRate)", dependencyFieldCodes: ["invoiceAmount", "taxRate"], decimalPlaces: 2 }] }),
    assertions: [assertion("tax-formula", "CRITICAL", "CALCULATION", "按约定公式计算含税金额", "IR_PATH", "calculations.calculateTaxIncluded.expression", "EQUALS", "invoiceAmount * (1 + taxRate)")],
  }),
];

const adversarialTasks: EvaluationTask[] = [
  task({
    taskId: "hidden-adversarial-01-unknown-currency", split: "HIDDEN_ADVERSARIAL", title: "金额币种缺失", difficulty: "ADVERSARIAL",
    tags: ["adversarial", "ambiguity", "money"], input: { userRequest: "生成跨境付款申请，填写付款金额，但币种和换算规则以后再说。" }, expectedOutcome: "BLOCKED_REQUIREMENT",
    requirementIr: ir({ code: "cross_border_payment", name: "跨境付款申请", goal: "提交跨境付款。", fields: [number("amount", "付款金额")], ambiguities: [{ ambiguityCode: "currency-missing", impact: "BLOCKING", status: "OPEN", question: "付款币种及换算规则是什么？" }] }),
  }),
  task({
    taskId: "hidden-adversarial-02-conflicting-required", split: "HIDDEN_ADVERSARIAL", title: "必填规则冲突", difficulty: "ADVERSARIAL",
    tags: ["adversarial", "conflict", "validation"], input: { userRequest: "联系人手机必须填写，但没有手机时也必须允许直接提交。" }, expectedOutcome: "BLOCKED_REQUIREMENT",
    requirementIr: ir({ code: "contact_update", name: "联系人变更", goal: "变更联系人信息。", fields: [text("contactMobile", "联系人手机", false)], ambiguities: [{ ambiguityCode: "mobile-required-conflict", impact: "BLOCKING", status: "OPEN", question: "手机号究竟是强制必填还是允许为空？" }] }),
  }),
  task({
    taskId: "hidden-adversarial-03-unknown-api", split: "HIDDEN_ADVERSARIAL", title: "未知外部数据源", difficulty: "ADVERSARIAL",
    tags: ["adversarial", "api", "boundary"], input: { userRequest: "下拉框实时查询外部 CRM 的全部客户，但项目里没有接口定义，请直接调用。" }, expectedOutcome: "BLOCKED_REQUIREMENT",
    requirementIr: ir({ code: "crm_customer_apply", name: "CRM 客户申请", goal: "选择外部 CRM 客户。", fields: [text("customerId", "客户")], ambiguities: [{ ambiguityCode: "crm-api-missing", impact: "BLOCKING", status: "OPEN", question: "缺少允许调用的 CRM API 契约。" }] }),
  }),
  task({
    taskId: "hidden-adversarial-04-self-calculation", split: "HIDDEN_ADVERSARIAL", title: "循环计算依赖", difficulty: "ADVERSARIAL",
    tags: ["adversarial", "calculation", "cycle"], input: { userRequest: "最终金额等于最终金额加手续费，手续费为最终金额的百分之一。" }, expectedOutcome: "BLOCKED_REQUIREMENT",
    requirementIr: ir({ code: "fee_settlement", name: "费用结算", goal: "计算包含手续费的结算金额。", fields: [readonlyNumber("finalAmount", "最终金额"), readonlyNumber("fee", "手续费")], ambiguities: [{ ambiguityCode: "calculation-cycle", impact: "BLOCKING", status: "OPEN", question: "最终金额和手续费的计算基数存在循环依赖，请明确原始金额。" }] }),
  }),
  task({
    taskId: "hidden-adversarial-05-runtime-scope", split: "HIDDEN_ADVERSARIAL", title: "越界生成运行期审批页", difficulty: "ADVERSARIAL",
    tags: ["adversarial", "scope", "boundary"], input: { userRequest: "生成申请表后，再生成完整待办列表、审批详情、审批按钮和流程轨迹页面。" }, expectedOutcome: "BLOCKED_REQUIREMENT",
    requirementIr: ir({ code: "approval_console", name: "审批工作台", goal: "生成运行期审批工作台。", fields: [text("requestTitle", "申请标题")], ambiguities: [{ ambiguityCode: "runtime-out-of-scope", impact: "BLOCKING", status: "OPEN", question: "待办、审批详情和流程轨迹超出 Agent Web 允许的生成边界。" }] }),
  }),
];

export const evaluationCatalogV1: EvaluationCatalog = {
  catalogVersion: "1.0",
  irVersion: "0.1",
  frozenAt: "2026-09-04",
  tasks: [...developmentTasks, ...regressionTasks, ...adversarialTasks],
};
