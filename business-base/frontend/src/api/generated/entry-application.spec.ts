import { afterEach, describe, expect, it, vi } from "vitest";
import {
  EntryApplicationApiError,
  ENTRY_APPLICATION_SUBMIT_URL,
  submitEntryApplication,
} from "./entry-application";
import { AUTHENTICATION_REQUIRED_EVENT } from "../http";

/**
 * jsdom 环境不保证 Blob.text() 存在，统一使用 FileReader 读取表单中的 Blob/File 内容。
 */
function readBlobText(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(blob);
  });
}

describe("submitEntryApplication", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("posts multipart form data with payload, bankReceipt files and Idempotency-Key", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        instanceId: "inst-1",
        instanceStatus: "RUNNING",
        tasks: [{ taskId: "task-apply-1", nodeCode: "apply", taskName: "提交申请" }],
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const receipt = new File(["abc"], "receipt.pdf", { type: "application/pdf" });
    const result = await submitEntryApplication(
      { applicantName: "张三", amount: 1000 },
      { idempotencyKey: "idem-1", bankReceiptFiles: [receipt] }
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(ENTRY_APPLICATION_SUBMIT_URL);
    expect(init.method).toBe("POST");
    expect(init.credentials).toBe("same-origin");
    expect((init.headers as Record<string, string>)["Idempotency-Key"]).toBe("idem-1");
    expect(init.body).toBeInstanceOf(FormData);

    const form = init.body as FormData;
    const payloadPart = form.get("payload") as Blob;
    expect(payloadPart.type).toBe("application/json");
    expect(JSON.parse(await readBlobText(payloadPart))).toEqual({ applicantName: "张三", amount: 1000 });

    const bankReceipts = form.getAll("bankReceipt") as File[];
    expect(bankReceipts).toHaveLength(1);
    expect(bankReceipts[0].name).toBe("receipt.pdf");

    expect(result).toEqual({
      instanceId: "inst-1",
      instanceStatus: "RUNNING",
      tasks: [{ taskId: "task-apply-1", nodeCode: "apply", taskName: "提交申请" }],
    });
  });

  it("throws EntryApplicationApiError with status, code and message on failure", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: async () => ({ code: "VALIDATION_ERROR", message: "缺少 Idempotency-Key 请求头" }),
      })
    );

    let caught: unknown;
    try {
      await submitEntryApplication(
        { applicantName: "张三", amount: 1000 },
        { idempotencyKey: "idem-1", bankReceiptFiles: [] }
      );
    } catch (error) {
      caught = error;
    }

    expect(caught).toBeInstanceOf(EntryApplicationApiError);
    const apiError = caught as EntryApplicationApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.code).toBe("VALIDATION_ERROR");
    expect(apiError.message).toBe("缺少 Idempotency-Key 请求头");
  });

  it("notifies the shell when the submission session has expired", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({ code: "BUSINESS_AUTHENTICATION_REQUIRED", message: "请先登录" }),
      })
    );
    const listener = vi.fn();
    window.addEventListener(AUTHENTICATION_REQUIRED_EVENT, listener);

    await expect(submitEntryApplication(
      { applicantName: "张三", amount: 1000 },
      { idempotencyKey: "idem-expired", bankReceiptFiles: [] }
    )).rejects.toMatchObject({ status: 401 });

    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener(AUTHENTICATION_REQUIRED_EVENT, listener);
  });

  it("forwards the abort signal to fetch", async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ instanceId: "inst-1", instanceStatus: "RUNNING", tasks: [] }),
    });
    vi.stubGlobal("fetch", fetchMock);

    await submitEntryApplication(
      { applicantName: "张三", amount: 1000 },
      { idempotencyKey: "idem-1", bankReceiptFiles: [], signal: controller.signal }
    );

    expect(fetchMock.mock.calls[0][1].signal).toBe(controller.signal);
  });
});
