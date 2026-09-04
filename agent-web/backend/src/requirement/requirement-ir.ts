import AjvModule, { type ErrorObject } from "ajv";
import {
  requirementIrDraftSchema,
  type BusinessRequirement,
  type EvaluationAssertion,
  type RequirementIrDraft,
  type RequirementIrField,
} from "@flowmind/agent-contracts";
import { validateGenerationRequirement } from "../generation/generation-spec.js";
import { validateRequirement } from "./requirement-validator.js";

export interface RequirementIrValidation {
  structurallyValid: boolean;
  generationReady: boolean;
  schemaErrors: ErrorObject[];
  semanticErrors: string[];
}

const validateSchema = new (AjvModule as any)({ allErrors: true, strict: false }).compile(requirementIrDraftSchema);

export function deriveRequirementIr(
  requirement: BusinessRequirement,
  generationMode: RequirementIrDraft["generationMode"] = "CREATE_STANDARD_MODULE",
): RequirementIrDraft {
  const validation = validateRequirement(requirement);
  const issues = [
    ...validation.missingItems.map((message) => `缺少：${message}`),
    ...validation.ambiguities,
    ...validateGenerationRequirement(requirement),
  ];
  const fields = requirement.formFields.map(toIrField);
  const frontend = requirement.frontendBehavior;
  return {
    irVersion: "0.1",
    generationMode,
    identity: {
      businessCode: requirement.businessCode,
      businessName: requirement.businessName,
      pageTitle: requirement.entryPageTitle || `发起${requirement.businessName}`,
      goal: requirement.goal,
    },
    fields,
    attachments: requirement.attachments
      .filter(({ applicableNodeCodes }) => applicableNodeCodes.includes("apply"))
      .map(({ attachmentCode, attachmentName, required, allowedExtensions, maxSizeBytes, minCount, maxCount }) => ({
        attachmentCode, attachmentName, required, allowedExtensions, maxSizeBytes, minCount, maxCount,
      })),
    sections: frontend?.sections.map(({ sectionCode, title, fieldCodes }) => ({ sectionCode, title, fieldCodes }))
      ?? [{ sectionCode: "business-info", title: "业务信息", fieldCodes: fields.map(({ fieldCode }) => fieldCode) }],
    dataQueries: frontend?.dataQueries.map(({ queryCode, resource, parameterBindings, loadMode, refreshable }) => ({
      queryCode, resource, parameterBindings, loadMode, refreshable,
    })) ?? [],
    calculations: frontend?.calculations.map(({ calculationCode, targetFieldCode, expression, dependencyFieldCodes, decimalPlaces }) => ({
      calculationCode, targetFieldCode, expression, dependencyFieldCodes, ...(decimalPlaces === undefined ? {} : { decimalPlaces }),
    })) ?? [],
    checks: frontend?.checks.map(({ checkCode, checkName, description, appliesWhen, passWhen, dependencyFieldCodes, dataQueryCodes }) => ({
      checkCode, name: checkName, description, ...(appliesWhen ? { appliesWhen } : {}), passWhen, dependencyFieldCodes, dataQueryCodes,
    })) ?? [],
    submission: {
      processCode: requirement.businessCode,
      action: "START_AND_SUBMIT",
      payloadFieldCodes: fields.map(({ fieldCode }) => fieldCode),
    },
    ambiguities: [...new Set(issues)].map((question, index) => ({
      ambiguityCode: `requirement-${index + 1}`,
      impact: "BLOCKING",
      status: "OPEN",
      question,
    })),
    sourceRefs: [`requirement://${requirement.businessCode}/${requirement.schemaVersion}`],
  };
}

export function validateRequirementIr(input: unknown): RequirementIrValidation {
  const structurallyValid = validateSchema(input);
  if (!structurallyValid) {
    return { structurallyValid: false, generationReady: false, schemaErrors: [...(validateSchema.errors ?? [])], semanticErrors: [] };
  }
  const ir = input as RequirementIrDraft;
  const semanticErrors: string[] = [];
  const fieldCodes = unique(ir.fields.map(({ fieldCode }) => fieldCode), "field", semanticErrors);
  const queryCodes = unique(ir.dataQueries.map(({ queryCode }) => queryCode), "query", semanticErrors);
  unique(ir.attachments.map(({ attachmentCode }) => attachmentCode), "attachment", semanticErrors);
  unique(ir.calculations.map(({ calculationCode }) => calculationCode), "calculation", semanticErrors);
  unique(ir.checks.map(({ checkCode }) => checkCode), "check", semanticErrors);

  for (const section of ir.sections) for (const code of section.fieldCodes) requireCode(fieldCodes, code, `section ${section.sectionCode}`, semanticErrors);
  for (const code of ir.submission.payloadFieldCodes) requireCode(fieldCodes, code, "submission", semanticErrors);
  for (const field of ir.fields) {
    if (field.multiple && field.control !== "SELECT") semanticErrors.push(`${field.fieldCode}: multiple values require SELECT.`);
    for (const code of Object.values(field.referenceData?.parameterBindings ?? {})) requireCode(fieldCodes, code, `${field.fieldCode} reference data`, semanticErrors);
    for (const code of Object.values(field.referenceData?.autofillBindings ?? {})) requireCode(fieldCodes, code, `${field.fieldCode} autofill`, semanticErrors);
  }
  for (const query of ir.dataQueries) for (const code of Object.values(query.parameterBindings)) requireCode(fieldCodes, code, `query ${query.queryCode}`, semanticErrors);
  for (const calculation of ir.calculations) {
    requireCode(fieldCodes, calculation.targetFieldCode, `calculation ${calculation.calculationCode}`, semanticErrors);
    for (const code of calculation.dependencyFieldCodes) requireCode(fieldCodes, code, `calculation ${calculation.calculationCode}`, semanticErrors);
    if (calculation.dependencyFieldCodes.includes(calculation.targetFieldCode)) semanticErrors.push(`${calculation.calculationCode}: target cannot depend on itself.`);
  }
  for (const check of ir.checks) {
    for (const code of check.dependencyFieldCodes) requireCode(fieldCodes, code, `check ${check.checkCode}`, semanticErrors);
    for (const code of check.dataQueryCodes) requireCode(queryCodes, code, `check ${check.checkCode}`, semanticErrors);
  }
  const blocking = ir.ambiguities.some(({ impact, status }) => impact === "BLOCKING" && status === "OPEN");
  return { structurallyValid: true, generationReady: !blocking && semanticErrors.length === 0, schemaErrors: [], semanticErrors };
}

export function deriveAcceptanceAssertions(ir: RequirementIrDraft): EvaluationAssertion[] {
  const assertions: EvaluationAssertion[] = [
    makeAssertion("submission-process", "CRITICAL", "SUBMISSION", "提交使用 IR 中的流程编码", "IR_PATH", "submission.processCode", "EQUALS", ir.submission.processCode),
    makeAssertion("submission-action", "CRITICAL", "SUBMISSION", "只允许发起并提交动作", "UI_BEHAVIOR", "submission.action", "EQUALS", "START_AND_SUBMIT"),
  ];
  for (const field of ir.fields) {
    assertions.push(makeAssertion(`field-${field.fieldCode}`, "CRITICAL", "IR", `生成字段 ${field.fieldName}`, "IR_PATH", `fields.${field.fieldCode}`, "EXISTS", true));
    if (field.required) assertions.push(makeAssertion(`required-${field.fieldCode}`, "CRITICAL", "VALIDATION", `${field.fieldName} 必填`, "UI_BEHAVIOR", `fields.${field.fieldCode}.required`, "EQUALS", true));
  }
  for (const attachment of ir.attachments) assertions.push(makeAssertion(`attachment-${attachment.attachmentCode}`, "CRITICAL", "UI", `生成附件 ${attachment.attachmentName}`, "UI_BEHAVIOR", `attachments.${attachment.attachmentCode}`, "EXISTS", true));
  for (const query of ir.dataQueries) assertions.push(makeAssertion(`query-${query.queryCode}`, "CRITICAL", "DATA", `实现数据查询 ${query.queryCode}`, "UI_BEHAVIOR", `dataQueries.${query.queryCode}`, "EXISTS", true));
  for (const calculation of ir.calculations) assertions.push(makeAssertion(`calculation-${calculation.calculationCode}`, "CRITICAL", "CALCULATION", `实现计算 ${calculation.calculationCode}`, "UI_BEHAVIOR", `calculations.${calculation.calculationCode}`, "EXISTS", true));
  for (const check of ir.checks) assertions.push(makeAssertion(`check-${check.checkCode}`, "CRITICAL", "VALIDATION", `实现核查 ${check.checkCode}`, "UI_BEHAVIOR", `checks.${check.checkCode}`, "EXISTS", true));
  return assertions;
}

function toIrField(field: BusinessRequirement["formFields"][number]): RequirementIrField {
  const valueTypes = { string: "STRING", number: "NUMBER", date: "DATE", boolean: "BOOLEAN", select: "ENUM" } as const;
  const controls = { input: "INPUT", textarea: "TEXTAREA", number: "NUMBER", datePicker: "DATE_PICKER", checkbox: "CHECKBOX", select: "SELECT" } as const;
  return {
    fieldCode: field.fieldCode, fieldName: field.fieldName, valueType: valueTypes[field.fieldType], control: controls[field.controlType],
    required: field.required, readOnly: field.readOnly ?? false, multiple: field.multiple ?? false, validation: field.validation,
    ...(field.options ? { options: field.options } : {}),
    ...(field.referenceDataSource ? { referenceData: {
      resource: field.referenceDataSource.resource,
      parameterBindings: field.referenceDataSource.parameterBindings ?? {},
      autofillBindings: field.referenceDataSource.autofillBindings ?? {},
    } } : {}),
  };
}

function makeAssertion(assertionId: string, severity: EvaluationAssertion["severity"], area: EvaluationAssertion["area"], description: string, kind: EvaluationAssertion["oracle"]["kind"], target: string, operator: EvaluationAssertion["oracle"]["operator"], expected: string | number | boolean): EvaluationAssertion {
  return { assertionId, severity, area, description, oracle: { kind, target, operator, expected } };
}

function unique(values: string[], label: string, errors: string[]): Set<string> {
  const result = new Set<string>();
  for (const value of values) {
    if (result.has(value)) errors.push(`Duplicate ${label} code: ${value}.`);
    result.add(value);
  }
  return result;
}

function requireCode(allowed: Set<string>, code: string, owner: string, errors: string[]): void {
  if (!allowed.has(code)) errors.push(`${owner} references unknown code ${code}.`);
}
