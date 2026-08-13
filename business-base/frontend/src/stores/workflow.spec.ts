import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useWorkflowStore } from "./workflow";

function deferredPage(title: string) {
  let resolve!: (value: Response) => void;
  const promise = new Promise<Response>((res) => {
    resolve = res;
  });

  return {
    promise,
    resolve: () =>
      resolve(
        new Response(
          JSON.stringify({
            records: [{ taskId: title, instanceTitle: title }],
            pageNo: 1,
            pageSize: 20,
            total: 1,
            totalPages: 1,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
  };
}

describe("workflow store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.restoreAllMocks();
  });

  it("ignores stale list responses so older requests cannot overwrite current results", async () => {
    const slow = deferredPage("older");
    const fast = deferredPage("newer");
    vi.stubGlobal(
      "fetch",
      vi.fn().mockReturnValueOnce(slow.promise).mockReturnValueOnce(fast.promise),
    );

    const store = useWorkflowStore();
    const firstLoad = store.loadList("todo", { pageNo: 1, pageSize: 20 });
    const secondLoad = store.loadList("todo", { pageNo: 1, pageSize: 20 });

    fast.resolve();
    await secondLoad;
    slow.resolve();
    await firstLoad;

    expect(store.list.records).toEqual([{ taskId: "newer", instanceTitle: "newer" }]);
    expect(store.list.loading).toBe(false);
  });

  it("sends reminder requests with independent submitting state", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ reminderId: "reminder-1" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const store = useWorkflowStore();
    await store.remindTask("task-1", {
      expectedTaskVersion: 3,
      comment: "请尽快处理",
      idempotencyKey: "idem-remind",
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/api/workflow/tasks/task-1/remind");
    expect((init.headers as Record<string, string>)["Idempotency-Key"]).toBe("idem-remind");
    expect(store.reminderSubmitting).toBe(false);
    expect(store.reminderError).toBe("");
  });
});