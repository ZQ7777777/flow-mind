import { afterEach, describe, expect, it, vi } from "vitest";
import {
  approveTask,
  fetchWorkflowList,
  uploadInstanceAttachment,
} from "./workflow";

describe("workflow api", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("requests workflow lists from the common workflow namespace", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [],
          pageNo: 2,
          pageSize: 10,
          total: 0,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    await fetchWorkflowList("todo", {
      pageNo: 2,
      pageSize: 10,
      keyword: "contract",
      taskSource: "DELEGATED",
    });

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/api/workflow/tasks/todo");
    expect(url).toContain("pageNo=2");
    expect(url).toContain("pageSize=10");
    expect(url).toContain("keyword=contract");
    expect(url).toContain("taskSource=DELEGATED");
  });

  it("sends task actions with the expected task version and idempotency key", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ replayed: false }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await approveTask("task-1", {
      expectedTaskVersion: 7,
      comment: "approved",
      idempotencyKey: "idem-123",
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(url).toContain("/api/workflow/tasks/task-1/approve");
    expect(headers["Idempotency-Key"]).toBe("idem-123");
    expect(JSON.parse(init.body as string)).toEqual({
      expectedTaskVersion: 7,
      comment: "approved",
    });
  });

  it("uploads attachments as multipart form data without caller-controlled storage fields", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ attachmentId: "att-1" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const file = new File(["receipt"], "receipt.txt", { type: "text/plain" });
    await uploadInstanceAttachment("instance-1", {
      file,
      fieldCode: "bankReceipt",
      templateCode: "receipt",
      idempotencyKey: "idem-upload",
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(url).toContain(
      "/api/workflow/instances/instance-1/attachments",
    );
    expect(headers["Idempotency-Key"]).toBe("idem-upload");

    const body = init.body as FormData;
    const uploadedFile = body.get("file") as File;
    expect(uploadedFile.name).toBe("receipt.txt");
    expect(uploadedFile.type).toBe("text/plain");
    expect(body.get("fieldCode")).toBe("bankReceipt");
    expect(body.get("templateCode")).toBe("receipt");
    expect(body.has("storageKey")).toBe(false);
    expect(body.has("uploaderUserId")).toBe(false);
  });
});
