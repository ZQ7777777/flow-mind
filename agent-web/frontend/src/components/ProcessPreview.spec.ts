import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ProcessPreview from "./ProcessPreview.vue";

describe("ProcessPreview", () => {
  it("renders nodes, fields, attachments and validation issues", async () => {
    const wrapper = mount(ProcessPreview, {
      props: {
        preview: {
          platformDefinitionId: "definition-entry",
          processCode: "entry_application",
          processName: "入金申请",
          nodes: [
            { nodeCode: "start", nodeName: "开始", nodeType: "START", positionX: 80, positionY: 120 },
            {
              nodeCode: "apply",
              nodeName: "申请",
              nodeType: "USER_TASK",
              listenerConfig: JSON.stringify({
                taskActionRules: {
                  reject: { enabled: true, targetNodeCodes: ["apply"] },
                  directSend: { enabled: true, targetMode: "REJECT_SOURCE" },
                },
              }),
              timeoutConfig: JSON.stringify({ enabled: true, durationMinutes: 1440, action: "REMIND" }),
              reminderConfig: JSON.stringify({ enabled: true, maxCount: 2 }),
              positionX: 260,
              positionY: 120,
            },
          ],
          edges: [{ edgeCode: "e1", sourceNodeCode: "start", targetNodeCode: "apply", conditionExpression: "amount > 0" }],
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
    await wrapper.get('[data-testid="graph-node-apply"]').trigger("click");
    expect(wrapper.text()).toContain("超时策略");
    expect(wrapper.text()).toContain("直送策略");
    expect(wrapper.text()).not.toContain("timeoutConfig");
  });
});
