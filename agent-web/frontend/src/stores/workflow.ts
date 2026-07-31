import { computed, ref } from "vue";
import { defineStore } from "pinia";
import type {
  BusinessRequirement,
  MockUser,
  RequirementRevision,
  WorkflowSnapshot,
} from "@flowmind/agent-contracts";
import { ApiError, apiRequest, streamEvents, type SseMessage } from "../api";

export const useWorkflowStore = defineStore("workflow", () => {
  const users = ref<MockUser[]>([]);
  const currentUser = ref<MockUser>();
  const snapshot = ref<WorkflowSnapshot>();
  const busy = ref(false);
  const error = ref("");
  const streamingText = ref("");
  const connected = ref(false);
  let streamAbort: AbortController | undefined;
  let reconnectTimer: number | undefined;

  const state = computed(() => snapshot.value?.state);
  const allowedActions = computed(() => snapshot.value?.allowedActions || []);

  async function initialize(): Promise<void> {
    users.value = await apiRequest<MockUser[]>("/api/agent/mock-users");
    const storedUser = localStorage.getItem("flowmind.agent.user");
    currentUser.value = users.value.find((user) => user.userId === storedUser) || users.value[0];
    if (currentUser.value) localStorage.setItem("flowmind.agent.user", currentUser.value.userId);
    const sessionId = localStorage.getItem(sessionStorageKey());
    if (sessionId && currentUser.value) {
      try {
        await refresh(sessionId);
        connect();
      } catch {
        localStorage.removeItem(sessionStorageKey());
      }
    }
  }

  async function selectUser(userId: string): Promise<void> {
    disconnect();
    currentUser.value = users.value.find((user) => user.userId === userId);
    snapshot.value = undefined;
    error.value = "";
    if (currentUser.value) {
      localStorage.setItem("flowmind.agent.user", userId);
      const sessionId = localStorage.getItem(sessionStorageKey());
      if (sessionId) {
        try { await refresh(sessionId); connect(); } catch { localStorage.removeItem(sessionStorageKey()); }
      }
    }
  }

  async function createSession(targetRoot?: string): Promise<void> {
    if (!currentUser.value) return;
    await run(async () => {
      const createdSnapshot = await apiRequest<WorkflowSnapshot>("/api/agent/sessions", currentUser.value, {
        method: "POST",
        body: JSON.stringify({ targetRoot: targetRoot?.trim() || undefined }),
      });
      applySnapshot(createdSnapshot);
      localStorage.setItem(sessionStorageKey(), createdSnapshot.sessionId);
      connect();
    });
  }

  async function refresh(sessionId = snapshot.value?.sessionId): Promise<void> {
    if (!currentUser.value || !sessionId) return;
    applySnapshot(await apiRequest<WorkflowSnapshot>(`/api/agent/sessions/${sessionId}`, currentUser.value));
  }

  async function sendMessage(content: string): Promise<void> {
    if (!snapshot.value || !currentUser.value) return;
    await run(async () => {
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/messages`, currentUser.value, {
        method: "POST",
        rowVersion: snapshot.value!.rowVersion,
        body: JSON.stringify({ content }),
      });
    }, false);
  }

  async function saveRequirement(requirement: BusinessRequirement): Promise<void> {
    if (!snapshot.value || !currentUser.value) return;
    await run(async () => {
      const revision = await apiRequest<RequirementRevision>(
        `/api/agent/sessions/${snapshot.value!.sessionId}/requirement`,
        currentUser.value,
        {
          method: "PUT",
          rowVersion: snapshot.value!.rowVersion,
          body: JSON.stringify({ requirement }),
        },
      );
      await refresh();
      if (snapshot.value) snapshot.value.requirement = revision;
    });
  }

  async function confirmRequirement(): Promise<void> {
    if (!snapshot.value?.requirement || !currentUser.value) return;
    await run(async () => {
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/requirement/confirm`, currentUser.value, {
        method: "POST",
        rowVersion: snapshot.value!.rowVersion,
        idempotencyKey: crypto.randomUUID(),
        body: JSON.stringify({ requirementRevision: snapshot.value!.requirement!.revision }),
      });
      await refresh();
    });
  }

  async function reopenRequirement(): Promise<void> {
    if (!snapshot.value || !currentUser.value) return;
    await command(`/api/agent/sessions/${snapshot.value.sessionId}/requirement/reopen`, false);
  }

  async function confirmProcess(): Promise<void> {
    if (!snapshot.value?.processPreview || !snapshot.value.requirement || !currentUser.value) return;
    await run(async () => {
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/process/confirm`, currentUser.value, {
        method: "POST",
        rowVersion: snapshot.value!.rowVersion,
        idempotencyKey: crypto.randomUUID(),
        body: JSON.stringify({
          platformDefinitionId: snapshot.value!.processPreview!.platformDefinitionId,
          requirementRevision: snapshot.value!.requirement!.revision,
        }),
      });
      await refresh();
    });
  }

  async function retryProcess(): Promise<void> {
    if (!snapshot.value) return;
    await command(`/api/agent/sessions/${snapshot.value.sessionId}/process/retry`, true);
  }

  async function command(path: string, idempotent: boolean): Promise<void> {
    if (!snapshot.value || !currentUser.value) return;
    await run(async () => {
      await apiRequest(path, currentUser.value, {
        method: "POST",
        rowVersion: snapshot.value!.rowVersion,
        idempotencyKey: idempotent ? crypto.randomUUID() : undefined,
        body: JSON.stringify({}),
      });
      await refresh();
    });
  }

  function connect(): void {
    disconnect();
    if (!snapshot.value || !currentUser.value) return;
    const controller = new AbortController();
    streamAbort = controller;
    const sessionId = snapshot.value.sessionId;
    void streamEvents(
      `/api/agent/sessions/${sessionId}/events`,
      currentUser.value,
      controller.signal,
      handleEvent,
    ).then(() => {
      if (streamAbort !== controller || controller.signal.aborted) return;
      connected.value = false;
      scheduleReconnect();
    }).catch((cause) => {
      if (streamAbort === controller && !controller.signal.aborted) {
        connected.value = false;
        error.value = cause instanceof Error ? cause.message : String(cause);
        scheduleReconnect();
      }
    });
    connected.value = true;
  }

  function handleEvent(message: SseMessage): void {
    if (message.event === "workflow.snapshot") {
      applySnapshot(message.data as WorkflowSnapshot);
      streamingText.value = "";
    } else if (message.event === "assistant.delta") {
      streamingText.value += (message.data as { delta: string }).delta;
    } else if (["assistant.completed", "requirement.ready", "workflow.state_changed", "process.validation_completed"].includes(message.event)) {
      streamingText.value = "";
      void refresh();
    } else if (message.event === "error") {
      error.value = (message.data as { message?: string }).message || "操作失败";
      void refresh();
    }
  }

  function scheduleReconnect(): void {
    window.clearTimeout(reconnectTimer);
    reconnectTimer = window.setTimeout(() => {
      if (snapshot.value) connect();
    }, 1500);
  }

  function applySnapshot(nextSnapshot: WorkflowSnapshot): void {
    snapshot.value = nextSnapshot;
    error.value = nextSnapshot.lastError?.message || "";
  }

  function disconnect(): void {
    window.clearTimeout(reconnectTimer);
    streamAbort?.abort();
    streamAbort = undefined;
    connected.value = false;
  }

  async function run(operation: () => Promise<void>, refreshOnConflict = true): Promise<void> {
    busy.value = true;
    error.value = "";
    try {
      await operation();
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : String(cause);
      if (refreshOnConflict && cause instanceof ApiError && cause.status === 409) await refresh();
      throw cause;
    } finally {
      busy.value = false;
    }
  }

  function sessionStorageKey(): string {
    return `flowmind.agent.session.${currentUser.value?.userId || "anonymous"}`;
  }

  return {
    users,
    currentUser,
    snapshot,
    state,
    allowedActions,
    busy,
    error,
    streamingText,
    connected,
    initialize,
    selectUser,
    createSession,
    refresh,
    sendMessage,
    saveRequirement,
    confirmRequirement,
    reopenRequirement,
    confirmProcess,
    retryProcess,
    disconnect,
  };
});
