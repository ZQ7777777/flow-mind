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
});
