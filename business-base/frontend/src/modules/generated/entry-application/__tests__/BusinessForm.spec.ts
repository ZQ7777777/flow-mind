import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import type { WorkflowFieldPermission, WorkflowFormField } from "../../../../types/workflow";
import BusinessForm from "../BusinessForm.vue";

const FIELD_CODES = [
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
] as const;

function buildFields(): WorkflowFormField[] {
  return [
    { fieldCode: "applicationNo", fieldName: "申请单号", fieldType: "string", controlType: "input", required: false, sortOrder: 1 },
    {
      fieldCode: "transferType",
      fieldName: "划款类型",
      fieldType: "select",
      controlType: "select",
      required: true,
      sortOrder: 2,
      options: [
        { label: "现金", value: "CASH" },
        { label: "转账", value: "TRANSFER" },
        { label: "汇票", value: "DRAFT" },
      ],
    },
    {
      fieldCode: "bankName",
      fieldName: "开户银行",
      fieldType: "select",
      controlType: "select",
      required: true,
      sortOrder: 3,
      options: [
        { label: "工商银行", value: "ICBC" },
        { label: "建设银行", value: "CCB" },
      ],
    },
    { fieldCode: "bankBranch", fieldName: "银行站点", fieldType: "string", controlType: "input", required: false, sortOrder: 4 },
    { fieldCode: "accountName", fieldName: "开户户名", fieldType: "string", controlType: "input", required: true, sortOrder: 5 },
    {
      fieldCode: "bankAccountNo",
      fieldName: "银行账号",
      fieldType: "string",
      controlType: "input",
      required: true,
      sortOrder: 6,
      validation: { minLength: 10, maxLength: 10, pattern: "^\\d{10}$" },
    },
    {
      fieldCode: "amount",
      fieldName: "金额",
      fieldType: "number",
      controlType: "number",
      required: true,
      sortOrder: 7,
      validation: { minimum: 0.01, maxDecimalPlaces: 2 },
    },
    { fieldCode: "amountInWords", fieldName: "金额大写", fieldType: "string", controlType: "input", required: true, sortOrder: 8 },
    { fieldCode: "fundAllocator", fieldName: "资金调拨人", fieldType: "string", controlType: "input", required: true, sortOrder: 9 },
    { fieldCode: "allocatorIdNo", fieldName: "调拨人证件号码", fieldType: "string", controlType: "input", required: false, sortOrder: 10 },
    { fieldCode: "remark", fieldName: "备注", fieldType: "string", controlType: "textarea", required: false, sortOrder: 11 },
  ];
}

/** 与确认需求 nodeFieldPermissions (apply 节点) 一致的字段权限。 */
function buildPermissions(): WorkflowFieldPermission[] {
  return [
    { nodeCode: "apply", fieldCode: "applicationNo", visible: true, editable: false, required: false },
    { nodeCode: "apply", fieldCode: "transferType", visible: true, editable: true, required: true },
    { nodeCode: "apply", fieldCode: "bankName", visible: true, editable: true, required: true },
    { nodeCode: "apply", fieldCode: "bankBranch", visible: true, editable: true, required: false },
    { nodeCode: "apply", fieldCode: "accountName", visible: true, editable: true, required: true },
    { nodeCode: "apply", fieldCode: "bankAccountNo", visible: true, editable: true, required: true },
    { nodeCode: "apply", fieldCode: "amount", visible: true, editable: true, required: true },
    { nodeCode: "apply", fieldCode: "amountInWords", visible: true, editable: true, required: true },
    { nodeCode: "apply", fieldCode: "fundAllocator", visible: true, editable: true, required: true },
    { nodeCode: "apply", fieldCode: "allocatorIdNo", visible: true, editable: true, required: false },
    { nodeCode: "apply", fieldCode: "remark", visible: true, editable: true, required: false },
  ];
}

/** Restrict overrides to only the BusinessForm props we override in tests, avoiding null contamination from MountingOptions. */
interface BusinessFormOverrides {
  modelValue?: Record<string, unknown>;
  fields?: WorkflowFormField[];
  fieldPermissions?: WorkflowFieldPermission[];
  mode?: "edit" | "readonly";
  disabled?: boolean;
}

function mountForm(overrides: BusinessFormOverrides = {}) {
  return mount(BusinessForm, {
    props: {
      modelValue: overrides.modelValue ?? {},
      fields: overrides.fields ?? buildFields(),
      fieldPermissions: overrides.fieldPermissions ?? buildPermissions(),
      mode: overrides.mode ?? ("edit" as const),
      disabled: overrides.disabled ?? false,
    },
  });
}

function findField(wrapper: ReturnType<typeof mountForm>, fieldCode: string) {
  return wrapper.find(`[data-field-code="${fieldCode}"]`);
}

describe("BusinessForm.vue", () => {
  it("renders every confirmed field exactly once by fieldCode", () => {
    const wrapper = mountForm();
    for (const fieldCode of FIELD_CODES) {
      expect(wrapper.findAll(`[data-field-code="${fieldCode}"]`)).toHaveLength(1);
    }
    expect(findField(wrapper, "remark").find("textarea").exists()).toBe(true);
    expect(findField(wrapper, "transferType").find("select").exists()).toBe(true);
    expect(findField(wrapper, "amount").find('input[type="number"]').exists()).toBe(true);
    expect(findField(wrapper, "accountName").find('input[type="text"]').exists()).toBe(true);
  });

  it("renders select options with confirmed labels and values", () => {
    const wrapper = mountForm();
    const options = findField(wrapper, "transferType").findAll("option");
    expect(options.map((option) => option.text())).toEqual(["请选择划款类型", "现金", "转账", "汇票"]);
  });

  it("does not render unconfirmed business fields", () => {
    const wrapper = mount(BusinessForm, {
      props: {
        modelValue: {},
        fields: [
          ...buildFields(),
          { fieldCode: "inventedField", fieldName: "未确认字段", fieldType: "string", controlType: "input", required: false, sortOrder: 99 },
        ],
        fieldPermissions: [
          ...buildPermissions(),
          { nodeCode: "apply", fieldCode: "inventedField", visible: true, editable: true, required: false },
        ],
        mode: "edit",
        disabled: false,
      },
    });
    expect(findField(wrapper, "inventedField").exists()).toBe(false);
  });

  it("emits update:modelValue with a new object containing the edited value", async () => {
    const wrapper = mountForm({ modelValue: { accountName: "老值" } });
    const initial = wrapper.props("modelValue");
    await findField(wrapper, "accountName").find("input").setValue("新户名");
    const emitted = wrapper.emitted("update:modelValue");
    expect(emitted).toBeTruthy();
    expect(emitted!.at(-1)![0]).toEqual({ accountName: "新户名" });
    expect(emitted!.at(-1)![0]).not.toBe(initial);
  });

  it("emits updates for select and textarea controls", async () => {
    const wrapper = mountForm();
    await findField(wrapper, "bankName").find("select").setValue("CCB");
    expect(wrapper.emitted("update:modelValue")!.at(-1)![0]).toMatchObject({ bankName: "CCB" });
    await findField(wrapper, "remark").find("textarea").setValue("加急处理");
    expect(wrapper.emitted("update:modelValue")!.at(-1)![0]).toMatchObject({ remark: "加急处理" });
  });

  it("hides fields whose runtime permission marks them invisible", () => {
    const wrapper = mountForm({
      fieldPermissions: buildPermissions().map((permission) =>
        permission.fieldCode === "allocatorIdNo" ? { ...permission, visible: false } : permission,
      ),
    });
    expect(findField(wrapper, "allocatorIdNo").exists()).toBe(false);
    expect(findField(wrapper, "remark").exists()).toBe(true);
  });

  it("disables fields whose runtime permission or disabled prop blocks editing", async () => {
    const wrapper = mountForm();
    expect((findField(wrapper, "applicationNo").find("input").element as HTMLInputElement).disabled).toBe(true);
    expect((findField(wrapper, "accountName").find("input").element as HTMLInputElement).disabled).toBe(false);

    await wrapper.setProps({ disabled: true });
    expect((findField(wrapper, "accountName").find("input").element as HTMLInputElement).disabled).toBe(true);
  });

  it("disables all inputs in readonly mode", async () => {
    const wrapper = mountForm({ mode: "edit" });
    expect((findField(wrapper, "accountName").find("input").element as HTMLInputElement).disabled).toBe(false);
    await wrapper.setProps({ mode: "readonly" });
    expect((findField(wrapper, "accountName").find("input").element as HTMLInputElement).disabled).toBe(true);
    expect((findField(wrapper, "transferType").find("select").element as HTMLSelectElement).disabled).toBe(true);
  });

  it("exposes validate() failing on missing required fields with messages", async () => {
    const wrapper = mountForm();
    expect(await wrapper.vm.validate()).toBe(false);
    const errors = wrapper.findAll(".field-error").map((node) => node.text());
    expect(errors).toContain("请选择划款类型");
    expect(errors).toContain("请输入开户户名");
    expect(errors).toContain("请输入银行账号");
    expect(errors).toContain("请输入金额");
  });

  it("applies pattern and length validation from confirmed rules", async () => {
    const wrapper = mountForm({ modelValue: { bankAccountNo: "123" } });
    expect(await wrapper.vm.validate()).toBe(false);
    expect(wrapper.findAll(".field-error").map((node) => node.text())).toContain("银行账号格式不正确");
  });

  it("applies minimum and decimal-place validation for amount", async () => {
    const wrapper = mountForm({ modelValue: { amount: "0.001" } });
    expect(await wrapper.vm.validate()).toBe(false);
    const messages = wrapper.findAll(".field-error").map((node) => node.text());
    expect(messages).toContain("金额最多 2 位小数");

    await wrapper.setProps({ modelValue: { amount: "0.00" } });
    expect(await wrapper.vm.validate()).toBe(false);
    expect(wrapper.findAll(".field-error").map((node) => node.text())).toContain("金额不能小于 0.01");
  });

  it("validates successfully when all required fields are filled", async () => {
    const wrapper = mountForm({
      modelValue: {
        transferType: "TRANSFER",
        bankName: "ICBC",
        accountName: "张三",
        bankAccountNo: "1234567890",
        amount: "1000.50",
        amountInWords: "壹仟元伍角",
        fundAllocator: "李四",
      },
    });
    expect(await wrapper.vm.validate()).toBe(true);
    expect(wrapper.findAll(".field-error")).toHaveLength(0);
  });

  it("combines node permission required flag with definition required flag", async () => {
    const wrapper = mountForm({
      fieldPermissions: buildPermissions().map((permission) =>
        permission.fieldCode === "remark" ? { ...permission, required: true } : permission,
      ),
    });
    expect(findField(wrapper, "remark").find(".required-mark").exists()).toBe(true);
    expect(await wrapper.vm.validate()).toBe(false);
    expect(wrapper.findAll(".field-error").map((node) => node.text())).toContain("请输入备注");
  });

  it("parses validationRule JSON strings from the process snapshot", async () => {
    const wrapper = mount(BusinessForm, {
      props: {
        modelValue: { bankAccountNo: "abc" },
        fields: buildFields().map((field) =>
          field.fieldCode === "bankAccountNo"
            ? { ...field, validation: undefined, validationRule: "{\"pattern\":\"^\\\\d{10}$\"}" }
            : field,
        ),
        fieldPermissions: buildPermissions(),
        mode: "edit",
        disabled: false,
      },
    });
    expect(await wrapper.vm.validate()).toBe(false);
    expect(wrapper.findAll(".field-error").map((node) => node.text())).toContain("银行账号格式不正确");
  });

  it("contains no submit controls, attachments, or workflow actions", () => {
    const wrapper = mountForm();
    expect(wrapper.find("button").exists()).toBe(false);
    expect(wrapper.find('input[type="file"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain("提交");
  });
});
