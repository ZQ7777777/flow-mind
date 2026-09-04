import { createDefaultUserTaskConfigs, type BusinessRequirement, type GenerationTargetContract, type RequirementIrDraft } from "@flowmind/agent-contracts";
import { createFakeGenerationFiles } from "../pi/fake-generation-files.js";
import type { GenerationSpec } from "./generation-spec.js";

export const DETERMINISTIC_GENERATION_STRATEGY = "DETERMINISTIC_IR_V1" as const;

/**
 * Materializes the fixed frontend module exclusively from the validated IR.
 * The low-level renderer is shared with Fake Pi so its long-lived templates
 * remain covered by the existing generation and quality-pipeline fixtures.
 */
export function createDeterministicGenerationFiles(
  ir: RequirementIrDraft,
  spec: GenerationSpec,
  contract: GenerationTargetContract,
  existingRouteRegistry: string,
): Record<string, string> {
  const files = createFakeGenerationFiles(materializeRequirementFromIr(ir), spec, contract, existingRouteRegistry);
  if (spec.paths.businessForm) {
    files[spec.paths.businessForm] = businessFormSource(ir, spec);
    files[spec.paths.businessFormTest] = businessFormTestSource(ir, spec);
  }
  return files;
}

const REFERENCE_FUNCTIONS = {
  FUTURES_ACCOUNTS: "loadFuturesAccounts",
  EXCHANGES: "loadExchanges",
  TRADING_CODES: "loadTradingCodes",
  FUTURES_PRODUCTS: "loadFuturesProducts",
} as const;

function businessFormSource(ir: RequirementIrDraft, spec: GenerationSpec): string {
  const referenceFunctions = [...new Set(ir.fields.flatMap((field) => field.referenceData
    ? [REFERENCE_FUNCTIONS[field.referenceData.resource]] : []))];
  const hasFundsQuery = ir.dataQueries.some(({ resource }) => resource === "ACCOUNT_FUNDS");
  const apiImports = [...referenceFunctions, ...(hasFundsQuery ? ["loadAccountFunds"] : [])];
  const importLine = apiImports.length
    ? `import { ${apiImports.join(", ")} } from "../../../api/generated/${spec.kebabCode}/${spec.kebabCode}";\n`
    : "";
  const fieldDefinitions = JSON.stringify(ir.fields.map((field) => ({
    fieldCode: field.fieldCode,
    fieldName: field.fieldName,
    valueType: field.valueType,
    control: field.control,
    required: field.required,
    readOnly: field.readOnly,
    multiple: field.multiple,
    validation: field.validation,
    options: field.options || [],
    referenceData: field.referenceData,
  })));
  const sectionDefinitions = JSON.stringify(ir.sections);
  const optionDeclarations = ir.fields.filter(({ referenceData }) => referenceData)
    .map(({ fieldCode }) => `const ${fieldCode}Options = ref([]);`).join("\n");
  const optionMap = ir.fields.filter(({ referenceData }) => referenceData)
    .map(({ fieldCode }) => `${JSON.stringify(fieldCode)}: ${fieldCode}Options`).join(", ");
  const referenceLoaders = ir.fields.flatMap((field) => {
    const source = field.referenceData;
    if (!source) return [];
    const args = Object.values(source.parameterBindings).map((fieldCode) => `String(value(${JSON.stringify(fieldCode)}) ?? "")`);
    const loader = REFERENCE_FUNCTIONS[source.resource];
    const body = `const options = await ${loader}(${args.join(", ")}); ${field.fieldCode}Options.value = options;`;
    const dependencies = Object.values(source.parameterBindings);
    const guard = dependencies.length
      ? `if ([${dependencies.map((code) => `value(${JSON.stringify(code)})`).join(", ")}].some((item) => item === undefined || item === null || item === "")) { ${field.fieldCode}Options.value = []; return; } `
      : "";
    const watcher = dependencies.length
      ? `watch(() => [${dependencies.map((code) => `value(${JSON.stringify(code)})`).join(", ")}], () => load${pascal(field.fieldCode)}(), { immediate: true });`
      : `onMounted(() => load${pascal(field.fieldCode)}());`;
    return [`async function load${pascal(field.fieldCode)}() { ${guard}try { ${body} } catch { referenceError.value = "参考数据加载失败"; } }\n${watcher}`];
  }).join("\n");
  const calculations = ir.calculations.map((calculation) => {
    const declarations = calculation.dependencyFieldCodes.map((code) => `const ${code} = value(${JSON.stringify(code)});`).join(" ");
    const result = calculation.decimalPlaces === undefined
      ? `(${calculation.expression})`
      : `Number(Number(${calculation.expression}).toFixed(${calculation.decimalPlaces}))`;
    const fn = `calculate${pascal(calculation.targetFieldCode)}`;
    return `function ${fn}() { ${declarations} update(${JSON.stringify(calculation.targetFieldCode)}, ${result}); }\nwatch(() => [${calculation.dependencyFieldCodes.map((code) => `value(${JSON.stringify(code)})`).join(", ")}], ${fn}, { immediate: true });`;
  }).join("\n");
  const queries = ir.dataQueries.map((query) => {
    const args = Object.values(query.parameterBindings).map((code) => `String(value(${JSON.stringify(code)}) ?? "")`);
    const dependencies = Object.values(query.parameterBindings);
    const fn = query.resource === "ACCOUNT_FUNDS" ? "loadFunds" : `load${pascal(query.queryCode)}`;
    const watcher = query.loadMode === "ON_CHANGE"
      ? `watch(() => [${dependencies.map((code) => `value(${JSON.stringify(code)})`).join(", ")}], ${fn}, { immediate: true });`
      : "";
    return `const ${query.queryCode} = ref({});\nasync function ${fn}() { if ([${dependencies.map((code) => `value(${JSON.stringify(code)})`).join(", ")}].some((item) => !item)) { ${query.queryCode}.value = {}; return; } ${query.queryCode}.value = await loadAccountFunds(${args.join(", ")}); }\n${watcher}`;
  }).join("\n");
  const checks = ir.checks.map((check) => {
    const fieldDeclarations = check.dependencyFieldCodes.map((code) => `const ${code} = value(${JSON.stringify(code)});`).join(" ");
    const queryDeclarations = check.dataQueryCodes.map((code) => `const ${code} = ${code}State();`).join(" ");
    const applies = check.appliesWhen || "true";
    return `{ ${fieldDeclarations} ${queryDeclarations} if ((${applies}) && !(${check.passWhen})) errors.push(${JSON.stringify(check.description)}); }`;
  }).join("\n  ");
  const checkDefinitions = JSON.stringify(ir.checks.map((check) => ({
    code: check.checkCode,
    name: check.name || check.description,
  })));
  const queryStateHelpers = ir.dataQueries.map(({ queryCode }) => `function ${queryCode}State() { return ${queryCode}.value || {}; }`).join("\n");

  return `<script setup>
import { computed, onMounted, ref, watch } from "vue";
${importLine}const props = defineProps({ modelValue: { type: Object, required: true }, fields: { type: Array, required: true }, fieldPermissions: { type: Array, required: true }, mode: { type: String, default: "edit" }, disabled: { type: Boolean, default: false } });
const emit = defineEmits(["update:modelValue"]);
const definitions = ${fieldDefinitions};
const sectionDefinitions = ${sectionDefinitions};
const checkDefinitions = ${checkDefinitions};
void checkDefinitions;
const referenceError = ref("");
${optionDeclarations}
const optionState = { ${optionMap} };
function value(fieldCode) { return props.modelValue[fieldCode]; }
function permission(fieldCode) { return props.fieldPermissions.find((item) => item.fieldCode === fieldCode) || {}; }
function isVisible(field) { return permission(field.fieldCode).visible !== false; }
function isDisabled(field) { const allowed = permission(field.fieldCode).editable; return props.disabled || props.mode === "readonly" || field.readOnly || allowed === false; }
function update(fieldCode, nextValue) {
  if (Object.is(value(fieldCode), nextValue)) return;
  const patch = { [fieldCode]: nextValue };
  const definition = definitions.find((field) => field.fieldCode === fieldCode);
  if (definition?.referenceData?.autofillBindings) {
    const selectedValue = Array.isArray(nextValue) ? nextValue[nextValue.length - 1] : nextValue;
    const selected = (optionState[fieldCode]?.value || []).find((option) => optionValue(option) === selectedValue);
    for (const [property, targetFieldCode] of Object.entries(definition.referenceData.autofillBindings)) patch[targetFieldCode] = selected?.[property];
  }
  emit("update:modelValue", { ...props.modelValue, ...patch });
}
function inputValue(field, event) { const raw = event.target.value; update(field.fieldCode, field.valueType === "NUMBER" ? (raw === "" ? undefined : Number(raw)) : raw); }
function optionsFor(field) { return field.referenceData ? (optionState[field.fieldCode]?.value || []) : field.options; }
function optionValue(option) { return option.value ?? option.accountNo ?? option.exchangeCode ?? option.tradingCode ?? option.productCode ?? ""; }
function optionLabel(option) { return option.label ?? option.customerName ?? option.exchangeName ?? option.tradingCode ?? option.productName ?? optionValue(option); }
const effectiveFields = computed(() => definitions.map((definition) => ({ ...definition, ...(props.fields.find((field) => field.fieldCode === definition.fieldCode) || {}) })).filter(isVisible));
const sections = computed(() => sectionDefinitions.map((section) => ({ ...section, fields: section.fieldCodes.map((code) => effectiveFields.value.find((field) => field.fieldCode === code)).filter(Boolean) })).filter((section) => section.fields.length));
${referenceLoaders}
${queries}
${queryStateHelpers}
${calculations}
async function validate() {
  const errors = [];
  for (const field of effectiveFields.value) {
    const current = value(field.fieldCode);
    if ((permission(field.fieldCode).required ?? field.required) && (current === undefined || current === null || current === "" || (Array.isArray(current) && !current.length))) errors.push(field.fieldName + "为必填项");
    if (typeof current === "number" && field.validation.minimum !== undefined && current < field.validation.minimum) errors.push(field.fieldName + "不能小于" + field.validation.minimum);
    if (typeof current === "number" && field.validation.exclusiveMinimum !== undefined && current <= field.validation.exclusiveMinimum) errors.push(field.fieldName + "必须大于" + field.validation.exclusiveMinimum);
    if (typeof current === "number" && field.validation.maximum !== undefined && current > field.validation.maximum) errors.push(field.fieldName + "不能大于" + field.validation.maximum);
    if (field.validation.integer && typeof current === "number" && !Number.isInteger(current)) errors.push(field.fieldName + "必须为整数");
    if (typeof current === "string" && field.validation.minLength !== undefined && current.length < field.validation.minLength) errors.push(field.fieldName + "长度不足");
    if (typeof current === "string" && field.validation.maxLength !== undefined && current.length > field.validation.maxLength) errors.push(field.fieldName + "长度超限");
    if (typeof current === "string" && field.validation.pattern && !new RegExp(field.validation.pattern).test(current)) errors.push(field.fieldName + "格式不正确");
  }
  ${checks}
  return errors.length ? { valid: false, errors } : true;
}
defineExpose({ validate });
</script>
<template>
  <section aria-label="${escapeHtml(ir.identity.businessName)}表单">
    <p v-if="referenceError" role="alert">{{ referenceError }}</p>
    <fieldset v-for="section in sections" :key="section.sectionCode"><legend>{{ section.title }}</legend>
      <label v-for="field in section.fields" :key="field.fieldCode" :data-field-code="field.fieldCode"><span>{{ field.fieldName }}</span>
        <textarea v-if="field.control === 'TEXTAREA'" :value="value(field.fieldCode) ?? ''" :disabled="isDisabled(field)" @input="inputValue(field, $event)" />
        <input v-else-if="field.control === 'CHECKBOX'" type="checkbox" :checked="Boolean(value(field.fieldCode))" :disabled="isDisabled(field)" @change="update(field.fieldCode, $event.target.checked)" />
        <select v-else-if="field.control === 'SELECT'" :value="value(field.fieldCode)" :multiple="field.multiple" :disabled="isDisabled(field)" @change="update(field.fieldCode, field.multiple ? Array.from($event.target.selectedOptions).map(option => option.value) : $event.target.value)"><option v-for="option in optionsFor(field)" :key="optionValue(option)" :value="optionValue(option)">{{ optionLabel(option) }}</option></select>
        <input v-else :type="field.control === 'NUMBER' ? 'number' : field.control === 'DATE_PICKER' ? 'date' : 'text'" :value="value(field.fieldCode) ?? ''" :disabled="isDisabled(field)" @input="inputValue(field, $event)" />
      </label>
    </fieldset>
  </section>
</template>
`;
}

function businessFormTestSource(ir: RequirementIrDraft, spec: GenerationSpec): string {
  const first = ir.fields[0];
  return `import { mount } from "@vue/test-utils";\nimport { describe, expect, it } from "vitest";\nimport BusinessForm from "../BusinessForm.vue";\ndescribe("${spec.kebabCode} BusinessForm", () => { it("renders the authoritative IR fields", () => { const wrapper = mount(BusinessForm, { props: { modelValue: {}, fields: [], fieldPermissions: [] } }); expect(wrapper.find('[data-field-code="${first.fieldCode}"]').exists()).toBe(true); }); });\n`;
}

function pascal(value: string): string {
  return value ? `${value[0].toUpperCase()}${value.slice(1)}` : value;
}

function escapeHtml(value: string): string {
  return value.replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

export function materializeRequirementFromIr(ir: RequirementIrDraft): BusinessRequirement {
  const fieldTypes = { STRING: "string", NUMBER: "number", DATE: "date", BOOLEAN: "boolean", ENUM: "select" } as const;
  const controls = { INPUT: "input", TEXTAREA: "textarea", NUMBER: "number", DATE_PICKER: "datePicker", CHECKBOX: "checkbox", SELECT: "select" } as const;
  return {
    schemaVersion: "1.2",
    businessCode: ir.identity.businessCode,
    businessName: ir.identity.businessName,
    entryDisplayName: ir.identity.businessName,
    entryPageTitle: ir.identity.pageTitle,
    systemCode: "DETERMINISTIC_GENERATOR",
    goal: ir.identity.goal,
    participants: [],
    formFields: ir.fields.map((field, index) => ({
      fieldCode: field.fieldCode,
      fieldName: field.fieldName,
      fieldType: fieldTypes[field.valueType],
      controlType: controls[field.control],
      required: field.required,
      readOnly: field.readOnly,
      multiple: field.multiple,
      validation: field.validation,
      ...(field.options ? { options: field.options } : {}),
      ...(field.referenceData ? { referenceDataSource: {
        resource: field.referenceData.resource,
        parameterBindings: field.referenceData.parameterBindings,
        autofillBindings: field.referenceData.autofillBindings,
      } } : {}),
      sortOrder: index + 1,
    })),
    attachments: ir.attachments.map((attachment, index) => ({
      ...attachment,
      applicableNodeCodes: ["apply"],
      sortOrder: index + 1,
    })),
    nodes: [
      { nodeCode: "start", nodeName: "开始", nodeType: "START", positionX: 80, positionY: 120, sortOrder: 1 },
      { nodeCode: "apply", nodeName: "申请", nodeType: "USER_TASK", approverRule: { type: "STARTER", config: {} }, multiInstanceMode: "SINGLE", ...createDefaultUserTaskConfigs({ rejectEnabled: false }), positionX: 260, positionY: 120, sortOrder: 2 },
      { nodeCode: "review", nodeName: "审核", nodeType: "USER_TASK", approverRule: { type: "ROLE", config: { roleCode: "reviewer" } }, multiInstanceMode: "SINGLE", ...createDefaultUserTaskConfigs(), positionX: 460, positionY: 120, sortOrder: 3 },
      { nodeCode: "end", nodeName: "结束", nodeType: "END", positionX: 640, positionY: 120, sortOrder: 4 },
    ],
    edges: [
      { edgeCode: "e1", sourceNodeCode: "start", targetNodeCode: "apply", defaultEdge: false, sortOrder: 1 },
      { edgeCode: "e2", sourceNodeCode: "apply", targetNodeCode: "review", defaultEdge: false, sortOrder: 2 },
      { edgeCode: "e3", sourceNodeCode: "review", targetNodeCode: "end", defaultEdge: false, sortOrder: 3 },
    ],
    nodeFieldPermissions: ["apply", "review"].flatMap((nodeCode) => ir.fields.map((field) => ({
      nodeCode,
      fieldCode: field.fieldCode,
      visible: true,
      editable: nodeCode === "apply" && !field.readOnly,
      required: nodeCode === "apply" && field.required,
    }))),
    businessRules: [],
    frontendBehavior: {
      sections: ir.sections.map((section, index) => ({ ...section, sortOrder: index + 1 })),
      dataQueries: ir.dataQueries,
      calculations: ir.calculations.map((calculation, index) => ({ ...calculation, sortOrder: index + 1 })),
      checks: ir.checks.map(({ name, ...check }, index) => ({ checkName: name || check.description, ...check, sortOrder: index + 1 })),
    },
  };
}
