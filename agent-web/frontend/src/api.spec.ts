import { afterEach, describe, expect, it, vi } from "vitest";
import { apiRequest, streamEvents } from "./api";

describe("Agent API transport", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("sends cookies, optimistic-lock and idempotency headers without client identity", async () => {
    let headers: Headers | undefined;
    vi.stubGlobal("fetch", async (_input: RequestInfo | URL, init?: RequestInit) => {
      headers = new Headers(init?.headers);
      return new Response(JSON.stringify({ accepted: true }), {
        status: 202,
        headers: { "Content-Type": "application/json" },
      });
    });

    await apiRequest("/api/agent/sessions/ags_1/process/confirm", {
      method: "POST",
      rowVersion: 7,
      idempotencyKey: "gate-two",
      body: JSON.stringify({ requirementRevision: 2 }),
    });

    expect(headers?.has("X-Agent-User-Id")).toBe(false);
    expect(headers?.has("X-Agent-User-Name")).toBe(false);
    expect(headers?.get("If-Match")).toBe("7");
    expect(headers?.get("Idempotency-Key")).toBe("gate-two");
  });

  it("parses fetch-based SSE without sending client identity headers", async () => {
    let headers: Headers | undefined;
    const encoder = new TextEncoder();
    vi.stubGlobal("fetch", async (_input: RequestInfo | URL, init?: RequestInit) => {
      headers = new Headers(init?.headers);
      return new Response(new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode(
            "event: workflow.snapshot\ndata: {\"sessionId\":\"ags_1\",\"state\":\"COLLECTING\"}\n\n",
          ));
          controller.close();
        },
      }), { status: 200, headers: { "Content-Type": "text/event-stream" } });
    });
    const received: Array<{ event: string; data: unknown }> = [];

    await streamEvents("/api/agent/sessions/ags_1/events", new AbortController().signal, (message) => {
      received.push(message);
    });

    expect(headers?.has("X-Agent-User-Id")).toBe(false);
    expect(received).toEqual([{
      event: "workflow.snapshot",
      data: { sessionId: "ags_1", state: "COLLECTING" },
    }]);
  });
});
