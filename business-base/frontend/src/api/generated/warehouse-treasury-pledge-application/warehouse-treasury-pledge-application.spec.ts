import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getAccountFunds,
  getExchanges,
  getTradingCodes,
  searchFuturesAccounts,
  searchFuturesProducts,
} from "./warehouse-treasury-pledge-application";

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" }, ...init });
}

describe("仓单、国债（解）质押申请 read-only api", () => {
  afterEach(() => vi.restoreAllMocks());

  it("queries declared reference-data endpoints with encoded parameters", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ accountNo: "8800/01", exchangeFunds: [] }))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]));

    await searchFuturesAccounts(" 中粮 ");
    await getAccountFunds("8800/01");
    await getExchanges();
    await getTradingCodes("8800/01", "CFFEX");
    await searchFuturesProducts("CFFEX", "IF");

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/reference-data/futures-accounts?keyword=%E4%B8%AD%E7%B2%AE",
      "/api/reference-data/futures-accounts/8800%2F01/funds?currency=CNY",
      "/api/reference-data/exchanges",
      "/api/reference-data/futures-accounts/8800%2F01/trading-codes?exchangeCode=CFFEX",
      "/api/reference-data/futures-products?exchangeCode=CFFEX&productType=FUTURES&keyword=IF",
    ]);
    for (const [, init] of fetchMock.mock.calls) {
      expect(init).toEqual({ credentials: "same-origin" });
      expect(init?.method).toBeUndefined();
    }
  });

  it("surfaces backend text errors", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("资金服务暂不可用", { status: 503 }));
    await expect(getAccountFunds("88000166")).rejects.toThrow("资金服务暂不可用");
  });
});
