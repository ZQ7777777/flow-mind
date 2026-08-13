import { flushPromises, mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminAlertView from "./AdminAlertView.vue";

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

function mockPage(records: unknown[]): ReturnType<typeof vi.fn> {
  return vi.fn().mockResolvedValue(
    jsonResponse({
      records,
      pageNo: 1,
      pageSize: 20,
      total: records.length,
      totalPages: 1,
    }),
  );
}

function mountView() {
  return mount(AdminAlertView, {
    global: { plugins: [createPinia()] },
  });
}

describe("AdminAlertView", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders open alerts with handle and ignore actions", async () => {
    vi.stubGlobal(
      "fetch",
      mockPage([
        {
          alertId: "a1",
          alertType: "TASK_TIMEOUT",
          alertStatus: "OPEN",
          severity: "HIGH",
        },
      ]),
    );

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.find('[data-test="alert-row-a1"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="alert-handle-a1"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="alert-ignore-a1"]').exists()).toBe(true);
  });

  it("hides actions for already-handled alerts", async () => {
    vi.stubGlobal(
      "fetch",
      mockPage([
        { alertId: "a1", alertType: "TASK_TIMEOUT", alertStatus: "HANDLED" },
      ]),
    );

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.find('[data-test="alert-handle-a1"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="alert-ignore-a1"]').exists()).toBe(false);
  });

  it("opens the handle modal and submits with an idempotency key", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(
      jsonResponse({
        records: [
          { alertId: "a1", alertType: "ACTION_EXCEPTION", alertStatus: "OPEN" },
        ],
        pageNo: 1,
        pageSize: 20,
        total: 1,
        totalPages: 1,
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountView();
    await flushPromises();

    await wrapper.find('[data-test="alert-handle-a1"]').trigger("click");
    expect(wrapper.find('[data-test="handle-comment"]').exists()).toBe(true);

    await wrapper.find('[data-test="handle-comment"]').setValue("已确认");
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        alertId: "a1",
        alertType: "ACTION_EXCEPTION",
        alertStatus: "HANDLED",
        handledBy: "admin-1",
      }),
    );

    await wrapper.find('[data-test="handle-confirm"]').trigger("click");
    await flushPromises();

    const handleCall = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(handleCall[0]).toContain("/api/workflow/admin/alerts/a1/handle");
    expect(
      (handleCall[1].headers as Record<string, string>)["Idempotency-Key"],
    ).toBeTruthy();
    expect(JSON.parse(handleCall[1].body as string)).toEqual({
      targetStatus: "HANDLED",
      comment: "已确认",
    });

    expect(wrapper.find('[data-test="handle-comment"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="alert-handle-a1"]').exists()).toBe(false);
  });

  it("applies status and type filters via the query button", async () => {
    const fetchMock = mockPage([]);
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountView();
    await flushPromises();

    await wrapper.find('[data-test="alerts-filter-status"]').setValue("OPEN");
    await wrapper.find('[data-test="alerts-filter-type"]').setValue("TASK_TIMEOUT");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    const lastCall = fetchMock.mock.calls[fetchMock.mock.calls.length - 1][0] as string;
    expect(lastCall).toContain("alertStatus=OPEN");
    expect(lastCall).toContain("alertType=TASK_TIMEOUT");
  });
});
