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
});
