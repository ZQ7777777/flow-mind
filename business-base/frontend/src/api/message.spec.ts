import { afterEach, describe, expect, it, vi } from "vitest";
import {
  fetchMessages,
  fetchUnreadCount,
  markAllMessagesRead,
  markMessageRead,
} from "./message";

describe("message api", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  function mockJson(body: unknown, status = 200) {
    return vi.fn().mockResolvedValue(
      new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
      }),
    );
  }

  it("queries the current user inbox with read status and type filters", async () => {
    const fetchMock = mockJson({
      records: [],
      pageNo: 2,
      pageSize: 10,
      unreadCount: 3,
    });
    vi.stubGlobal("fetch", fetchMock);

    await fetchMessages({
      pageNo: 2,
      pageSize: 10,
      readStatus: "UNREAD",
      messageType: "ALERT",
    });

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/api/messages");
    expect(url).toContain("pageNo=2");
    expect(url).toContain("pageSize=10");
    expect(url).toContain("readStatus=UNREAD");
    expect(url).toContain("messageType=ALERT");
  });

  it("fetches the unread count", async () => {
    const fetchMock = mockJson({ unreadCount: 5 });
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchUnreadCount();

    expect(result.unreadCount).toBe(5);
    expect(fetchMock.mock.calls[0][0]).toContain("/api/messages/unread-count");
  });

  it("marks a single message read via POST", async () => {
    const fetchMock = mockJson({});
    vi.stubGlobal("fetch", fetchMock);

    await markMessageRead("msg-1");

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/api/messages/msg-1/read");
    expect(init.method).toBe("POST");
  });

  it("marks all messages read via POST", async () => {
    const fetchMock = mockJson({});
    vi.stubGlobal("fetch", fetchMock);

    await markAllMessagesRead();

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/api/messages/read-all");
    expect(init.method).toBe("POST");
  });
});
