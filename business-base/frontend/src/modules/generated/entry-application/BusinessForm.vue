<script setup lang="ts">
import { computed, ref } from "vue";
import type { WorkflowFieldPermission, WorkflowFormField } from "../../../types/workflow";

/**
 * 入金申请（entry_application）生成的表单区域组件。
 *
 * 职责边界：本组件只负责渲染确认过的业务字段、应用运行时字段权限、
 * 发出不可变的 update:modelValue 更新并暴露 validate() 校验入口。
 * 它不发起任何 HTTP 请求、不上传附件、不渲染工作流按钮、不持有提交状态；
 * 上下文加载、附件与提交均由共享的 WorkflowStartShell / 任务详情页负责。
 */

/** 确认需求中声明的字段，按 fieldCode 精确匹配，禁止渲染未确认字段。 */
const CONFIRMED_FIELD_CODES: ReadonlyArray<string> = [
  "applicationNo",
  "transferType",
  "bankName",
  "bankBranch",
  "accountName",
  "bankAccountNo",
  "amount",
  "amountInWords",
  "fundAllocator",
  "allocatorIdNo",
  "remark",
];

const props = withDefaults(
  defineProps<{
    modelValue: Record<string, unknown>;
    fields: WorkflowFormField[];
    fieldPermissions: WorkflowFieldPermission[];
    mode?: "edit" | "readonly";
    disabled?: boolean;
  }>(),
  {
    mode: "edit",
    disabled: false,
  },
);

const emit = defineEmits<{
  (event: "update:modelValue", value: Record<string, unknown>): void;
}>();

interface ResolvedField {
  field: WorkflowFormField;
  permission: WorkflowFieldPermission | undefined;
  required: boolean;
  editable: boolean;
  rules: Record<string, unknown>;
}

function resolveRules(field: WorkflowFormField): Record<string, unknown> {
  if (field.validation && typeof field.validation === "object") {
    return field.validation;
  }
  if (field.validationRule) {
    try {
      const parsed: unknown = JSON.parse(field.validationRule);
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>;
      }
    } catch {
      // 校验规则解析失败时退化为仅必填校验，不阻塞表单渲染。
    }
  }
  return {};
}

/**
 * 仅渲染确认过的字段，按 sortOrder 排序并合并运行时字段权限。
 * 服务端下发的 fieldPermissions 是权威数据源：
 * visible=false 隐藏，editable=false 禁用，required 合并定义与节点权限。
 */
const resolvedFields = computed<ResolvedField[]>(() => {
  const confirmed = new Set(CONFIRMED_FIELD_CODES);
  return props.fields
    .filter((field) => confirmed.has(field.fieldCode))
    .slice()
    .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
    .map((field) => {
      const permission = props.fieldPermissions.find((item) => item.fieldCode === field.fieldCode);
      const visible = permission?.visible !== false;
      const editable =
        props.mode === "edit" && !props.disabled && permission?.editable !== false;
      return {
        field,
        permission,
        visible,
        required: field.required === true || permission?.required === true,
        editable,
        rules: resolveRules(field),
      };
    })
    .filter((entry) => entry.visible);
});

const errors = ref<Record<string, string>>({});

function isEmptyValue(value: unknown): boolean {
  return value === null || value === undefined || (typeof value === "string" && value.trim() === "");
}

function requiredMessage(field: WorkflowFormField): string {
  if (field.controlType === "select") {
    return `请选择${field.fieldName}`;
  }
  return `请输入${field.fieldName}`;
}

function validateResolvedField(entry: ResolvedField): string | undefined {
  const field = entry.field;
  const value = props.modelValue[field.fieldCode];
  if (isEmptyValue(value)) {
    return entry.required ? requiredMessage(field) : undefined;
  }
  const text = String(value);
  const pattern = typeof entry.rules.pattern === "string" ? entry.rules.pattern : undefined;
  if (pattern && !new RegExp(pattern).test(text)) {
    return `${field.fieldName}格式不正确`;
  }
  const minLength = typeof entry.rules.minLength === "number" ? entry.rules.minLength : undefined;
  const maxLength = typeof entry.rules.maxLength === "number" ? entry.rules.maxLength : undefined;
  if (minLength !== undefined && text.length < minLength) {
    return `${field.fieldName}长度不能少于 ${minLength} 位`;
  }
  if (maxLength !== undefined && text.length > maxLength) {
    return `${field.fieldName}长度不能超过 ${maxLength} 位`;
  }
  if (field.fieldType === "number" || field.controlType === "number") {
    const numeric = Number(text);
    if (Number.isNaN(numeric)) {
      return `${field.fieldName}必须是数字`;
    }
    // 格式类校验（小数位数）优先于范围类校验（最小值），
    // 用户应先修正格式错误的值，再处理范围问题。
    const maxDecimalPlaces =
      typeof entry.rules.maxDecimalPlaces === "number" ? entry.rules.maxDecimalPlaces : undefined;
    if (maxDecimalPlaces !== undefined) {
      const decimals = text.includes(".") ? text.split(".")[1]!.length : 0;
      if (decimals > maxDecimalPlaces) {
        return `${field.fieldName}最多 ${maxDecimalPlaces} 位小数`;
      }
    }
    const minimum = typeof entry.rules.minimum === "number" ? entry.rules.minimum : undefined;
    if (minimum !== undefined && numeric < minimum) {
      return `${field.fieldName}不能小于 ${minimum}`;
    }
  }
  return undefined;
}

/** 暴露给共享外壳的校验入口：客户端校验只提升反馈，不替代后端权威校验。 */
async function validate(): Promise<boolean> {
  const nextErrors: Record<string, string> = {};
  for (const entry of resolvedFields.value) {
    const message = validateResolvedField(entry);
    if (message) {
      nextErrors[entry.field.fieldCode] = message;
    }
  }
  errors.value = nextErrors;
  return Object.keys(nextErrors).length === 0;
}

defineExpose({ validate });

/** 以新对象发出更新，保持 modelValue 不可变，便于上层做幂等与状态比较。 */
function updateValue(fieldCode: string, value: unknown): void {
  emit("update:modelValue", { ...props.modelValue, [fieldCode]: value });
}

function normalizeInputValue(fieldCode: string, rawValue: string): unknown {
  const entry = resolvedFields.value.find((item) => item.field.fieldCode === fieldCode);
  const field = entry?.field;
  if (field?.fieldType === "number" || field?.controlType === "number") {
    return rawValue.trim() === "" ? "" : Number(rawValue);
  }
  return rawValue;
}

function onInput(fieldCode: string, event: Event): void {
  const input = event.target as HTMLInputElement;
  updateValue(fieldCode, normalizeInputValue(fieldCode, input.value));
}

function onSelect(fieldCode: string, event: Event): void {
  updateValue(fieldCode, (event.target as HTMLSelectElement).value);
}
</script>

<template>
  <section class="entry-application-form" aria-label="入金申请表单">
    <div
      v-for="entry in resolvedFields"
      :key="entry.field.fieldCode"
      class="form-field"
      :data-field-code="entry.field.fieldCode"
    >
      <label :for="`entry-application-${entry.field.fieldCode}`" class="field-label">
        {{ entry.field.fieldName }}<b v-if="entry.required" class="required-mark"> *</b>
      </label>
      <select
        v-if="entry.field.controlType === 'select'"
        :id="`entry-application-${entry.field.fieldCode}`"
        :value="String(modelValue[entry.field.fieldCode] ?? '')"
        :disabled="!entry.editable"
        :aria-invalid="errors[entry.field.fieldCode] ? 'true' : undefined"
        @change="onSelect(entry.field.fieldCode, $event)"
      >
        <option value="" disabled>请选择{{ entry.field.fieldName }}</option>
        <option
          v-for="option in entry.field.options || []"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </option>
      </select>
      <textarea
        v-else-if="entry.field.controlType === 'textarea'"
        :id="`entry-application-${entry.field.fieldCode}`"
        :value="String(modelValue[entry.field.fieldCode] ?? '')"
        :disabled="!entry.editable"
        rows="3"
        :aria-invalid="errors[entry.field.fieldCode] ? 'true' : undefined"
        @input="onInput(entry.field.fieldCode, $event)"
      ></textarea>
      <input
        v-else-if="entry.field.controlType === 'number'"
        :id="`entry-application-${entry.field.fieldCode}`"
        type="number"
        step="0.01"
        :value="String(modelValue[entry.field.fieldCode] ?? '')"
        :disabled="!entry.editable"
        :aria-invalid="errors[entry.field.fieldCode] ? 'true' : undefined"
        @input="onInput(entry.field.fieldCode, $event)"
      />
      <input
        v-else
        :id="`entry-application-${entry.field.fieldCode}`"
        type="text"
        :value="String(modelValue[entry.field.fieldCode] ?? '')"
        :disabled="!entry.editable"
        :aria-invalid="errors[entry.field.fieldCode] ? 'true' : undefined"
        @input="onInput(entry.field.fieldCode, $event)"
      />
      <p v-if="errors[entry.field.fieldCode]" class="field-error" role="alert">
        {{ errors[entry.field.fieldCode] }}
      </p>
    </div>
  </section>
</template>

<style scoped>
.entry-application-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px 18px;
}

.form-field {
  display: grid;
  gap: 6px;
}

.field-label {
  color: #17202a;
  font-size: 14px;
  font-weight: 600;
}

.required-mark {
  color: #be123c;
}

.entry-application-form input,
.entry-application-form select,
.entry-application-form textarea {
  border: 1px solid #d8dee8;
  border-radius: 6px;
  min-height: 34px;
  padding: 6px 10px;
  color: #17202a;
  font: inherit;
  width: 100%;
  box-sizing: border-box;
}

.entry-application-form input:disabled,
.entry-application-form select:disabled,
.entry-application-form textarea:disabled {
  background: #f1f4f9;
  color: #5d6978;
  cursor: not-allowed;
}

.field-error {
  margin: 0;
  color: #be123c;
  font-size: 12px;
}
</style>
