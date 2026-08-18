import { flushPromises, mount } from "@vue/test-utils";
import { ElMessageBox } from "element-plus";
import { nextTick } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdminProcessInstancesView from "./AdminProcessInstancesView.vue";

const listRecord = {
  instanceId: "instance-1",
  definitionId: "definition-1",
  processCode: "entry_application",
  processName: "入金申请",
  version: 1,
  instanceTitle: "入金申请 #1",
  starterUserId: "user-1",
  starterUserName: "用户一",
  instanceStatus: "RUNNING",
  currentNodeCodes: ["manager_approve"],
  startedAt: "2026-08-13T08:00:00Z",
};

function json(value: unknown): Response {
  return new Response(JSON.stringify(value), { status: 200, headers: { "Content-Type": "application/json" } });
}

function mountView() {
  return mount(AdminProcessInstancesView, {
    global: {
      stubs: {
        ProcessGraph: { template: '<div data-test="process-graph-stub">流程图</div>' },
      },
    },
  });
}

describe("AdminProcessInstancesView", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("removes obsolete filters and opens instance detail in a modal while loading", async () => {
    let resolveDetail: ((response: Response) => void) | undefined;
    const requestedUrls: string[] = [];
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      requestedUrls.push(url);
      if (url.includes("/active-tasks")) return Promise.resolve(json([]));
      if (url.includes("/api/admin/process-definitions/definition-1")) {
        return Promise.resolve(json({ ...listRecord, id: "definition-1", nodes: [], edges: [], formFields: [], attachmentTemplates: [] }));
      }
      if (url === "/api/admin/process-instances/instance-1") {
        return new Promise<Response>((resolve) => { resolveDetail = resolve; });
      }
      return Promise.resolve(json({ records: [listRecord], pageNo: 1, pageSize: 10, total: 1, totalPages: 1 }));
    }));

    const wrapper = mountView();
    await flushPromises();

    const filterText = wrapper.get("form.filters").text();
    expect(filterText).not.toContain("业务键");
    expect(filterText).not.toContain("当前节点");
    expect(filterText).not.toContain("开始时间");
    expect(filterText).not.toContain("结束时间");
    const initialQuery = new URL(requestedUrls[0], "http://localhost").searchParams;
    expect(initialQuery.has("businessKey")).toBe(false);
    expect(initialQuery.has("currentNodeCode")).toBe(false);
    expect(initialQuery.has("startedFrom")).toBe(false);
    expect(initialQuery.has("startedTo")).toBe(false);

    void wrapper.findAll("button").find((item) => item.text() === "查看详情")!.trigger("click");
    await nextTick();
    expect(wrapper.find('[data-test="instance-detail-backdrop"]').exists()).toBe(true);
    expect(wrapper.get(".detail-loading").text()).toContain("正在加载实例详情");
    expect(wrapper.find(".detail-card").exists()).toBe(false);

    resolveDetail!(json({ ...listRecord, businessKey: "BUS-1", variables: { amount: 100 } }));
    await flushPromises();
    expect(wrapper.get(".detail-modal").text()).toContain("BUS-1");
    expect(wrapper.find('[data-test="process-graph-stub"]').exists()).toBe(true);

    await wrapper.findAll(".detail-header button").at(-1)!.trigger("click");
    expect(wrapper.find(".detail-modal").exists()).toBe(false);
  });

  it("refreshes detail after termination and closes it after deletion", async () => {
    let detailRequests = 0;
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/active-tasks")) return Promise.resolve(json([]));
      if (url.includes("/api/admin/process-definitions/definition-1")) {
        return Promise.resolve(json({ id: "definition-1", nodes: [], edges: [], formFields: [], attachmentTemplates: [] }));
      }
      if (url === "/api/admin/process-instances/instance-1" && !init?.method) {
        detailRequests += 1;
        return Promise.resolve(json({
          ...listRecord,
          variables: {},
          instanceStatus: detailRequests > 1 ? "TERMINATED" : "RUNNING",
        }));
      }
      if (url.endsWith("/terminate")) return Promise.resolve(json({ ...listRecord, instanceStatus: "TERMINATED" }));
      if (url === "/api/admin/process-instances/instance-1" && init?.method === "DELETE") return Promise.resolve(json(null));
      return Promise.resolve(json({ records: [listRecord], pageNo: 1, pageSize: 10, total: 1, totalPages: 1 }));
    });
    vi.stubGlobal("fetch", fetchMock);
    vi.spyOn(ElMessageBox, "prompt")
      .mockResolvedValueOnce({ value: "终止测试" } as never)
      .mockResolvedValueOnce({ value: "instance-1" } as never);

    const wrapper = mountView();
    await flushPromises();
    await wrapper.findAll("button").find((item) => item.text() === "查看详情")!.trigger("click");
    await flushPromises();

    await wrapper.findAll(".detail-header button").find((item) => item.text() === "终止流程")!.trigger("click");
    await flushPromises();
    expect(detailRequests).toBe(2);
    expect(wrapper.get(".detail-header").text()).toContain("TERMINATED");

    await wrapper.findAll(".detail-header button").find((item) => item.text() === "删除实例")!.trigger("click");
    await flushPromises();
    expect(fetchMock.mock.calls.some(([url, init]) => String(url) === "/api/admin/process-instances/instance-1" && init?.method === "DELETE")).toBe(true);
    expect(wrapper.find(".detail-modal").exists()).toBe(false);
  });
});
