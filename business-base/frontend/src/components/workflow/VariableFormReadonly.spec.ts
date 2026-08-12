import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import VariableFormReadonly from "./VariableFormReadonly.vue";

describe("VariableFormReadonly", () => {
  it("renders definition-driven variables without evaluating HTML", () => {
    const wrapper = mount(VariableFormReadonly, {
      props: {
        fields: [
          {
            fieldCode: "amount",
            fieldName: "金额",
            fieldType: "number",
            sortOrder: 1,
          },
          {
            fieldCode: "payload",
            fieldName: "扩展",
            fieldType: "unknown",
            sortOrder: 2,
          },
        ],
        variables: {
          amount: 1288.5,
          payload: { html: "<img src=x onerror=alert(1)>" },
        },
      },
    });

    expect(wrapper.text()).toContain("1,288.5");
    expect(wrapper.html()).toContain("&lt;img src=x onerror=alert(1)&gt;");
    expect(wrapper.find("img").exists()).toBe(false);
  });

  it("edits definition-driven controls and validates required fields", async () => {
    const wrapper = mount(VariableFormReadonly, {
      props: {
        editable: true,
        fields: [
          { fieldCode: "amount", fieldName: "金额", fieldType: "number", controlType: "number", required: true },
          {
            fieldCode: "currency", fieldName: "币种", fieldType: "select", controlType: "select", required: true,
            validationRule: '{"options":[{"label":"人民币","value":"CNY"}]}',
          },
        ],
        variables: { amount: 100, currency: "CNY" },
      },
    });

    await wrapper.get('input[type="number"]').setValue(250);
    expect(wrapper.emitted("update:variables")?.at(-1)?.[0]).toMatchObject({ amount: 250, currency: "CNY" });
    await wrapper.get("select").setValue("");
    expect((wrapper.vm as unknown as { validate: () => boolean }).validate()).toBe(false);
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain("币种不能为空");
  });
});
