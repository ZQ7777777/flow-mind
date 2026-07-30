import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ProcessPreview from "./ProcessPreview.vue";

describe("ProcessPreview", () => {
  it("renders nodes, fields, attachments and validation issues", () => {
    const wrapper = mount(ProcessPreview, {
      props: {
        preview: {
          platformDefinitionId: "definition-entry",
          processCode: "entry_application",
          processName: "入金申请",
          nodes: [
            { nodeCode: "start", nodeName: "开始", nodeType: "START", positionX: 80, positionY: 120 },
            { nodeCode: "apply", nodeName: "申请", nodeType: "USER_TASK", positionX: 260, positionY: 120 },
          ],
          edges: [{ edgeCode: "e1", sourceNodeCode: "start", targetNodeCode: "apply" }],
          formFields: [{ fieldCode: "amount", fieldName: "入金金额", fieldType: "number", controlType: "number", required: true }],
          attachmentTemplates: [{ attachmentCode: "bankReceipt", attachmentName: "银行回单", required: true }],
          validation: { valid: false, issues: [{ code: "TEST_ISSUE", message: "测试校验问题", nodeCode: "apply" }] },
        },
      },
      global: {
        stubs: {
          ElAlert: { template: "<div><slot />{{ title }}</div>", props: ["title"] },
          ElTag: { template: "<span><slot /></span>" },
          ElTable: { template: "<div><slot /></div>" },
          ElTableColumn: { template: "<div><slot :row=\"{}\" /></div>" },
        },
      },
    });
    expect(wrapper.text()).toContain("definition-entry");
    expect(wrapper.text()).toContain("开始");
    expect(wrapper.text()).toContain("申请");
    expect(wrapper.text()).toContain("测试校验问题");
  });
});
