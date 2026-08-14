import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdminProcessDefinitionsView from "./AdminProcessDefinitionsView.vue";

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
    const fieldInputs = wrapper.findAll(".editor-section")[2].findAll("tbody input");
    expect((fieldInputs[0].element as HTMLInputElement).value).toBe("amount");
    expect((fieldInputs[1].element as HTMLInputElement).value).toBe("金额");
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
});
