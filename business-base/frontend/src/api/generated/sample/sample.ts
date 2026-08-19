export interface WorkflowStartOption {
  label: string;
  value: string;
}

export interface WorkflowStartFormField {
  fieldCode: string;
  fieldName: string;
  fieldType: string;
  controlType: string;
  defaultValue?: string | null;
  validationRule?: string | null;
  options: WorkflowStartOption[];
  visible: boolean;
  editable: boolean;
  required: boolean;
  sortOrder: number;
}

export interface WorkflowStartAttachmentRule {
  attachmentCode: string;
  attachmentName: string;
  description?: string | null;
  required: boolean;
  minCount: number;
  maxCount: number;
  maxSizeBytes: number;
  allowedExtensions: string[];
  sortOrder: number;
}

export interface WorkflowStartContext {
  definitionId?: string | null;
  processCode: string;
  processName: string;
  definitionVersion?: number | null;
  currentNodeCode?: string | null;
  startable: boolean;
  disabledReason?: string | null;
  formFields: WorkflowStartFormField[];
  attachments: WorkflowStartAttachmentRule[];
}

export interface WorkflowStartSubmitPayload {
  businessKey?: string;
  variables: Record<string, unknown>;
}

export interface WorkflowStartSubmitResponse {
  instanceId?: string;
  instanceStatus?: string;
  status?: string;
  [key: string]: unknown;
}

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

export async function getStartContext(processCode: string): Promise<WorkflowStartContext> {
  const response = await fetch(`/api/workflow/processes/${encodeURIComponent(processCode)}/start-context`, {
    credentials: "same-origin",
  });
  return readJson<WorkflowStartContext>(response);
}

export async function searchFuturesAccounts(keyword = ""): Promise<FuturesAccount[]> {
  const params = new URLSearchParams();
  if (keyword.trim()) {
    params.set("keyword", keyword.trim());
  }
  const suffix = params.toString() ? `?${params.toString()}` : "";
  const response = await fetch(`/api/reference-data/futures-accounts${suffix}`, {
    credentials: "same-origin",
  });
  return readJson<FuturesAccount[]>(response);
}

export async function getAccountFunds(accountNo: string): Promise<AccountFund> {
  const response = await fetch(
    `/api/reference-data/futures-accounts/${encodeURIComponent(accountNo)}/funds?currency=CNY`,
    { credentials: "same-origin" },
  );
  return readJson<AccountFund>(response);
}

export async function getExchanges(): Promise<Exchange[]> {
  const response = await fetch("/api/reference-data/exchanges", {
    credentials: "same-origin",
  });
  return readJson<Exchange[]>(response);
}

export async function getTradingCodes(accountNo: string, exchangeCode: string): Promise<TradingCode[]> {
  const params = new URLSearchParams({ exchangeCode });
  const response = await fetch(
    `/api/reference-data/futures-accounts/${encodeURIComponent(accountNo)}/trading-codes?${params.toString()}`,
    { credentials: "same-origin" },
  );
  return readJson<TradingCode[]>(response);
}

export async function searchFuturesProducts(
  exchangeCode: string,
  keyword = "",
): Promise<FuturesProduct[]> {
  const params = new URLSearchParams({
    exchangeCode,
    productType: "FUTURES",
  });
  if (keyword.trim()) {
    params.set("keyword", keyword.trim());
  }
  const response = await fetch(`/api/reference-data/futures-products?${params.toString()}`, {
    credentials: "same-origin",
  });
  return readJson<FuturesProduct[]>(response);
}

export async function startSubmit(
  processCode: string,
  payload: WorkflowStartSubmitPayload,
  filesByAttachmentCode: Record<string, File[]>,
  idempotencyKey: string,
): Promise<WorkflowStartSubmitResponse> {
  const formData = new FormData();
  formData.append(
    "payload",
    new Blob([JSON.stringify(payload)], { type: "application/json" }),
  );

  Object.entries(filesByAttachmentCode).forEach(([attachmentCode, files]) => {
    files.forEach((file) => {
      formData.append(attachmentCode, file);
    });
  });

  const response = await fetch(
    `/api/workflow/processes/${encodeURIComponent(processCode)}/start-submit`,
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey,
      },
      body: formData,
      credentials: "same-origin",
    },
  );
  return readJson<WorkflowStartSubmitResponse>(response);
}
