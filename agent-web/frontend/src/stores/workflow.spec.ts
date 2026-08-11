import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { MockUser, WorkflowSnapshot } from "@flowmind/agent-contracts";
import { ApiError } from "../api";

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
      if (path === "/api/agent/config") return { defaultTargetRoot: "E:\\workspace\\business-base" };
      if (path === "/api/agent/mock-users") return [user];
      if (path === "/api/agent/sessions") return snapshot;
      return snapshot;
    });
  });

  it("loads the configured default target root for the UI", async () => {
    const store = useWorkflowStore();
    await store.initialize();

    expect(store.defaultTargetRoot).toBe("E:\\workspace\\business-base");
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
      if (path === "/api/agent/config") return { defaultTargetRoot: "E:\\workspace\\business-base" };
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

  it("reconnects the current session when reset fails", async () => {
    mocks.streamEvents.mockImplementation(() => new Promise<void>(() => undefined));
    mocks.apiRequest.mockImplementation(async (path: string) => {
      if (path === "/api/agent/config") return { defaultTargetRoot: "E:\\workspace\\business-base" };
      if (path === "/api/agent/mock-users") return [user];
      if (path === "/api/agent/sessions") return snapshot;
      if (path === "/api/agent/sessions/ags_1/reset") {
        throw new ApiError(409, "AGENT_STATE_CONFLICT", "current state does not allow this operation");
      }
      if (path === "/api/agent/sessions/ags_1") return snapshot;
      return snapshot;
    });
    const store = useWorkflowStore();
    await store.initialize();
    await store.createSession();

    await expect(store.resetSession()).rejects.toThrow("current state does not allow this operation");

    expect(store.connected).toBe(true);
    expect(mocks.streamEvents).toHaveBeenCalledTimes(2);
    expect(store.snapshot).toMatchObject({ sessionId: "ags_1", state: "COLLECTING" });
  });

  it("records compaction token information without refreshing the workflow snapshot", async () => {
    mocks.streamEvents.mockImplementation((_url, _user, _signal, onMessage) => {
      onMessage({
        event: "context.compacted",
        data: {
          reason: "threshold",
          tokensBefore: 50000,
          summaryTokens: 1200,
          summary: "## Current task and workflow state\nstate",
          keptRecentTokens: 8000,
          tokensAfterEstimate: 9200,
          tokensReducedEstimate: 40800,
        },
      });
      return new Promise<void>(() => undefined);
    });
    const store = useWorkflowStore();
    await store.initialize();
    await store.createSession();

    expect(store.lastCompaction).toMatchObject({
      reason: "threshold",
      tokensBefore: 50000,
      summaryTokens: 1200,
      summary: "## Current task and workflow state\nstate",
      keptRecentTokens: 8000,
      tokensAfterEstimate: 9200,
      tokensReducedEstimate: 40800,
    });
    expect(store.streamingText).toBe("");
    expect(mocks.apiRequest).toHaveBeenCalledTimes(3);
  });

  it("starts M3 with a late-bound target and loads generated content plus diff", async () => {
    const active: WorkflowSnapshot = { ...snapshot, state: "PROCESS_ACTIVE", rowVersion: 4, allowedActions: ["START_GENERATION"] };
    const review: WorkflowSnapshot = {
      ...active,
      state: "CODE_REVIEW",
      rowVersion: 6,
      activeGeneration: {
        generationId: "acg_1", status: "REVIEW", generationRevision: 1,
        targetRoot: "E:\\workspace\\business-base", contractVersion: "1.0",
        manifest: { generationId: "acg_1", targetRoot: "E:\\workspace\\business-base", contractVersion: "1.0", revision: 1, files: [] },
        createdAt: "2026-08-03T00:00:00Z", updatedAt: "2026-08-03T00:00:00Z",
      },
      allowedActions: ["EDIT_GENERATED_FILE", "REGENERATE"],
    };
    let current = active;
    mocks.streamEvents.mockImplementation(() => new Promise<void>(() => undefined));
    mocks.apiRequest.mockImplementation(async (path: string, _user?: MockUser, options?: { method?: string; body?: string }) => {
      if (path === "/api/agent/config") return { defaultTargetRoot: "E:\\workspace\\business-base" };
      if (path === "/api/agent/mock-users") return [user];
      if (path === "/api/agent/sessions") return active;
      if (path.endsWith("/code-generations")) { expect(JSON.parse(options!.body!)).toEqual({ targetRoot: "E:\\workspace\\business-base" }); current = review; return { accepted: true }; }
      if (path.endsWith("/files/frontend/src/router/generated-routes.ts")) return { generationId: "acg_1", generationRevision: 1, relativePath: "frontend/src/router/generated-routes.ts", content: "route", sha256: "abc" };
      if (path.endsWith("/diff/frontend/src/router/generated-routes.ts")) return { generationId: "acg_1", generationRevision: 1, relativePath: "frontend/src/router/generated-routes.ts", changeType: "MODIFY", stagedSha256: "abc", stale: false, originalContent: "", stagedContent: "route", unifiedDiff: "+route" };
      if (path.endsWith("/files/frontend/src/modules/generated/entry/EntryApply.vue")) return { generationId: "acg_1", generationRevision: 1, relativePath: "frontend/src/modules/generated/entry/EntryApply.vue", content: "<template><form /></template>", sha256: "def" };
      return current;
    });
    const store = useWorkflowStore();
    await store.initialize(); await store.createSession();
    await store.startGeneration("E:\\workspace\\business-base");
    expect(store.state).toBe("CODE_REVIEW");
    await store.loadGeneratedFile("frontend/src/router/generated-routes.ts");
    expect(store.generatedFile?.content).toBe("route");
    expect(store.generatedDiff?.unifiedDiff).toBe("+route");
    await store.loadGeneratedPreviewFile("frontend/src/modules/generated/entry/EntryApply.vue");
    expect(store.generatedPreviewFile?.relativePath).toBe("frontend/src/modules/generated/entry/EntryApply.vue");
    expect(mocks.apiRequest).not.toHaveBeenCalledWith(
      expect.stringContaining("/diff/frontend/src/modules/generated/entry/EntryApply.vue"),
      expect.anything(),
    );
  });

  it("reverifies the current generation revision after an edited failure", async () => {
    const failed: WorkflowSnapshot = {
      ...snapshot,
      state: "CODE_PIPELINE_FAILED",
      rowVersion: 7,
      activeGeneration: {
        generationId: "acg_failed",
        status: "FAILED",
        generationRevision: 2,
        targetRoot: "E:\\workspace\\business-base",
        contractVersion: "1.0",
        manifest: {
          generationId: "acg_failed",
          targetRoot: "E:\\workspace\\business-base",
          contractVersion: "1.0",
          revision: 2,
          files: [],
        },
        createdAt: "2026-08-03T00:00:00Z",
        updatedAt: "2026-08-03T00:01:00Z",
      },
      allowedActions: ["EDIT_GENERATED_FILE", "REGENERATE", "REVERIFY"],
    };
    mocks.apiRequest.mockImplementation(async (path: string) => {
      if (path.endsWith("/reverify")) return { accepted: true, state: "CODE_VERIFYING" };
      return failed;
    });
    const store = useWorkflowStore();
    store.currentUser = user;
    store.snapshot = failed;

    await store.reverifyGeneration();

    expect(mocks.apiRequest).toHaveBeenCalledWith(
      "/api/agent/sessions/ags_1/code-generations/acg_failed/reverify",
      user,
      expect.objectContaining({
        method: "POST",
        rowVersion: 7,
        body: JSON.stringify({ generationRevision: 2, skipAiReview: false }),
      }),
    );
  });
});
