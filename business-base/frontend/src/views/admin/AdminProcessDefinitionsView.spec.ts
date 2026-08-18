import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdminProcessDefinitionsView from "./AdminProcessDefinitionsView.vue";

async function mountDefinitionWithField(field: Record<string, unknown>) {
  const definition = {
    id: "definition-field-editor",
    processCode: "field_editor",
    processName: "Field editor",
    systemCode: "business-base",
    version: 1,
    definitionStatus: "DRAFT",
    activationStatus: "INACTIVE",
  };
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    let payload: unknown;
    if (url.includes("process-definition-options")) {
      payload = { users: [], departments: [], roles: [] };
    } else if (url.includes("attachment-templates")) {
      payload = [];
    } else if (url.endsWith("/publish-validation")) {
      payload = { valid: true, issues: [] };
    } else if (url.endsWith("/graph") && init?.method === "PUT") {
      payload = definition;
    } else if (url.endsWith(`/${definition.id}`)) {
      payload = {
        ...definition,
        nodes: [
          { nodeCode: "start", nodeName: "Start", nodeType: "START", sortOrder: 1 },
          { nodeCode: "end", nodeName: "End", nodeType: "END", sortOrder: 2 },
        ],
        edges: [{ edgeCode: "e1", sourceNodeCode: "start", targetNodeCode: "end", defaultEdge: false, sortOrder: 1 }],
        formFields: [field],
        attachmentTemplates: [],
      };
    } else {
      payload = { records: [definition], pageNo: 1, pageSize: 10, total: 1, totalPages: 1 };
    }
    return new Response(JSON.stringify(payload), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }));

  const wrapper = mount(AdminProcessDefinitionsView);
  await flushPromises();
  await wrapper.get("tbody .actions button").trigger("click");
  await flushPromises();
  await wrapper.findAll(".tabs button").find((item) => item.text().includes("表单字段"))!.trigger("click");
  return wrapper;
}

describe("AdminProcessDefinitionsView", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      const payload = url.includes("process-definition-options")
        ? { users: [], departments: [], roles: [] }
        : url.includes("attachment-templates")
          ? []
          : { records: [], pageNo: 1, pageSize: 10, total: 0, totalPages: 0 };
      return new Response(JSON.stringify(payload), { status: 200, headers: { "Content-Type": "application/json" } });
    }));
  });

  it("opens a complete entry-application draft for a new definition", async () => {
    const wrapper = mount(AdminProcessDefinitionsView);
    await flushPromises();
    await wrapper.get(".page-header .primary").trigger("click");
    await flushPromises();

    const basicInputs = wrapper.findAll(".form-grid input");
    expect((basicInputs[0].element as HTMLInputElement).value).toMatch(/^entry_application_\d+$/);
    expect((basicInputs[1].element as HTMLInputElement).value).toBe("入金申请测试流程");

    const graphTab = wrapper.findAll(".tabs button").find((item) => item.text().includes("流程图"));
    await graphTab!.trigger("click");
    expect(wrapper.findAll(".designer-node")).toHaveLength(3);
    expect(wrapper.findAll(".edge-line")).toHaveLength(2);

    const fieldsTab = wrapper.findAll(".tabs button").find((item) => item.text().includes("表单字段"));
    await fieldsTab!.trigger("click");
    const fieldInputs = wrapper.get(".form-fields-editor").findAll("tbody input");
    expect((fieldInputs[0].element as HTMLInputElement).value).toBe("amount");
    expect((fieldInputs[1].element as HTMLInputElement).value).toBe("金额");
  });

  it("configures select options without requiring administrators to write JSON", async () => {
    const created = {
      id: "definition-select",
      processCode: "select_application",
      processName: "Select application",
      systemCode: "business-base",
      definitionStatus: "DRAFT",
      activationStatus: "INACTIVE",
    };
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      let payload: unknown;
      if (url.includes("process-definition-options")) {
        payload = { users: [], departments: [], roles: [] };
      } else if (url.includes("attachment-templates")) {
        payload = [];
      } else if (url.endsWith("/publish-validation")) {
        payload = { valid: true, issues: [] };
      } else if (url.endsWith("/graph") && init?.method === "PUT") {
        payload = created;
      } else if (url.endsWith("/process-definitions") && init?.method === "POST") {
        payload = created;
      } else {
        payload = { records: [], pageNo: 1, pageSize: 10, total: 0, totalPages: 0 };
      }
      return new Response(JSON.stringify(payload), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }));

    const wrapper = mount(AdminProcessDefinitionsView);
    await flushPromises();
    await wrapper.get(".page-header .primary").trigger("click");
    await flushPromises();
    await wrapper.findAll(".tabs button").find((item) => item.text().includes("表单字段"))!.trigger("click");
    await wrapper.get('[data-test="add-form-field"]').trigger("click");

    const field = wrapper.get('[data-test="form-field-1"]');
    await field.get('[data-test="field-type"]').setValue("select");
    expect((field.get('[data-test="field-control"]').element as HTMLSelectElement).value).toBe("select");
    expect(wrapper.find('[data-test="advanced-validation-1"]').exists()).toBe(false);

    const options = wrapper.get('[data-test="select-options-1"]');
    await wrapper.get(".modal-footer .primary").trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("第 1 个选项的名称和值均不能为空");
    expect(vi.mocked(fetch).mock.calls.some(([input, init]) => String(input).endsWith("/process-definitions") && init?.method === "POST")).toBe(false);

    await options.get('[data-test="option-label-0"]').setValue("人民币");
    await options.get('[data-test="option-value-0"]').setValue("CNY");
    await options.get('[data-test="add-select-option"]').trigger("click");
    await options.get('[data-test="option-label-1"]').setValue("美元");
    await options.get('[data-test="option-value-1"]').setValue("USD");
    await field.get('[data-test="field-default"]').setValue("CNY");
    await wrapper.get(".modal-footer .primary").trigger("click");
    await flushPromises();

    const graphCall = vi.mocked(fetch).mock.calls.find(([input, init]) =>
      String(input).endsWith("/definition-select/graph") && init?.method === "PUT");
    expect(graphCall).toBeDefined();
    const request = JSON.parse(String(graphCall![1]?.body));
    expect(JSON.parse(request.formFields[1].validationRule)).toEqual({
      options: [
        { label: "人民币", value: "CNY" },
        { label: "美元", value: "USD" },
      ],
    });
    expect(request.formFields[1].defaultValue).toBe("CNY");
  });

  it("hydrates existing options and preserves other validation rules", async () => {
    const wrapper = await mountDefinitionWithField({
      fieldCode: "currency",
      fieldName: "币种",
      fieldType: "select",
      controlType: "select",
      required: true,
      validationRule: JSON.stringify({ pattern: "^[A-Z]+$", options: [{ label: "人民币", value: "CNY" }] }),
      defaultValue: "CNY",
      sortOrder: 1,
    });

    expect((wrapper.get('[data-test="option-label-0"]').element as HTMLInputElement).value).toBe("人民币");
    await wrapper.get('[data-test="option-label-0"]').setValue("人民币（CNY）");
    await wrapper.get('[data-test="add-select-option"]').trigger("click");
    await wrapper.get('[data-test="option-label-1"]').setValue("美元");
    await wrapper.get('[data-test="option-value-1"]').setValue("USD");
    await wrapper.get(".modal-footer .primary").trigger("click");
    await flushPromises();

    const graphCall = vi.mocked(fetch).mock.calls.find(([input, init]) =>
      String(input).endsWith("/definition-field-editor/graph") && init?.method === "PUT");
    const request = JSON.parse(String(graphCall![1]?.body));
    expect(JSON.parse(request.formFields[0].validationRule)).toEqual({
      pattern: "^[A-Z]+$",
      options: [
        { label: "人民币（CNY）", value: "CNY" },
        { label: "美元", value: "USD" },
      ],
    });
  });

  it("normalizes legacy array options after structured editing", async () => {
    const wrapper = await mountDefinitionWithField({
      fieldCode: "legacyChoice",
      fieldName: "历史选项",
      fieldType: "select",
      controlType: "select",
      required: false,
      validationRule: '[{"label":"旧选项","value":"legacy"}]',
      sortOrder: 1,
    });

    await wrapper.get('[data-test="option-label-0"]').setValue("新选项");
    await wrapper.get(".modal-footer .primary").trigger("click");
    await flushPromises();

    const graphCall = vi.mocked(fetch).mock.calls.find(([input, init]) =>
      String(input).endsWith("/definition-field-editor/graph") && init?.method === "PUT");
    const request = JSON.parse(String(graphCall![1]?.body));
    expect(JSON.parse(request.formFields[0].validationRule)).toEqual({
      options: [{ label: "新选项", value: "legacy" }],
    });
  });

  it("keeps malformed JSON intact, expands advanced settings, and blocks saving", async () => {
    const wrapper = await mountDefinitionWithField({
      fieldCode: "brokenChoice",
      fieldName: "异常选项",
      fieldType: "select",
      controlType: "select",
      required: false,
      validationRule: "{broken",
      sortOrder: 1,
    });

    const advanced = wrapper.get('[data-test="advanced-validation-0"]');
    expect((advanced.element as HTMLTextAreaElement).value).toBe("{broken");
    expect(wrapper.get('[data-test="add-select-option"]').attributes("disabled")).toBeDefined();
    await wrapper.get(".modal-footer .primary").trigger("click");
    await flushPromises();

    expect(vi.mocked(fetch).mock.calls.some(([input, init]) => String(input).endsWith("/graph") && init?.method === "PUT")).toBe(false);
    expect(wrapper.text()).toContain("校验规则不是合法 JSON");
    expect((advanced.element as HTMLTextAreaElement).value).toBe("{broken");
  });

  it("blocks duplicate select values and defaults outside the option list", async () => {
    const duplicateWrapper = await mountDefinitionWithField({
      fieldCode: "duplicateChoice",
      fieldName: "重复选项",
      fieldType: "select",
      controlType: "select",
      required: false,
      validationRule: JSON.stringify({ options: [
        { label: "选项 A", value: "same" },
        { label: "选项 B", value: "same" },
      ] }),
      sortOrder: 1,
    });
    await duplicateWrapper.get(".modal-footer .primary").trigger("click");
    await flushPromises();
    expect(duplicateWrapper.text()).toContain("第 2 个选项的值重复");
    expect(vi.mocked(fetch).mock.calls.some(([input, init]) => String(input).endsWith("/graph") && init?.method === "PUT")).toBe(false);

    duplicateWrapper.unmount();
    const defaultWrapper = await mountDefinitionWithField({
      fieldCode: "defaultChoice",
      fieldName: "默认选项",
      fieldType: "select",
      controlType: "select",
      required: false,
      validationRule: JSON.stringify({ options: [{ label: "选项 A", value: "A" }] }),
      defaultValue: "missing",
      sortOrder: 1,
    });
    await defaultWrapper.get(".modal-footer .primary").trigger("click");
    await flushPromises();
    expect(defaultWrapper.text()).toContain("默认值必须是已配置的选项值");
    expect(vi.mocked(fetch).mock.calls.some(([input, init]) => String(input).endsWith("/graph") && init?.method === "PUT")).toBe(false);
  });

  it.each([
    {
      definitionId: "definition-copy-v2",
      processCode: "entry_application",
      attachmentTemplates: [{
        id: "attachment-row-copy",
        attachmentConfigId: "attachment-group-copy",
        definitionId: "definition-copy-v2",
        attachmentTemplateId: "template-bank-receipt",
        attachmentCode: "bankReceipt",
        required: true,
        minCount: 1,
        maxCount: 5,
        applicableNodeCodes: ["apply"],
        sortOrder: 1,
      }],
      expectedAttachments: [expect.objectContaining({
        configId: "attachment-row-copy",
        attachmentConfigId: "attachment-group-copy",
        definitionId: "definition-copy-v2",
      })],
    },
    {
      definitionId: "definition-copy-no-attachments",
      processCode: "entry_application_1786581976224",
      attachmentTemplates: [],
      expectedAttachments: [],
    },
  ])("preserves attachment identity when saving $processCode", async ({
    definitionId, processCode, attachmentTemplates, expectedAttachments,
  }) => {
    const definition = {
      id: definitionId,
      processCode,
      processName: "Copied definition",
      systemCode: "business-base",
      version: 2,
      definitionStatus: "DRAFT",
      activationStatus: "INACTIVE",
    };
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      let payload: unknown;
      if (url.includes("process-definition-options")) {
        payload = { users: [], departments: [], roles: [] };
      } else if (url.includes("attachment-templates")) {
        payload = [];
      } else if (url.endsWith(`/${definitionId}/publish-validation`)) {
        payload = { valid: true, issues: [] };
      } else if (url.endsWith(`/${definitionId}/graph`) && init?.method === "PUT") {
        payload = definition;
      } else if (url.endsWith(`/${definitionId}`)) {
        payload = {
          ...definition,
          nodes: [
            { nodeCode: "start", nodeName: "Start", nodeType: "START", sortOrder: 1 },
            { nodeCode: "apply", nodeName: "Apply", nodeType: "USER_TASK", approverRuleType: "STARTER", approverRuleConfig: "{}", multiInstanceMode: "SINGLE", sortOrder: 2 },
            { nodeCode: "end", nodeName: "End", nodeType: "END", sortOrder: 3 },
          ],
          edges: [
            { edgeCode: "e1", sourceNodeCode: "start", targetNodeCode: "apply", defaultEdge: false, sortOrder: 1 },
            { edgeCode: "e2", sourceNodeCode: "apply", targetNodeCode: "end", defaultEdge: false, sortOrder: 2 },
          ],
          formFields: [],
          attachmentTemplates,
        };
      } else {
        payload = { records: [definition], pageNo: 1, pageSize: 10, total: 1, totalPages: 1 };
      }
      return new Response(JSON.stringify(payload), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }));

    const wrapper = mount(AdminProcessDefinitionsView);
    await flushPromises();
    await wrapper.get("tbody .actions button").trigger("click");
    await flushPromises();
    await wrapper.get(".modal-footer .primary").trigger("click");
    await flushPromises();

    const graphCall = vi.mocked(fetch).mock.calls.find(([input, init]) =>
      String(input).endsWith(`/${definitionId}/graph`) && init?.method === "PUT");
    expect(graphCall).toBeDefined();
    const request = JSON.parse(String(graphCall![1]?.body));
    expect(request.attachmentConfigs).toEqual(expectedAttachments);
  });

  it("saves business hall entry config without icon or theme fields", async () => {
    const definition = {
      id: "definition-entry-config",
      processCode: "entry_application",
      processName: "入金流程",
      systemCode: "business-base",
      version: 1,
      definitionStatus: "DRAFT",
      activationStatus: "INACTIVE",
    };
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      let payload: unknown;
      if (url.includes("business-entry-configs/by-definition") && init?.method === "PUT") {
        payload = { definitionId: definition.id, ...JSON.parse(String(init.body)) };
      } else if (url.includes("business-entry-configs/by-definition")) {
        payload = {
          definitionId: definition.id,
          entryDisplayName: "旧入口",
          entryPageUrl: "/old-entry",
          entrySource: "MANUAL",
          enabled: false,
        };
      } else if (url.includes("process-definition-options")) {
        payload = { users: [], departments: [], roles: [] };
      } else if (url.includes("attachment-templates")) {
        payload = [];
      } else if (url.endsWith(`/${definition.id}/publish-validation`)) {
        payload = { valid: true, issues: [] };
      } else if (url.endsWith(`/${definition.id}/graph`) && init?.method === "PUT") {
        payload = definition;
      } else if (url.endsWith(`/${definition.id}`)) {
        payload = { ...definition, nodes: [], edges: [], formFields: [], attachmentTemplates: [] };
      } else {
        payload = { records: [definition], pageNo: 1, pageSize: 10, total: 1, totalPages: 1 };
      }
      return new Response(JSON.stringify(payload), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }));

    const wrapper = mount(AdminProcessDefinitionsView);
    await flushPromises();
    await wrapper.get("tbody .actions button").trigger("click");
    await flushPromises();
    await wrapper.findAll(".tabs button").find((button) => button.text() === "入口配置")?.trigger("click");
    await wrapper.get('input[placeholder="默认使用流程名称"]').setValue("入金申请");
    await wrapper.get('input[placeholder="例如 /generated/entry-application/apply"]').setValue("/generated/entry-application/apply");
    await wrapper.get(".checkbox-field input").setValue(true);
    await wrapper.get(".modal-footer .primary").trigger("click");
    await flushPromises();

    const configCall = vi.mocked(fetch).mock.calls.find(([input, init]) =>
      String(input).includes("/api/admin/business-entry-configs/by-definition/definition-entry-config") && init?.method === "PUT");
    expect(configCall).toBeDefined();
    const request = JSON.parse(String(configCall![1]?.body));
    expect(request).toMatchObject({
      entryDisplayName: "入金申请",
      entryPageUrl: "/generated/entry-application/apply",
      entrySource: "MANUAL",
      enabled: true,
    });
    expect(request).not.toHaveProperty("icon");
    expect(request).not.toHaveProperty("themeColor");
  });
});



