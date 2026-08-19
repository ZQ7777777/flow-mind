import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getAccountFunds,
  getExchanges,
  getStartContext,
  getTradingCodes,
  searchFuturesAccounts,
  searchFuturesProducts,
  startSubmit,
} from "./sample";

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
    ...init,
  });
}

describe("generated sample api", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("loads sample start context", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse({ processCode: "sample", processName: "仓单、国债（解）质押申请", startable: true }),
    );

    const result = await getStartContext("sample");

    expect(result.processCode).toBe("sample");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/workflow/processes/sample/start-context",
      expect.objectContaining({ credentials: "same-origin" }),
    );
  });

  it("queries reference-data endpoints with expected parameters", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ accountNo: "80000188", exchangeFunds: [] }))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]));

    await searchFuturesAccounts("8000");
    await getAccountFunds("80000188");
    await getExchanges();
    await getTradingCodes("80000188", "CFFEX");
    await searchFuturesProducts("CFFEX", "IF");

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "/api/reference-data/futures-accounts?keyword=8000",
    );
    expect(fetchMock.mock.calls[1]?.[0]).toBe(
      "/api/reference-data/futures-accounts/80000188/funds?currency=CNY",
    );
    expect(fetchMock.mock.calls[2]?.[0]).toBe("/api/reference-data/exchanges");
    expect(fetchMock.mock.calls[3]?.[0]).toBe(
      "/api/reference-data/futures-accounts/80000188/trading-codes?exchangeCode=CFFEX",
    );
    expect(fetchMock.mock.calls[4]?.[0]).toBe(
      "/api/reference-data/futures-products?exchangeCode=CFFEX&productType=FUTURES&keyword=IF",
    );
  });

  it("submits payload and files using multipart form data", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse({ instanceId: "pi-1", status: "RUNNING" }),
    );
    const receipt = new File(["receipt"], "receipt.pdf", { type: "application/pdf" });

    await startSubmit(
      "sample",
      { variables: { businessType: "仓单质押", amount: 1000 } },
      { 银行回单: [receipt] },
      "idem-1",
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe("/api/workflow/processes/sample/start-submit");
    expect(init).toEqual(
      expect.objectContaining({
        method: "POST",
        headers: { "Idempotency-Key": "idem-1" },
        credentials: "same-origin",
      }),
    );

    const body = init?.body as FormData;
    expect(body).toBeInstanceOf(FormData);
    expect(body.get("payload")).toBeInstanceOf(Blob);
    expect(body.getAll("银行回单")).toEqual([receipt]);
  });

  it("surfaces backend text error", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("附件数量不符合要求", { status: 400 }),
    );

    await expect(getStartContext("sample")).rejects.toThrow("附件数量不符合要求");
  });
});
