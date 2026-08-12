/**
 * 入金申请 API 层。
 * <p>
 * HTTP 请求全部收敛在本模块：multipart/form-data 提交、Idempotency-Key
 * 幂等头、成功解析与统一错误契约。组件与 Store 不得直接发起提交请求。
 */

import { notifyAuthenticationRequired } from "../http";

export interface EntryApplicationSubmitPayload {
  applicantName: string;
  amount: number;
}

export interface EntryApplicationTaskSummary {
  taskId: string;
  nodeCode: string;
  taskName: string;
}

export interface EntryApplicationSubmitResult {
  instanceId: string;
  instanceStatus: string;
  tasks: EntryApplicationTaskSummary[];
}

export interface EntryApplicationSubmitOptions {
  /** 幂等键：页面加载时生成一次并保持稳定，服务端据此派生稳定的 operationId。 */
  idempotencyKey: string;
  /** 付款凭证文件，按附件编码 bankReceipt 提交（可多个）。 */
  bankReceiptFiles: File[];
  signal?: AbortSignal;
}

export const ENTRY_APPLICATION_SUBMIT_URL = "/api/generated/entry-application/submit";

/** 后端统一错误契约对应的客户端异常。 */
export class EntryApplicationApiError extends Error {
  readonly code: string;
  readonly status: number;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = "EntryApplicationApiError";
    this.status = status;
    this.code = code;
  }
}

/**
 * 提交入金申请。
 *
 * 请求体为 multipart/form-data：名为 payload 的 JSON 数据部分 + 以附件编码
 * bankReceipt 命名的文件部分。身份、流程编码与附件校验均由服务端完成。
 */
export async function submitEntryApplication(
  payload: EntryApplicationSubmitPayload,
  options: EntryApplicationSubmitOptions
): Promise<EntryApplicationSubmitResult> {
  const formData = new FormData();
  const payloadBlob = new Blob([JSON.stringify(payload)], { type: "application/json" });
  formData.append("payload", payloadBlob, "payload.json");
  for (const file of options.bankReceiptFiles) {
    formData.append("bankReceipt", file, file.name);
  }

  const response = await fetch(ENTRY_APPLICATION_SUBMIT_URL, {
    method: "POST",
    credentials: "same-origin",
    headers: { "Idempotency-Key": options.idempotencyKey },
    body: formData,
    signal: options.signal,
  });

  const body: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    notifyAuthenticationRequired(response.status);
    const errorBody = body as { code?: unknown; message?: unknown } | null;
    const code = typeof errorBody?.code === "string" ? errorBody.code : "UNKNOWN_ERROR";
    const message = typeof errorBody?.message === "string" ? errorBody.message : "提交失败";
    throw new EntryApplicationApiError(response.status, code, message);
  }
  return body as EntryApplicationSubmitResult;
}
