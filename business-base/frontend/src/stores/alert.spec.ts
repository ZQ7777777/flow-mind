import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useAlertStore } from "./alert";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("alert store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("loads alerts with paging totals", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse({
          records: [
            { alertId: "a1", alertType: "TASK_TIMEOUT", alertStatus: "OPEN" },
          ],
          pageNo: 1,
          pageSize: 20,
          total: 1,
          totalPages: 1,
        }),
      ),
    );

    const store = useAlertStore();
    await store.loadAlerts(1);

    expect(store.records).toHaveLength(1);
    expect(store.total).toBe(1);
    expect(store.totalPages).toBe(1);
  });

  it("handles an alert and updates the record in place", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          records: [
            { alertId: "a1", alertType: "ACTION_EXCEPTION", alertStatus: "OPEN" },
          ],
          pageNo: 1,
          pageSize: 20,
          total: 1,
          totalPages: 1,
        }),
      )
      .mockResolvedValue(
        jsonResponse({
          alertId: "a1",
          alertType: "ACTION_EXCEPTION",
          alertStatus: "HANDLED",
          handledBy: "admin-1",
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    const store = useAlertStore();
    await store.loadAlerts(1);
    await store.handleAlert("a1", { targetStatus: "HANDLED", comment: "已处理" }, "idem-1");

    expect(store.records[0].alertStatus).toBe("HANDLED");
    expect(store.records[0].handledBy).toBe("admin-1");
    expect(store.handleLoading).toBe(false);

    const [, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect((init.headers as Record<string, string>)["Idempotency-Key"]).toBe("idem-1");
  });
});
