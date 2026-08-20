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

describe("warehouse treasury pledge application read-only api", () => {
  afterEach(() => vi.restoreAllMocks());

  it("queries declared reference-data endpoints with encoded parameters", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ exchangeFunds: [] }))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]));

    await searchFuturesAccounts(" 远航 ");
    await getAccountFunds("6/A299");
    await getExchanges();
    await getTradingCodes("6/A299", "CZCE");
    await searchFuturesProducts("CZCE", "SR");

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/reference-data/futures-accounts?keyword=%E8%BF%9C%E8%88%AA",
      "/api/reference-data/futures-accounts/6%2FA299/funds?currency=CNY",
      "/api/reference-data/exchanges",
      "/api/reference-data/futures-accounts/6%2FA299/trading-codes?exchangeCode=CZCE",
      "/api/reference-data/futures-products?exchangeCode=CZCE&productType=FUTURES&keyword=SR",
    ]);
    for (const [, init] of fetchMock.mock.calls) {
      expect(init).toEqual({ credentials: "same-origin" });
      expect(init?.method).toBeUndefined();
    }
  });

  it("surfaces backend text errors", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("资金服务暂不可用", { status: 503 }));
    await expect(getAccountFunds("6/A299")).rejects.toThrow("资金服务暂不可用");
  });
});
