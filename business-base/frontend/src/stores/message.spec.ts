import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useMessageStore } from "./message";
import { useToastStore } from "./toast";

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  url: string;
  withCredentials: boolean;
  onmessage: ((ev: { data: string }) => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;

  constructor(url: string, options?: { withCredentials?: boolean }) {
    this.url = url;
    this.withCredentials = !!options?.withCredentials;
    FakeEventSource.instances.push(this);
  }

  close(): void {
    this.closed = true;
  }

  emit(data: unknown): void {
    this.onmessage?.({ data: JSON.stringify(data) });
  }
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("message store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    FakeEventSource.instances = [];
    vi.stubGlobal("EventSource", FakeEventSource);
    vi.useFakeTimers();
  });

  afterEach(() => {
    useMessageStore().disconnectStream();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("loads messages together with the unread count", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse({
          records: [
            { messageId: "m1", messageType: "ALERT", title: "t", readStatus: "UNREAD" },
          ],
          pageNo: 1,
          pageSize: 20,
          unreadCount: 2,
        }),
      ),
    );

    const store = useMessageStore();
    await store.loadMessages(1);

    expect(store.records).toHaveLength(1);
    expect(store.unreadCount).toBe(2);
    expect(store.hasMore).toBe(false);
  });

  it("marks a message read and decrements the unread count", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          records: [
            { messageId: "m1", messageType: "TASK_REMIND", title: "t", readStatus: "UNREAD" },
            { messageId: "m2", messageType: "ALERT", title: "t2", readStatus: "UNREAD" },
          ],
          pageNo: 1,
          pageSize: 20,
          unreadCount: 2,
        }),
      )
      .mockResolvedValue(jsonResponse({}));
    vi.stubGlobal("fetch", fetchMock);

    const store = useMessageStore();
    await store.loadMessages(1);
    await store.markRead("m1");

    expect(store.records[0].readStatus).toBe("READ");
    expect(store.unreadCount).toBe(1);
    expect(fetchMock.mock.calls[1][0]).toContain("/api/messages/m1/read");
  });

  it("rolls back the optimistic read update when the request fails", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          records: [
            { messageId: "m1", messageType: "ALERT", title: "t", readStatus: "UNREAD" },
          ],
          pageNo: 1,
          pageSize: 20,
          unreadCount: 1,
        }),
      )
      .mockResolvedValue(jsonResponse({ message: "boom" }, 500));
    vi.stubGlobal("fetch", fetchMock);

    const store = useMessageStore();
    await store.loadMessages(1);
    await expect(store.markRead("m1")).rejects.toThrow();

    expect(store.records[0].readStatus).toBe("UNREAD");
    expect(store.unreadCount).toBe(1);
  });

  it("marks all messages read and zeros the unread count", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          records: [
            { messageId: "m1", messageType: "ALERT", title: "t", readStatus: "UNREAD" },
          ],
          pageNo: 1,
          pageSize: 20,
          unreadCount: 3,
        }),
      )
      .mockResolvedValue(jsonResponse({}));
    vi.stubGlobal("fetch", fetchMock);

    const store = useMessageStore();
    await store.loadMessages(1);
    await store.markAllRead();

    expect(store.records.every((record) => record.readStatus === "READ")).toBe(true);
    expect(store.unreadCount).toBe(0);
  });

  it("handles stream messages by incrementing unread, prepending and toasting", () => {
    const store = useMessageStore();
    store.pageNo = 1;
    store.filters = { readStatus: "", messageType: "" };
    store.unreadCount = 0;
    store.records = [];

    store.handleStreamMessage({
      id: "s1",
      messageType: "TASK_TIMEOUT",
      title: "超时",
      content: "c",
      readStatus: "UNREAD",
      payloadJson: '{"taskId":"t1"}',
    });

    expect(store.unreadCount).toBe(1);
    expect(store.records[0].messageId).toBe("s1");
    expect(store.records[0].payload).toEqual({ taskId: "t1" });
    const toasts = useToastStore().toasts;
    expect(toasts).toHaveLength(1);
    expect(toasts[0].tone).toBe("error");
  });

  it("does not prepend stream messages when filtering by read status READ", () => {
    const store = useMessageStore();
    store.pageNo = 1;
    store.filters = { readStatus: "READ", messageType: "" };
    store.records = [];

    store.handleStreamMessage({
      id: "s2",
      messageType: "TASK_REMIND",
      readStatus: "UNREAD",
    });

    expect(store.records).toHaveLength(0);
    expect(store.unreadCount).toBe(1);
  });

  it("connects the SSE stream and dispatches incoming frames", () => {
    const store = useMessageStore();
    store.connectStream();

    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.instances[0].url).toContain("/api/messages/stream");
    expect(FakeEventSource.instances[0].withCredentials).toBe(true);

    FakeEventSource.instances[0].emit({
      id: "s3",
      messageType: "TASK_DUE_SOON",
      title: "即将超时",
      readStatus: "UNREAD",
    });

    expect(store.unreadCount).toBe(1);
    expect(store.records[0].messageId).toBe("s3");

    store.disconnectStream();
    expect(FakeEventSource.instances[0].closed).toBe(true);
  });
});
