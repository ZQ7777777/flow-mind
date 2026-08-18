import { afterEach, describe, expect, it, vi } from "vitest";
import {
  approveTask,
  remindTask,
  fetchEntryApplicationProcess,
  fetchWorkflowStartContext,
  fetchWorkflowList,
  fetchWorkflowUsers,
  fetchProcessEntryLink,
  fetchProcessEntryLinks,
  deleteAttachment,
  downloadAttachment,
  startWorkflowProcess,
  uploadInstanceAttachment,
  replaceInstanceAttachment,
  uploadTaskAttachment,
} from "./workflow";

describe("workflow api", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("requests workflow lists from the common workflow namespace", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          records: [],
          pageNo: 2,
          pageSize: 10,
          total: 0,
          totalPages: 0,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    await fetchWorkflowList("todo", {
      pageNo: 2,
      pageSize: 10,
      instanceTitle: "contract",
      source: "DELEGATED",
    });

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/api/workflow/tasks/todo");
    expect(url).toContain("pageNo=2");
    expect(url).toContain("pageSize=10");
    expect(url).toContain("instanceTitle=contract");
    expect(url).toContain("source=DELEGATED");
  });

  it("searches workflow users for action target selectors", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([{ userId: "u_operations_01", userName: "运营职工一" }]), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await fetchWorkflowUsers("operation", 20);

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/api/workflow/users");
    expect(url).toContain("keyword=operation");
    expect(url).toContain("limit=20");
  });

  it("loads the startable entry application process metadata", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ processCode: "entry_application", processName: "客户入金" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchEntryApplicationProcess();

    expect(fetchMock.mock.calls[0][0]).toBe("/api/workflow/startable-processes/entry-application");
    expect(result.processName).toBe("客户入金");
  });
  it("loads configured process entry links from the workflow namespace", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([{ definitionId: "definition-1", entryPageUrl: "/generated/demo/apply" }]), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchProcessEntryLinks();
    await fetchProcessEntryLink("definition / 1");

    expect(fetchMock.mock.calls[0][0]).toBe("/api/workflow/process-entry-links");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/workflow/process-entry-links/definition%20%2F%201");
    expect(result[0].entryPageUrl).toBe("/generated/demo/apply");
  });

  it("loads configured process entry links from the workflow namespace", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([{ definitionId: "definition-1", entryPageUrl: "/generated/demo/apply" }]), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchProcessEntryLinks();
    await fetchProcessEntryLink("definition / 1");

    expect(fetchMock.mock.calls[0][0]).toBe("/api/workflow/process-entry-links");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/workflow/process-entry-links/definition%20%2F%201");
    expect(result[0].entryPageUrl).toBe("/generated/demo/apply");
  });


  it("loads a generic workflow start context by process code", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          definitionId: "definition-1",
          definitionVersion: 3,
          processCode: "entry_application",
          processName: "入金申请",
          startable: true,
          formFields: [],
          fieldPermissions: [],
          attachmentTemplates: [],
          defaultVariables: {},
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchWorkflowStartContext("entry_application");

    expect(fetchMock.mock.calls[0][0]).toBe(
      "/api/workflow/processes/entry_application/start-context",
    );
    expect(result.definitionVersion).toBe(3);
    expect(result.processName).toBe("入金申请");
  });

  it("starts a workflow process with variables, attachments and idempotency key", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ instanceId: "instance-1", createdTasks: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const receipt = new File(["receipt"], "receipt.pdf", { type: "application/pdf" });
    const proof = new File(["proof"], "proof.png", { type: "image/png" });
    const result = await startWorkflowProcess("entry_application", {
      definitionId: "definition-1",
      definitionVersion: 3,
      variables: { amount: 1000, bankName: "招商银行" },
      attachments: { receipt: [receipt, proof] },
      idempotencyKey: "idem-start",
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const body = init.body as FormData;
    expect(url).toBe("/api/workflow/processes/entry_application/start-submit");
    expect(init.method).toBe("POST");
    expect((init.headers as Record<string, string>)["Idempotency-Key"]).toBe("idem-start");
    expect(body.has("definitionId")).toBe(false);
    expect(body.has("definitionVersion")).toBe(false);
    expect((body.get("payload") as File).type).toBe("application/json");
    expect((body.get("payload") as File).size).toBeGreaterThan(0);
    expect(body.getAll("receipt")).toEqual([receipt, proof]);
    expect(body.has("attachments[receipt]")).toBe(false);
    expect(result.instanceId).toBe("instance-1");
  });

  it("loads backend start attachments from the compatible attachments field", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          definitionId: "definition-1",
          definitionVersion: 3,
          processCode: "entry_application",
          processName: "入金申请",
          startable: true,
          formFields: [],
          fieldPermissions: [],
          attachments: [{
            attachmentCode: "receipt",
            attachmentName: "回单",
            minCount: 1,
            maxCount: 2,
            allowedExtensions: ["pdf"],
          }],
          defaultVariables: {},
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchWorkflowStartContext("entry_application");

    expect(result.attachments?.[0].attachmentCode).toBe("receipt");
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


  it("sends starter reminders with task version and idempotency key", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ reminderId: "reminder-1" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await remindTask("task-1", {
      expectedTaskVersion: 7,
      comment: "请尽快处理",
      idempotencyKey: "idem-remind",
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(url).toContain("/api/workflow/tasks/task-1/remind");
    expect(headers["Idempotency-Key"]).toBe("idem-remind");
    expect(JSON.parse(init.body as string)).toEqual({
      expectedTaskVersion: 7,
      comment: "请尽快处理",
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
      attachmentCode: "receipt",
      sourceTaskId: "task-1",
      expectedTaskVersion: 4,
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
    expect(body.get("attachmentCode")).toBe("receipt");
    expect(body.get("sourceTaskId")).toBe("task-1");
    expect(body.get("expectedTaskVersion")).toBe("4");
    expect(body.has("storageKey")).toBe(false);
    expect(body.has("uploaderUserId")).toBe(false);
  });

  it("replaces an instance attachment through the current starter task", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ attachmentId: "new-att" }), {
      status: 200, headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);
    const file = new File(["new"], "new.pdf", { type: "application/pdf" });

    await replaceInstanceAttachment("apply-task", "old-att", file, 4, "replace-key");

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/api/workflow/tasks/apply-task/instance-attachments/old-att");
    expect(init.method).toBe("PUT");
    expect((init.headers as Record<string, string>)["Idempotency-Key"]).toBe("replace-key");
    expect((init.body as FormData).get("expectedTaskVersion")).toBe("4");
    expect(((init.body as FormData).get("file") as File).name).toBe("new.pdf");
  });

  it("uploads task attachments with instance id and task version", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ attachmentId: "att-task" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const file = new File(["note"], "note.txt", { type: "text/plain" });
    await uploadTaskAttachment("task-1", {
      file,
      instanceId: "instance-1",
      attachmentCode: "approvalNote",
      expectedTaskVersion: 9,
      idempotencyKey: "idem-task-upload",
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const body = init.body as FormData;
    expect(url).toContain("/api/workflow/tasks/task-1/attachments");
    expect((init.headers as Record<string, string>)["Idempotency-Key"]).toBe("idem-task-upload");
    expect(body.get("instanceId")).toBe("instance-1");
    expect(body.get("attachmentCode")).toBe("approvalNote");
    expect(body.get("expectedTaskVersion")).toBe("9");
    expect(body.has("sourceTaskId")).toBe(false);
  });

  it("downloads attachment content from the common workflow namespace", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("file-content", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const blob = await downloadAttachment("att-1");

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/api/workflow/attachments/att-1/content");
    expect(blob).toBeInstanceOf(Blob);
    expect(blob.size).toBe("file-content".length);
  });

  it("deletes an attachment with an encoded id and idempotency key", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await deleteAttachment("att/with space", "idem-delete");

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/workflow/attachments/att%2Fwith%20space");
    expect(init.method).toBe("DELETE");
    expect((init.headers as Record<string, string>)["Idempotency-Key"]).toBe("idem-delete");
  });
});


