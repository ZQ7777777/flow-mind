export interface FuturesAccount {
  accountNo: string;
  customerName: string;
  accountStatus: "NORMAL" | "DORMANT" | string;
}

export interface Exchange {
  exchangeCode: string;
  exchangeName: string;
  sourcePrefix: string;
}

export interface ExchangeFund {
  exchangeCode: string;
  exchangeName: string;
  pledgeAmount: number;
  positionMargin: number;
}

export interface AccountFund {
  accountNo: string;
  customerName: string;
  currency: string;
  currentEquity: number;
  availableFunds: number;
  pledgeAmount: number;
  actualCash: number;
  snapshotAt: string;
  exchangeFunds: ExchangeFund[];
}

export interface TradingCode {
  accountNo: string;
  exchangeCode: string;
  tradingCode: string;
  tradingStatus: "NORMAL" | "DORMANT" | string;
}

export interface FuturesProduct {
  exchangeCode: string;
  exchangeName: string;
  productCode: string;
  productName: string;
  productType: string;
  contractMultiplier: number;
  pledgeUnitQuantity: number;
  previousSettlementPrice: number;
  dataSource: string;
}

async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `请求失败（${response.status}）`);
  }
  return response.json() as Promise<T>;
}

export async function searchFuturesAccounts(keyword = ""): Promise<FuturesAccount[]> {
  const params = new URLSearchParams();
  if (keyword.trim()) params.set("keyword", keyword.trim());
  const suffix = params.size ? `?${params.toString()}` : "";
  return readJson(await fetch(`/api/reference-data/futures-accounts${suffix}`, { credentials: "same-origin" }));
}

export async function getAccountFunds(accountNo: string): Promise<AccountFund> {
  return readJson(await fetch(
    `/api/reference-data/futures-accounts/${encodeURIComponent(accountNo)}/funds?currency=CNY`,
    { credentials: "same-origin" },
  ));
}

export async function getExchanges(): Promise<Exchange[]> {
  return readJson(await fetch("/api/reference-data/exchanges", { credentials: "same-origin" }));
}

export async function getTradingCodes(accountNo: string, exchangeCode: string): Promise<TradingCode[]> {
  const params = new URLSearchParams({ exchangeCode });
  return readJson(await fetch(
    `/api/reference-data/futures-accounts/${encodeURIComponent(accountNo)}/trading-codes?${params.toString()}`,
    { credentials: "same-origin" },
  ));
}

export async function searchFuturesProducts(exchangeCode: string, keyword = ""): Promise<FuturesProduct[]> {
  const params = new URLSearchParams({ exchangeCode, productType: "FUTURES" });
  if (keyword.trim()) params.set("keyword", keyword.trim());
  return readJson(await fetch(`/api/reference-data/futures-products?${params.toString()}`, { credentials: "same-origin" }));
}
