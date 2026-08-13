import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchAlerts, handleAlert } from "./alert";

describe("admin alert api", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("queries alerts under the admin namespace with status filter", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          records: [],
          pageNo: 1,
          pageSize: 20,
          total: 0,
          totalPages: 0,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    await fetchAlerts({ pageNo: 1, pageSize: 20, alertStatus: "OPEN" });

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/api/workflow/admin/alerts");
    expect(url).toContain("alertStatus=OPEN");
  });

  it("handles alerts with an idempotency key and target status body", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ alertId: "alert-1", alertStatus: "HANDLED" }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    await handleAlert(
      "alert-1",
      { targetStatus: "HANDLED", comment: "已确认" },
      "idem-1",
    );

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/api/workflow/admin/alerts/alert-1/handle");
    expect(init.method).toBe("POST");
    expect((init.headers as Record<string, string>)["Idempotency-Key"]).toBe(
      "idem-1",
    );
    expect(JSON.parse(init.body as string)).toEqual({
      targetStatus: "HANDLED",
      comment: "已确认",
    });
  });
});
