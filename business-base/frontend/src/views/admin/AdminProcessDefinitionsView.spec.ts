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
});
