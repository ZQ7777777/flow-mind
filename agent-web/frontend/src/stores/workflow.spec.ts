import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { AgentAuthenticatedUser, WorkflowSnapshot } from "@flowmind/agent-contracts";
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

const user: AgentAuthenticatedUser = {
  userId: "u_admin_01", username: "admin01", realName: "系统管理员一",
  departmentId: "dept_company", departmentName: "总公司",
  userType: "ADMIN", administrator: true,
};
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
      if (path === "/api/agent/sessions") return snapshot;
      return snapshot;
    });
  });

  it("loads the configured default target root for the UI", async () => {
    const store = useWorkflowStore();
    await store.initialize(user);

    expect(store.defaultTargetRoot).toBe("E:\\workspace\\business-base");
  });

  it("loads owner-isolated session history with the management lists", async () => {
    const store = useWorkflowStore();
    await store.initialize(user);
    mocks.apiRequest.mockImplementation(async (path: string) => {
      if (path === "/api/agent/management/sessions") {
        return [{
          sessionId: "ags_history",
          businessName: "历史需求",
          state: "CODE_PIPELINE_FAILED",
          rowVersion: 7,
          createdAt: "2026-08-10T00:00:00.000Z",
          updatedAt: "2026-08-11T00:00:00.000Z",
        }];
      }
      return [];
    });

    await store.loadManagementLists();

    expect(store.managedSessions).toHaveLength(1);
    expect(store.managedSessions[0]).toMatchObject({ sessionId: "ags_history", state: "CODE_PIPELINE_FAILED" });
  });

  it("opens a historical session and only then replaces the persisted current session", async () => {
    const historical = { ...snapshot, sessionId: "ags_history", state: "CODE_PIPELINE_FAILED" as const };
    mocks.streamEvents.mockImplementation(() => new Promise<void>(() => undefined));
    mocks.apiRequest.mockImplementation(async (path: string) => {
      if (path === "/api/agent/config") return { defaultTargetRoot: "E:\\workspace\\business-base" };
      if (path === "/api/agent/sessions/ags_history") return historical;
      return snapshot;
    });
    const store = useWorkflowStore();
    await store.initialize(user);
    localStorage.setItem("flowmind.agent.session.u_admin_01", "ags_current");

    await store.openSession("ags_history");

    expect(store.snapshot?.sessionId).toBe("ags_history");
    expect(localStorage.getItem("flowmind.agent.session.u_admin_01")).toBe("ags_history");
    expect(mocks.streamEvents).toHaveBeenCalledWith(
      "/api/agent/sessions/ags_history/events",
      expect.any(AbortSignal),
      expect.any(Function),
    );
  });

  it("preserves the current session when opening history fails", async () => {
    mocks.streamEvents.mockImplementation(() => new Promise<void>(() => undefined));
    mocks.apiRequest.mockImplementation(async (path: string) => {
      if (path === "/api/agent/config") return { defaultTargetRoot: "E:\\workspace\\business-base" };
      if (path === "/api/agent/sessions") return snapshot;
      if (path === "/api/agent/sessions/ags_missing") throw new ApiError(404, "AGENT_SESSION_NOT_FOUND", "session not found");
      return snapshot;
    });
    const store = useWorkflowStore();
    await store.initialize(user);
    await store.createSession();

    await expect(store.openSession("ags_missing")).rejects.toThrow("session not found");

    expect(store.snapshot?.sessionId).toBe("ags_1");
    expect(localStorage.getItem("flowmind.agent.session.u_admin_01")).toBe("ags_1");
  });

  it("clears workflow state when the authenticated session is lost", async () => {
    mocks.streamEvents.mockImplementation(() => new Promise<void>(() => undefined));
    const store = useWorkflowStore();
    await store.initialize(user);
    await store.createSession();

    store.clearForAuthentication();

    expect(store.currentUser).toBeUndefined();
    expect(store.snapshot).toBeUndefined();
    expect(store.connected).toBe(false);
  });

  it("reconnects a failed stream and cancels stale reconnects on disconnect", async () => {
    mocks.streamEvents
      .mockRejectedValueOnce(new Error("connection lost"))
      .mockImplementation(() => new Promise<void>(() => undefined));
    const store = useWorkflowStore();
    await store.initialize(user);
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
      if (path === "/api/agent/sessions") return snapshot;
      if (path === "/api/agent/sessions/ags_1/reset") {
        return { ...snapshot, rowVersion: 1, messages: [], requirement: undefined, processPreview: undefined };
      }
      return snapshot;
    });
    const store = useWorkflowStore();
    await store.initialize(user);
    await store.createSession();
    await store.resetSession();

    expect(mocks.apiRequest).toHaveBeenCalledWith(
      "/api/agent/sessions/ags_1/reset",
      expect.objectContaining({ method: "POST", rowVersion: 0 }),
    );
    expect(store.snapshot?.rowVersion).toBe(1);
    expect(store.snapshot?.messages).toEqual([]);
  });

  it("reconnects the current session when reset fails", async () => {
    mocks.streamEvents.mockImplementation(() => new Promise<void>(() => undefined));
    mocks.apiRequest.mockImplementation(async (path: string) => {
      if (path === "/api/agent/config") return { defaultTargetRoot: "E:\\workspace\\business-base" };
      if (path === "/api/agent/sessions") return snapshot;
      if (path === "/api/agent/sessions/ags_1/reset") {
        throw new ApiError(409, "AGENT_STATE_CONFLICT", "current state does not allow this operation");
      }
      if (path === "/api/agent/sessions/ags_1") return snapshot;
      return snapshot;
    });
    const store = useWorkflowStore();
    await store.initialize(user);
    await store.createSession();

    await expect(store.resetSession()).rejects.toThrow("current state does not allow this operation");

    expect(store.connected).toBe(true);
    expect(mocks.streamEvents).toHaveBeenCalledTimes(2);
    expect(store.snapshot).toMatchObject({ sessionId: "ags_1", state: "COLLECTING" });
  });

  it("records compaction token information without refreshing the workflow snapshot", async () => {
    mocks.streamEvents.mockImplementation((_url, _signal, onMessage) => {
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
    await store.initialize(user);
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
    expect(mocks.apiRequest).toHaveBeenCalledTimes(2);
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
    mocks.apiRequest.mockImplementation(async (path: string, options?: { method?: string; body?: string }) => {
      if (path === "/api/agent/config") return { defaultTargetRoot: "E:\\workspace\\business-base" };
      if (path === "/api/agent/sessions") return active;
      if (path.endsWith("/code-generations")) { expect(JSON.parse(options!.body!)).toEqual({ targetRoot: "E:\\workspace\\business-base" }); current = review; return { accepted: true }; }
      if (path.endsWith("/files/frontend/src/router/generated-routes.ts")) return { generationId: "acg_1", generationRevision: 1, relativePath: "frontend/src/router/generated-routes.ts", content: "route", sha256: "abc" };
      if (path.endsWith("/diff/frontend/src/router/generated-routes.ts")) return { generationId: "acg_1", generationRevision: 1, relativePath: "frontend/src/router/generated-routes.ts", changeType: "MODIFY", stagedSha256: "abc", stale: false, originalContent: "", stagedContent: "route", unifiedDiff: "+route" };
      if (path.endsWith("/files/frontend/src/modules/generated/entry/EntryApply.vue")) return { generationId: "acg_1", generationRevision: 1, relativePath: "frontend/src/modules/generated/entry/EntryApply.vue", content: "<template><form /></template>", sha256: "def" };
      return current;
    });
    const store = useWorkflowStore();
    await store.initialize(user); await store.createSession();
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
      expect.objectContaining({
        method: "POST",
        rowVersion: 7,
        body: JSON.stringify({ generationRevision: 2, skipAiReview: false }),
      }),
    );
  });

  it("refreshes the retryable entry-registration state after confirm-write fails", async () => {
    const ready: WorkflowSnapshot = {
      ...snapshot,
      state: "CODE_REVIEW",
      rowVersion: 4,
      activeGeneration: {
        generationId: "acg_entry",
        status: "REVIEW",
        generationRevision: 2,
        targetRoot: "E:\\workspace\\business-base",
        contractVersion: "1.0",
        manifest: {
          generationId: "acg_entry",
          targetRoot: "E:\\workspace\\business-base",
          contractVersion: "1.0",
          revision: 2,
          files: [{
            relativePath: "frontend/src/router/generated-routes.ts",
            changeType: "MODIFY",
            stagedSha256: "a".repeat(64),
            baseSha256: "b".repeat(64),
            sizeBytes: 10,
            validationStatus: "VALID",
            editedByUser: false,
          }],
        },
        createdAt: "2026-08-03T00:00:00Z",
        updatedAt: "2026-08-03T00:01:00Z",
      },
      allowedActions: ["CONFIRM_WRITE"],
    };
    const failed: WorkflowSnapshot = {
      ...ready,
      state: "BUSINESS_ENTRY_CONFIG_FAILED",
      rowVersion: 6,
      activeGeneration: ready.activeGeneration && {
        ...ready.activeGeneration,
        status: "ENTRY_CONFIG_FAILED",
      },
      lastError: {
        code: "AGENT_BUSINESS_ENTRY_CONFIG_FAILED",
        message: "代码已写入，但业务入口登记失败；请重试入口登记。",
      },
    };
    mocks.apiRequest.mockImplementation(async (path: string) => {
      if (path.endsWith("/confirm-write")) {
        throw new ApiError(502, "AGENT_BUSINESS_ENTRY_CONFIG_FAILED", failed.lastError!.message);
      }
      if (path === "/api/agent/sessions/ags_1") return failed;
      return ready;
    });
    const store = useWorkflowStore();
    store.currentUser = user;
    store.snapshot = ready;

    await expect(store.confirmGenerationWrite()).rejects.toThrow("业务入口登记失败");

    expect(store.state).toBe("BUSINESS_ENTRY_CONFIG_FAILED");
    expect(store.snapshot?.rowVersion).toBe(6);
    expect(store.snapshot?.activeGeneration?.status).toBe("ENTRY_CONFIG_FAILED");
    expect(mocks.apiRequest).toHaveBeenCalledWith(
      "/api/agent/sessions/ags_1/code-generations/acg_entry/confirm-write",
      expect.objectContaining({ method: "POST", rowVersion: 4 }),
    );
  });

  it("stops the currently running quality gate", async () => {
    const verifying: WorkflowSnapshot = {
      ...snapshot,
      state: "CODE_VERIFYING",
      rowVersion: 8,
      activeGeneration: {
        generationId: "acg_verifying",
        status: "VERIFYING",
        generationRevision: 2,
        targetRoot: "E:\\workspace\\business-base",
        contractVersion: "1.0",
        manifest: {
          generationId: "acg_verifying",
          targetRoot: "E:\\workspace\\business-base",
          contractVersion: "1.0",
          revision: 2,
          files: [],
        },
        createdAt: "2026-08-03T00:00:00Z",
        updatedAt: "2026-08-03T00:01:00Z",
      },
      allowedActions: ["STOP_QUALITY"],
    };
    const stopped: WorkflowSnapshot = {
      ...verifying,
      state: "CODE_REVIEW",
      rowVersion: 9,
      allowedActions: ["EDIT_GENERATED_FILE", "REGENERATE", "REVERIFY"],
      activeGeneration: verifying.activeGeneration && { ...verifying.activeGeneration, status: "REVIEW" },
    };
    mocks.apiRequest.mockImplementation(async (path: string) => {
      if (path.endsWith("/quality/stop")) return { cancelled: true, state: "CODE_REVIEW" };
      return stopped;
    });
    const store = useWorkflowStore();
    store.currentUser = user;
    store.snapshot = verifying;
    store.qualityStream = "running";
    store.reasoningText = "thinking";

    await store.stopGenerationQuality();

    expect(mocks.apiRequest).toHaveBeenCalledWith(
      "/api/agent/sessions/ags_1/code-generations/acg_verifying/quality/stop",
      expect.objectContaining({ method: "POST", rowVersion: 8 }),
    );
    expect(store.state).toBe("CODE_REVIEW");
    expect(store.qualityStream).toBe("");
    expect(store.reasoningText).toBe("");
  });
});
