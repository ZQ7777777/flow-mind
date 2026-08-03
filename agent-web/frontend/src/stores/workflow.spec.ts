import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { MockUser, WorkflowSnapshot } from "@flowmind/agent-contracts";

const mocks = vi.hoisted(() => ({
  apiRequest: vi.fn(),
  streamEvents: vi.fn(),
}));

vi.mock("../api", async (importOriginal) => {
  const original = await importOriginal<typeof import("../api")>();
  return { ...original, apiRequest: mocks.apiRequest, streamEvents: mocks.streamEvents };
});

import { useWorkflowStore } from "./workflow";

const user: MockUser = { userId: "user_sales", userName: "Sales User" };
const snapshot: WorkflowSnapshot = {
  sessionId: "ags_1",
  ownerUserId: user.userId,
  state: "COLLECTING",
  rowVersion: 0,
  messages: [],
  allowedActions: ["SEND_MESSAGE"],
};

describe("workflow SSE lifecycle", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    setActivePinia(createPinia());
    localStorage.clear();
    mocks.apiRequest.mockReset();
    mocks.streamEvents.mockReset();
    mocks.apiRequest.mockImplementation(async (path: string) => {
      if (path === "/api/agent/mock-users") return [user];
      if (path === "/api/agent/sessions") return snapshot;
      return snapshot;
    });
  });

  it("reconnects a failed stream and cancels stale reconnects on disconnect", async () => {
    mocks.streamEvents
      .mockRejectedValueOnce(new Error("connection lost"))
      .mockImplementation(() => new Promise<void>(() => undefined));
    const store = useWorkflowStore();
    await store.initialize();
    await store.createSession();
    await Promise.resolve();

    expect(mocks.streamEvents).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1500);
    expect(mocks.streamEvents).toHaveBeenCalledTimes(2);

    store.disconnect();
    await vi.advanceTimersByTimeAsync(3000);
    expect(mocks.streamEvents).toHaveBeenCalledTimes(2);
    vi.useRealTimers();
  });

  it("resets the current session and reconnects using the cleared snapshot", async () => {
    mocks.streamEvents.mockImplementation(() => new Promise<void>(() => undefined));
    mocks.apiRequest.mockImplementation(async (path: string) => {
      if (path === "/api/agent/mock-users") return [user];
      if (path === "/api/agent/sessions") return snapshot;
      if (path === "/api/agent/sessions/ags_1/reset") {
        return { ...snapshot, rowVersion: 1, messages: [], requirement: undefined, processPreview: undefined };
      }
      return snapshot;
    });
    const store = useWorkflowStore();
    await store.initialize();
    await store.createSession();
    await store.resetSession();

    expect(mocks.apiRequest).toHaveBeenCalledWith(
      "/api/agent/sessions/ags_1/reset",
      user,
      expect.objectContaining({ method: "POST", rowVersion: 0 }),
    );
    expect(store.snapshot?.rowVersion).toBe(1);
    expect(store.snapshot?.messages).toEqual([]);
  });
});
