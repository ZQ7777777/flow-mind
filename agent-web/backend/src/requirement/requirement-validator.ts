import AjvModule, { type ErrorObject } from "ajv";
import ts from "typescript";
import {
  businessRequirementSchema,
  type BusinessRequirement,
  type ProcessNodeRequirement,
} from "@flowmind/agent-contracts";

export interface RequirementValidation {
  structurallyValid: boolean;
  readyForReview: boolean;
  missingItems: string[];
  ambiguities: string[];
  schemaErrors: ErrorObject[];
}

const ajv = new (AjvModule as any)({ allErrors: true, strict: false });
const validateSchema = ajv.compile(businessRequirementSchema);

export function validateRequirement(input: unknown): RequirementValidation {
  const structurallyValid = validateSchema(input);
  if (!structurallyValid) {
    return {
      structurallyValid: false,
      readyForReview: false,
      missingItems: [],
      ambiguities: [],
      schemaErrors: [...(validateSchema.errors || [])],
    };
  }
  const requirement = input as BusinessRequirement;
  const missing = new Set<string>();
  const ambiguities = new Set<string>();
  const nonEmpty = (value: string | undefined) => Boolean(value?.trim());
  if (!nonEmpty(requirement.businessCode)) missing.add("业务编码");
  if (!nonEmpty(requirement.businessName)) missing.add("业务名称");
  if (!nonEmpty(requirement.systemCode)) missing.add("系统编码");
  if (!nonEmpty(requirement.goal)) missing.add("办理目标");
  if (requirement.participants.length === 0) missing.add("参与角色");

  addDuplicateIssues(requirement.formFields.map((item) => item.fieldCode), "表单字段编码", ambiguities);
  addDuplicateIssues(requirement.attachments.map((item) => item.attachmentCode), "附件编码", ambiguities);
  addDuplicateIssues(requirement.nodes.map((item) => item.nodeCode), "节点编码", ambiguities);
  addDuplicateIssues(requirement.edges.map((item) => item.edgeCode), "连线编码", ambiguities);

  const nodeByCode = new Map(requirement.nodes.map((node) => [node.nodeCode, node]));
  const fieldByCode = new Map(requirement.formFields.map((field) => [field.fieldCode, field]));
  const starts = requirement.nodes.filter((node) => node.nodeType === "START");
  const ends = requirement.nodes.filter((node) => node.nodeType === "END");
  if (starts.length !== 1) ambiguities.add("流程必须且只能有一个开始节点");
  if (ends.length === 0) missing.add("结束节点");

  for (const field of requirement.formFields) {
    if (!nonEmpty(field.fieldCode) || !nonEmpty(field.fieldName)) missing.add("完整的表单字段");
    if (field.fieldType === "select" && (!field.options || field.options.length === 0) && !field.referenceDataSource) {
      missing.add(`下拉字段 ${field.fieldCode || field.fieldName} 的选项`);
    }
    if (field.multiple && (field.fieldType !== "select" || field.controlType !== "select")) {
      ambiguities.add(`多选字段 ${field.fieldCode || field.fieldName} 必须使用 select 字段和控件类型`);
    }
    if (field.referenceDataSource) {
      validateReferenceDataSource(field.fieldCode, field.referenceDataSource, requirement.schemaVersion,
        fieldByCode, ambiguities);
    }
  }
  for (const attachment of requirement.attachments) {
    if (attachment.required && attachment.minCount < 1) {
      ambiguities.add(`必填附件 ${attachment.attachmentCode} 的最小数量必须大于等于 1`);
    }
    if (attachment.maxCount < attachment.minCount) {
      ambiguities.add(`附件 ${attachment.attachmentCode} 的最大数量不能小于最小数量`);
    }
    for (const code of attachment.applicableNodeCodes) {
      if (!nodeByCode.has(code)) ambiguities.add(`附件 ${attachment.attachmentCode} 引用了不存在的节点 ${code}`);
    }
  }
  for (const node of requirement.nodes) validateNode(node, nodeByCode, missing, ambiguities);
  for (const edge of requirement.edges) {
    if (!nodeByCode.has(edge.sourceNodeCode) || !nodeByCode.has(edge.targetNodeCode)) {
      ambiguities.add(`连线 ${edge.edgeCode} 引用了不存在的节点`);
    }
  }
  if (starts.length === 1) validateReachability(starts[0], requirement, missing);
  validateFrontendBehavior(requirement, fieldByCode, ambiguities);

  return {
    structurallyValid: true,
    readyForReview: missing.size === 0 && ambiguities.size === 0,
    missingItems: [...missing],
    ambiguities: [...ambiguities],
    schemaErrors: [],
  };
}

function validateFrontendBehavior(
  requirement: BusinessRequirement,
  fieldByCode: Map<string, BusinessRequirement["formFields"][number]>,
  ambiguities: Set<string>,
): void {
  const behavior = requirement.frontendBehavior;
  if (!behavior) return;
  const queryCodes = new Set(behavior.dataQueries.map(({ queryCode }) => queryCode));
  addDuplicateIssues(behavior.sections.map(({ sectionCode }) => sectionCode), "页面分区编码", ambiguities);
  addDuplicateIssues([...queryCodes], "页面查询编码", ambiguities);
  addDuplicateIssues(behavior.calculations.map(({ calculationCode }) => calculationCode), "页面计算编码", ambiguities);
  addDuplicateIssues(behavior.checks.map(({ checkCode }) => checkCode), "页面核查编码", ambiguities);
  for (const section of behavior.sections) {
    for (const fieldCode of section.fieldCodes) {
      if (!fieldByCode.has(fieldCode)) ambiguities.add(`页面分区 ${section.sectionCode} 引用了不存在的字段 ${fieldCode}`);
    }
  }
  for (const query of behavior.dataQueries) {
    if (!query.parameterBindings.accountNo) ambiguities.add(`页面查询 ${query.queryCode} 缺少参数绑定 accountNo`);
    for (const fieldCode of Object.values(query.parameterBindings)) {
      if (!fieldByCode.has(fieldCode)) ambiguities.add(`页面查询 ${query.queryCode} 引用了不存在的字段 ${fieldCode}`);
    }
  }
  for (const calculation of behavior.calculations) {
    if (!fieldByCode.has(calculation.targetFieldCode)) ambiguities.add(`页面计算 ${calculation.calculationCode} 的目标字段不存在`);
    for (const fieldCode of calculation.dependencyFieldCodes) {
      if (!fieldByCode.has(fieldCode)) ambiguities.add(`页面计算 ${calculation.calculationCode} 引用了不存在的字段 ${fieldCode}`);
    }
    validateFrontendExpression(calculation.expression, calculation.calculationCode, fieldByCode, queryCodes, ambiguities);
  }
  for (const check of behavior.checks) {
    for (const fieldCode of check.dependencyFieldCodes) {
      if (!fieldByCode.has(fieldCode)) ambiguities.add(`页面核查 ${check.checkCode} 引用了不存在的字段 ${fieldCode}`);
    }
    for (const queryCode of check.dataQueryCodes) {
      if (!queryCodes.has(queryCode)) ambiguities.add(`页面核查 ${check.checkCode} 引用了不存在的查询 ${queryCode}`);
    }
    if (check.appliesWhen) validateFrontendExpression(check.appliesWhen, check.checkCode, fieldByCode, queryCodes, ambiguities);
    validateFrontendExpression(check.passWhen, check.checkCode, fieldByCode, queryCodes, ambiguities);
  }
}

function validateFrontendExpression(
  expression: string,
  label: string,
  fieldByCode: Map<string, BusinessRequirement["formFields"][number]>,
  queryCodes: Set<string>,
  ambiguities: Set<string>,
): void {
  const source = ts.createSourceFile("frontend-expression.ts", `const value = (${expression});`, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
  const diagnostics = (source as ts.SourceFile & { parseDiagnostics?: readonly ts.Diagnostic[] }).parseDiagnostics || [];
  if (diagnostics.length) {
    ambiguities.add(`页面表达式 ${label} 语法无效`);
    return;
  }
  const declaration = (source.statements[0] as ts.VariableStatement | undefined)?.declarationList.declarations[0];
  const root = declaration?.initializer;
  if (!root || !isSafeExpression(root, fieldByCode, queryCodes)) ambiguities.add(`页面表达式 ${label} 包含不允许的语法或未知标识符`);
}

const SAFE_BINARY_OPERATORS = new Set<ts.SyntaxKind>([
  ts.SyntaxKind.PlusToken, ts.SyntaxKind.MinusToken, ts.SyntaxKind.AsteriskToken, ts.SyntaxKind.SlashToken,
  ts.SyntaxKind.PercentToken, ts.SyntaxKind.LessThanToken, ts.SyntaxKind.LessThanEqualsToken,
  ts.SyntaxKind.GreaterThanToken, ts.SyntaxKind.GreaterThanEqualsToken, ts.SyntaxKind.EqualsEqualsToken,
  ts.SyntaxKind.EqualsEqualsEqualsToken, ts.SyntaxKind.ExclamationEqualsToken, ts.SyntaxKind.ExclamationEqualsEqualsToken,
  ts.SyntaxKind.AmpersandAmpersandToken, ts.SyntaxKind.BarBarToken,
]);

function isSafeExpression(
  node: ts.Expression,
  fieldByCode: Map<string, BusinessRequirement["formFields"][number]>,
  queryCodes: Set<string>,
): boolean {
  if (ts.isParenthesizedExpression(node)) return isSafeExpression(node.expression, fieldByCode, queryCodes);
  if (ts.isNumericLiteral(node) || ts.isStringLiteral(node)
    || node.kind === ts.SyntaxKind.TrueKeyword || node.kind === ts.SyntaxKind.FalseKeyword
    || node.kind === ts.SyntaxKind.NullKeyword) return true;
  if (ts.isIdentifier(node)) return fieldByCode.has(node.text);
  if (ts.isPropertyAccessExpression(node)) {
    return ts.isIdentifier(node.expression) && queryCodes.has(node.expression.text) && ts.isIdentifier(node.name);
  }
  if (ts.isPrefixUnaryExpression(node)) {
    return [ts.SyntaxKind.PlusToken, ts.SyntaxKind.MinusToken, ts.SyntaxKind.ExclamationToken].includes(node.operator)
      && isSafeExpression(node.operand, fieldByCode, queryCodes);
  }
  if (ts.isBinaryExpression(node)) {
    return SAFE_BINARY_OPERATORS.has(node.operatorToken.kind)
      && isSafeExpression(node.left, fieldByCode, queryCodes)
      && isSafeExpression(node.right, fieldByCode, queryCodes);
  }
  return false;
}

function validateReferenceDataSource(
  fieldCode: string,
  source: NonNullable<BusinessRequirement["formFields"][number]["referenceDataSource"]>,
  schemaVersion: BusinessRequirement["schemaVersion"],
  fieldByCode: Map<string, BusinessRequirement["formFields"][number]>,
  ambiguities: Set<string>,
): void {
  if (schemaVersion === "1.0") {
    ambiguities.add(`动态参考数据字段 ${fieldCode} 必须使用需求结构 1.1 或 1.2`);
  }
  const requiredParameters: Record<typeof source.resource, string[]> = {
    FUTURES_ACCOUNTS: [],
    EXCHANGES: [],
    TRADING_CODES: ["accountNo", "exchangeCode"],
    FUTURES_PRODUCTS: ["exchangeCode"],
  };
  const parameterBindings = source.parameterBindings || {};
  for (const parameter of requiredParameters[source.resource]) {
    if (!parameterBindings[parameter]) {
      ambiguities.add(`动态参考数据字段 ${fieldCode} 缺少参数绑定 ${parameter}`);
    }
  }
  for (const [parameter, dependencyFieldCode] of Object.entries(parameterBindings)) {
    if (dependencyFieldCode === fieldCode) {
      ambiguities.add(`动态参考数据字段 ${fieldCode} 的参数 ${parameter} 不能依赖自身`);
    } else if (!fieldByCode.has(dependencyFieldCode)) {
      ambiguities.add(`动态参考数据字段 ${fieldCode} 引用了不存在的依赖字段 ${dependencyFieldCode}`);
    }
  }
  for (const [responseProperty, targetFieldCode] of Object.entries(source.autofillBindings || {})) {
    if (targetFieldCode === fieldCode) {
      ambiguities.add(`动态参考数据字段 ${fieldCode} 的自动带出属性 ${responseProperty} 不能写回自身`);
    } else if (!fieldByCode.has(targetFieldCode)) {
      ambiguities.add(`动态参考数据字段 ${fieldCode} 的自动带出目标不存在：${targetFieldCode}`);
    }
  }
}

function addDuplicateIssues(values: string[], label: string, target: Set<string>): void {
  const seen = new Set<string>();
  for (const value of values) {
    if (seen.has(value)) target.add(`${label}重复：${value}`);
    seen.add(value);
  }
}

function validateNode(
  node: ProcessNodeRequirement,
  nodeByCode: Map<string, ProcessNodeRequirement>,
  missing: Set<string>,
  ambiguities: Set<string>,
): void {
  if (node.nodeType === "USER_TASK") {
    if (!node.approverRule) missing.add(`用户任务 ${node.nodeName} 的审批人规则`);
    if (node.approverRule) validateApproverRuleConfig(node, ambiguities);
    if (!node.multiInstanceMode) missing.add(`用户任务 ${node.nodeName} 的多人模式`);
    validateRuntimeTaskPolicies(node, nodeByCode, ambiguities);
  } else if (node.nodeType === "NOTICE") {
    if (!node.approverRule) missing.add(`知会节点 ${node.nodeName} 的接收人规则`);
    if (node.approverRule) validateApproverRuleConfig(node, ambiguities);
    if (node.multiInstanceMode && node.multiInstanceMode !== "SINGLE") {
      ambiguities.add(`知会节点 ${node.nodeName} 不支持或签或会签`);
    }
    for (const [field, value] of Object.entries(node.noticeConfig || {})) {
      if ((field === "title" || field === "content") && !hasTextValue(value)) {
        ambiguities.add(`知会节点 ${node.nodeName} 的 ${field} 必须是非空文本`);
      }
    }
    if (node.listenerConfig !== undefined || node.timeoutConfig !== undefined || node.reminderConfig !== undefined) {
      ambiguities.add(`知会节点 ${node.nodeName} 不支持任务监听、超时或催办配置`);
    }
  } else if (node.listenerConfig !== undefined || node.timeoutConfig !== undefined
    || node.reminderConfig !== undefined || node.noticeConfig !== undefined) {
    ambiguities.add(`节点 ${node.nodeName} 的运行时配置仅支持 USER_TASK`);
  }
  if (node.nodeType.startsWith("PARALLEL_")) {
    if (!node.pairedGatewayCode) {
      missing.add(`并行网关 ${node.nodeName} 的配对网关`);
    } else if (!nodeByCode.has(node.pairedGatewayCode)) {
      ambiguities.add(`并行网关 ${node.nodeName} 的配对节点不存在`);
    }
  }
}

function validateApproverRuleConfig(
  node: ProcessNodeRequirement,
  ambiguities: Set<string>,
): void {
  const config = record(node.approverRule?.config) || {};
  const ruleType = node.approverRule?.type;
  if (ruleType === "USER") {
    const userIds = Array.isArray(config.userIds) ? config.userIds : [];
    if (!userIds.some(hasTextValue)) {
      ambiguities.add(`节点 ${node.nodeName} 的 USER 审批规则必须配置 userIds`);
    }
    return;
  }
  if (ruleType === "DEPARTMENT" && !hasTextValue(config.departmentId)) {
    ambiguities.add(`节点 ${node.nodeName} 的 DEPARTMENT 审批规则必须配置 departmentId`);
    return;
  }
  if (ruleType === "ROLE" && !hasTextValue(config.roleCode)) {
    ambiguities.add(`节点 ${node.nodeName} 的 ROLE 审批规则必须配置 roleCode`);
    return;
  }
  if (ruleType === "ROLE_IN_DEPARTMENT" && !hasTextValue(config.roleCode)) {
    ambiguities.add(`节点 ${node.nodeName} 的 ROLE_IN_DEPARTMENT 审批规则必须配置 roleCode`);
    return;
  }
  if (ruleType === "APPROVER_EXPRESSION" && !hasTextValue(config.expression)) {
    ambiguities.add(`节点 ${node.nodeName} 的 APPROVER_EXPRESSION 审批规则必须配置 expression`);
  }
}

function validateRuntimeTaskPolicies(
  node: ProcessNodeRequirement,
  nodeByCode: Map<string, ProcessNodeRequirement>,
  ambiguities: Set<string>,
): void {
  const rules = record(node.listenerConfig?.taskActionRules);
  const reject = record(rules?.reject);
  if (reject?.enabled === true) {
    if (!Array.isArray(reject.targetNodeCodes) || reject.targetNodeCodes.length === 0) {
      ambiguities.add(`节点 ${node.nodeName} 启用驳回时必须配置目标节点`);
    } else {
      for (const targetNodeCode of reject.targetNodeCodes) {
        const target = typeof targetNodeCode === "string" ? nodeByCode.get(targetNodeCode) : undefined;
        if (!target || target.nodeType !== "USER_TASK") {
          ambiguities.add(`节点 ${node.nodeName} 的驳回目标必须是已存在的用户任务`);
        }
      }
    }
  }
  const directSend = record(rules?.directSend);
  if (directSend?.enabled === true && directSend.targetMode !== "REJECT_SOURCE") {
    ambiguities.add(`节点 ${node.nodeName} 的直送目标模式仅支持 REJECT_SOURCE`);
  }
  const timeout = node.timeoutConfig;
  if (timeout?.enabled === true
    && (!Number.isInteger(timeout.durationMinutes) || Number(timeout.durationMinutes) < 0)) {
    ambiguities.add(`节点 ${node.nodeName} 的超时时长必须是非负整数分钟`);
  }
  const reminder = node.reminderConfig;
  if (reminder?.maxCount !== undefined
    && (!Number.isInteger(reminder.maxCount) || Number(reminder.maxCount) < 0)) {
    ambiguities.add(`节点 ${node.nodeName} 的提醒次数必须是非负整数`);
  }
}

function record(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown> : undefined;
}

function hasTextValue(value: unknown): boolean {
  return typeof value === "string" && value.trim().length > 0;
}

function validateReachability(
  start: ProcessNodeRequirement,
  requirement: BusinessRequirement,
  missing: Set<string>,
): void {
  const outgoing = new Map<string, string[]>();
  for (const edge of requirement.edges) {
    outgoing.set(edge.sourceNodeCode, [...(outgoing.get(edge.sourceNodeCode) || []), edge.targetNodeCode]);
  }
  const visited = new Set<string>();
  const queue = [start.nodeCode];
  while (queue.length) {
    const current = queue.shift()!;
    if (visited.has(current)) continue;
    visited.add(current);
    queue.push(...(outgoing.get(current) || []));
  }
  for (const node of requirement.nodes) {
    if (!visited.has(node.nodeCode)) missing.add(`从开始节点可达的节点：${node.nodeName}`);
  }
  if (!requirement.nodes.some((node) => node.nodeType === "END" && visited.has(node.nodeCode))) {
    missing.add("从开始节点可达的结束路径");
  }
}
