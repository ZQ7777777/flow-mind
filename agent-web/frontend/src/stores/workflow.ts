import { computed, ref } from "vue";
import { defineStore } from "pinia";
import type {
  AgentPublicConfig,
  BusinessRequirement,
  MockUser,
  RequirementRevision,
  WorkflowSnapshot,
  GeneratedFileContent,
  GeneratedFileDiff,
  ArtifactManifest,
  GenerationQualityReport,
} from "@flowmind/agent-contracts";
import { ApiError, apiRequest, streamEvents, type SseMessage } from "../api";

export interface CompactionNotice {
  reason?: string;
  tokensBefore?: number;
  summaryTokens?: number;
  summary?: string;
  keptRecentTokens?: number;
  tokensAfterEstimate?: number;
  tokensReducedEstimate?: number;
  createdAt: string;
}


export interface ManagedDefinition {
  id: string;
  sessionId: string;
  businessCode: string;
  businessName: string;
  status: string;
  activationStatus?: string;
  definitionVersion?: number;
  activatedAt?: string;
  createdAt: string;
}

export interface ManagedGeneration {
  generationId: string;
  sessionId: string;
  businessCode: string;
  businessName: string;
  status: string;
  generationRevision: number;
  hardGatePassed: number;
  overrideRequired: number;
  canWrite: number;
  writtenAt?: string;
  createdAt: string;
}
export const useWorkflowStore = defineStore("workflow", () => {
  const users = ref<MockUser[]>([]);
  const defaultTargetRoot = ref("");
  const currentUser = ref<MockUser>();
  const snapshot = ref<WorkflowSnapshot>();
  const busy = ref(false);
  const error = ref("");
  const streamingText = ref("");
  const qualityReport = ref<GenerationQualityReport>();
  const managedDefinitions = ref<ManagedDefinition[]>([]);
  const managedGenerations = ref<ManagedGeneration[]>([]);
  const connected = ref(false);
  const generatedFile = ref<GeneratedFileContent>();
  const generatedDiff = ref<GeneratedFileDiff>();
  const lastCompaction = ref<CompactionNotice>();
  let streamAbort: AbortController | undefined;
  let reconnectTimer: number | undefined;

  const state = computed(() => snapshot.value?.state);
  const allowedActions = computed(() => snapshot.value?.allowedActions || []);

  async function initialize(): Promise<void> {
    const [publicConfig, availableUsers] = await Promise.all([
      apiRequest<AgentPublicConfig>("/api/agent/config"),
      apiRequest<MockUser[]>("/api/agent/mock-users"),
    ]);
    defaultTargetRoot.value = publicConfig.defaultTargetRoot;
    users.value = availableUsers;
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

  async function createQualityGateFixture(targetRoot?: string): Promise<void> {
    if (!currentUser.value || currentUser.value.userId !== "user_tester") return;
    await run(async () => {
      const created = await apiRequest<{ sessionId: string }>("/api/agent/test-fixtures/quality-gate", currentUser.value!, {
        method: "POST",
        body: JSON.stringify({ targetRoot: targetRoot?.trim() || undefined }),
      });
      localStorage.setItem(sessionStorageKey(), created.sessionId);
      await refresh(created.sessionId);
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

  async function resetSession(): Promise<void> {
    if (!snapshot.value || !currentUser.value) return;
    await run(async () => {
      disconnect();
      const resetSnapshot = await apiRequest<WorkflowSnapshot>(
        `/api/agent/sessions/${snapshot.value!.sessionId}/reset`,
        currentUser.value!,
        { method: "POST", rowVersion: snapshot.value!.rowVersion, body: JSON.stringify({}) },
      );
      streamingText.value = "";
      applySnapshot(resetSnapshot);
      connect();
    });
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

  async function startGeneration(targetRoot?: string): Promise<void> {
    if (!snapshot.value || !currentUser.value) return;
    await run(async () => {
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/code-generations`, currentUser.value, {
        method: "POST",
        rowVersion: snapshot.value!.rowVersion,
        idempotencyKey: crypto.randomUUID(),
        body: JSON.stringify({ targetRoot: targetRoot?.trim() || undefined }),
      });
      await refresh();
    });
  }

  async function loadGeneratedFile(relativePath: string): Promise<void> {
    const generation = snapshot.value?.activeGeneration;
    if (!snapshot.value || !currentUser.value || !generation) return;
    const path = encodePath(relativePath);
    const base = `/api/agent/sessions/${snapshot.value.sessionId}/code-generations/${generation.generationId}`;
    await run(async () => {
      const [file, diff] = await Promise.all([
        apiRequest<GeneratedFileContent>(`${base}/files/${path}`, currentUser.value!),
        apiRequest<GeneratedFileDiff>(`${base}/diff/${path}`, currentUser.value!),
      ]);
      generatedFile.value = file;
      generatedDiff.value = diff;
    }, false);
  }

  async function saveGeneratedFile(relativePath: string, content: string): Promise<ArtifactManifest | undefined> {
    const generation = snapshot.value?.activeGeneration;
    if (!snapshot.value || !currentUser.value || !generation) return;
    return runWithResult(async () => {
      const manifest = await apiRequest<ArtifactManifest>(
        `/api/agent/sessions/${snapshot.value!.sessionId}/code-generations/${generation.generationId}/files/${encodePath(relativePath)}`,
        currentUser.value!,
        { method: "PUT", body: JSON.stringify({ content, generationRevision: generation.generationRevision }) },
      );
      await refresh();
      await loadGeneratedFile(relativePath);
      return manifest;
    });
  }

  async function cancelGeneration(): Promise<void> {
    const generation = snapshot.value?.activeGeneration;
    if (!snapshot.value || !currentUser.value || !generation) return;
    await command(`/api/agent/sessions/${snapshot.value.sessionId}/code-generations/${generation.generationId}/cancel`, false);
  }

  async function regenerate(): Promise<void> {
    const generation = snapshot.value?.activeGeneration;
    if (!snapshot.value || !currentUser.value || !generation) return;
    await run(async () => {
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/code-generations/${generation.generationId}/regenerate`, currentUser.value!, {
        method: "POST",
        rowVersion: snapshot.value!.rowVersion,
        idempotencyKey: crypto.randomUUID(),
        body: JSON.stringify({ generationRevision: generation.generationRevision }),
      });
      generatedFile.value = undefined;
      generatedDiff.value = undefined;
      await refresh();
    });
  }

  async function loadGenerationQuality(): Promise<void> {
    const generation = snapshot.value?.activeGeneration;
    if (!snapshot.value || !currentUser.value || !generation) return;
    qualityReport.value = await apiRequest<GenerationQualityReport>(
      `/api/agent/sessions/${snapshot.value.sessionId}/code-generations/${generation.generationId}/quality`,
      currentUser.value,
    );
  }

  async function reverifyGeneration(skipAiReview = Boolean(qualityReport.value?.aiReviewSkipped)): Promise<void> {
    const generation = snapshot.value?.activeGeneration;
    if (!snapshot.value || !currentUser.value || !generation) return;
    await run(async () => {
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/code-generations/${generation.generationId}/reverify`, currentUser.value!, {
        method: "POST",
        rowVersion: snapshot.value!.rowVersion,
        idempotencyKey: crypto.randomUUID(),
        body: JSON.stringify({ generationRevision: generation.generationRevision, skipAiReview }),
      });
      qualityReport.value = undefined;
      await refresh();
    });
  }

  async function startGenerationQuality(skipAiReview: boolean): Promise<void> {
    const generation = snapshot.value?.activeGeneration;
    if (!snapshot.value || !currentUser.value || !generation) return;
    await run(async () => {
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/code-generations/${generation.generationId}/quality/start`, currentUser.value!, {
        method: "POST",
        rowVersion: snapshot.value!.rowVersion,
        idempotencyKey: crypto.randomUUID(),
        body: JSON.stringify({ generationRevision: generation.generationRevision, skipAiReview }),
      });
      qualityReport.value = undefined;
      await refresh();
    });
  }

  async function overrideGenerationQuality(
    scopes: Array<"BACKEND_TESTS" | "FRONTEND_TESTS" | "REVIEWER">,
    reason: string,
  ): Promise<void> {
    const generation = snapshot.value?.activeGeneration;
    if (!snapshot.value || !currentUser.value || !generation) return;
    await run(async () => {
      qualityReport.value = await apiRequest<GenerationQualityReport>(
        `/api/agent/sessions/${snapshot.value!.sessionId}/code-generations/${generation.generationId}/quality-override`,
        currentUser.value!,
        {
          method: "POST",
          rowVersion: snapshot.value!.rowVersion,
          idempotencyKey: crypto.randomUUID(),
          body: JSON.stringify({ generationRevision: generation.generationRevision, scopes, reason }),
        },
      );
      await refresh();
    });
  }

  async function confirmGenerationWrite(): Promise<void> {
    const generation = snapshot.value?.activeGeneration;
    if (!snapshot.value || !currentUser.value || !generation?.manifest) return;
    await run(async () => {
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/code-generations/${generation.generationId}/confirm-write`, currentUser.value!, {
        method: "POST",
        rowVersion: snapshot.value!.rowVersion,
        idempotencyKey: crypto.randomUUID(),
        body: JSON.stringify({
          generationRevision: generation.generationRevision,
          files: generation.manifest!.files.map(({ relativePath, stagedSha256 }) => ({ relativePath, stagedSha256 })),
        }),
      });
      await refresh();
    });
  }

  async function loadManagementLists(): Promise<void> {
    if (!currentUser.value) return;
    const [definitions, generations] = await Promise.all([
      apiRequest<ManagedDefinition[]>("/api/agent/management/process-definitions", currentUser.value),
      apiRequest<ManagedGeneration[]>("/api/agent/management/code-generations", currentUser.value),
    ]);
    managedDefinitions.value = definitions;
    managedGenerations.value = generations;
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
    } else if (message.event === "context.compacted") {
      lastCompaction.value = { ...(message.data as Omit<CompactionNotice, "createdAt">), createdAt: new Date().toISOString() };
    } else if (["assistant.completed", "requirement.ready", "workflow.state_changed", "process.validation_completed", "generation.stage_changed", "generation.file_changed", "generation.quality_completed"].includes(message.event)) {
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
    qualityReport.value = nextSnapshot.activeGeneration?.quality;
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

  async function runWithResult<T>(operation: () => Promise<T>): Promise<T> {
    busy.value = true;
    error.value = "";
    try { return await operation(); }
    catch (cause) { error.value = cause instanceof Error ? cause.message : String(cause); throw cause; }
    finally { busy.value = false; }
  }

  function sessionStorageKey(): string {
    return `flowmind.agent.session.${currentUser.value?.userId || "anonymous"}`;
  }

  return {
    users,
    defaultTargetRoot,
    currentUser,
    snapshot,
    state,
    allowedActions,
    busy,
    error,
    streamingText,
    connected,
    generatedFile,
    generatedDiff,
    qualityReport,
    managedDefinitions,
    managedGenerations,
    lastCompaction,
    initialize,
    selectUser,
    createSession,
    createQualityGateFixture,
    refresh,
    sendMessage,
    saveRequirement,
    confirmRequirement,
    reopenRequirement,
    resetSession,
    confirmProcess,
    retryProcess,
    startGeneration,
    loadGeneratedFile,
    saveGeneratedFile,
    cancelGeneration,
    regenerate,
    loadGenerationQuality,
    reverifyGeneration,
    startGenerationQuality,
    overrideGenerationQuality,
    confirmGenerationWrite,
    loadManagementLists,
    disconnect,
  };
});

function encodePath(path: string): string {
  return path.split("/").map((segment) => encodeURIComponent(segment)).join("/");
}
