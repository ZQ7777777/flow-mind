import { computed, ref } from "vue";
import { defineStore } from "pinia";
import type {
  AgentPublicConfig,
  AgentAuthenticatedUser,
  BusinessRequirement,
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

export interface ManagedSession {
  sessionId: string;
  businessName: string;
  state: string;
  rowVersion: number;
  lastError?: { code: string; message: string };
  createdAt: string;
  updatedAt: string;
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

export interface GenerationLogEntry {
  id: string;
  at: string;
  toolName?: string;
  target?: string;
  status: "running" | "completed" | "error";
}

export interface VerifyStageEntry {
  stage: string;
  status: string;
  hardGate: boolean;
}

/** Fixed order + hard-gate flags for the six verification stages. The backend
 * streams live status for each via `generation.verify_stage` events. */
const QUALITY_STAGE_ORDER: Array<{ stage: string; hardGate: boolean }> = [
  { stage: "STATIC_VALIDATION", hardGate: true },
  { stage: "BACKEND_COMPILE", hardGate: true },
  { stage: "BACKEND_TESTS", hardGate: false },
  { stage: "FRONTEND_TYPECHECK", hardGate: true },
  { stage: "FRONTEND_TESTS", hardGate: false },
  { stage: "FRONTEND_BUILD", hardGate: true },
];

function initialVerifyStages(): VerifyStageEntry[] {
  return QUALITY_STAGE_ORDER.map(({ stage, hardGate }) => ({ stage, status: "PENDING", hardGate }));
}

export const useWorkflowStore = defineStore("workflow", () => {
  const defaultTargetRoot = ref("");
  const currentUser = ref<AgentAuthenticatedUser>();
  const snapshot = ref<WorkflowSnapshot>();
  const busy = ref(false);
  const error = ref("");
  const streamingText = ref("");
  const qualityReport = ref<GenerationQualityReport>();
  const managedSessions = ref<ManagedSession[]>([]);
  const managedDefinitions = ref<ManagedDefinition[]>([]);
  const managedGenerations = ref<ManagedGeneration[]>([]);
  const connected = ref(false);
  const generatedFile = ref<GeneratedFileContent>();
  const generatedDiff = ref<GeneratedFileDiff>();
  const generatedPreviewFile = ref<GeneratedFileContent>();
  const lastCompaction = ref<CompactionNotice>();
  const generationLog = ref<GenerationLogEntry[]>([]);
  const verifyStages = ref<VerifyStageEntry[]>(initialVerifyStages());
  const qualityLog = ref<GenerationLogEntry[]>([]);
  const qualityStream = ref("");
  const reasoningText = ref("");
  let streamAbort: AbortController | undefined;
  let reconnectTimer: number | undefined;

  const state = computed(() => snapshot.value?.state);
  const allowedActions = computed(() => snapshot.value?.allowedActions || []);

  async function initialize(authenticatedUser: AgentAuthenticatedUser): Promise<void> {
    currentUser.value = authenticatedUser;
    const publicConfig = await apiRequest<AgentPublicConfig>("/api/agent/config");
    defaultTargetRoot.value = publicConfig.defaultTargetRoot;
    const sessionId = localStorage.getItem(sessionStorageKey());
    if (sessionId) {
      try {
        await refresh(sessionId);
        connect();
      } catch {
        localStorage.removeItem(sessionStorageKey());
      }
    }
  }

  async function createSession(targetRoot?: string): Promise<void> {
    if (!currentUser.value) return;
    await run(async () => {
      const createdSnapshot = await apiRequest<WorkflowSnapshot>("/api/agent/sessions", {
        method: "POST",
        body: JSON.stringify({ targetRoot: targetRoot?.trim() || undefined }),
      });
      applySnapshot(createdSnapshot);
      localStorage.setItem(sessionStorageKey(), createdSnapshot.sessionId);
      connect();
    });
  }

  async function createQualityGateFixture(targetRoot?: string): Promise<void> {
    if (!currentUser.value || currentUser.value.userId !== "u_admin_01") return;
    await run(async () => {
      const created = await apiRequest<{ sessionId: string }>("/api/agent/test-fixtures/quality-gate", {
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
    applySnapshot(await apiRequest<WorkflowSnapshot>(`/api/agent/sessions/${sessionId}`));
  }

  async function openSession(sessionId: string): Promise<void> {
    if (!currentUser.value || !sessionId || sessionId === snapshot.value?.sessionId) return;
    await run(async () => {
      const nextSnapshot = await apiRequest<WorkflowSnapshot>(`/api/agent/sessions/${sessionId}`);
      disconnect();
      clearSessionViewState();
      applySnapshot(nextSnapshot);
      localStorage.setItem(sessionStorageKey(), nextSnapshot.sessionId);
      connect();
    }, false);
  }

  async function sendMessage(content: string): Promise<void> {
    if (!snapshot.value || !currentUser.value) return;
    await run(async () => {
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/messages`, {
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
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/requirement/confirm`, {
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
    disconnect();
    try {
      await run(async () => {
        const resetSnapshot = await apiRequest<WorkflowSnapshot>(
          `/api/agent/sessions/${snapshot.value!.sessionId}/reset`,
          { method: "POST", rowVersion: snapshot.value!.rowVersion, body: JSON.stringify({}) },
        );
        streamingText.value = "";
        applySnapshot(resetSnapshot);
      });
    } finally {
      connect();
    }
  }

  async function confirmProcess(): Promise<void> {
    if (!snapshot.value?.processPreview || !snapshot.value.requirement || !currentUser.value) return;
    await run(async () => {
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/process/confirm`, {
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
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/code-generations`, {
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
        apiRequest<GeneratedFileContent>(`${base}/files/${path}`),
        apiRequest<GeneratedFileDiff>(`${base}/diff/${path}`),
      ]);
      generatedFile.value = file;
      generatedDiff.value = diff;
    }, false);
  }

  async function loadGeneratedPreviewFile(relativePath: string): Promise<GeneratedFileContent | undefined> {
    const generation = snapshot.value?.activeGeneration;
    const sessionId = snapshot.value?.sessionId;
    const user = currentUser.value;
    if (!sessionId || !user || !generation) return;
    generatedPreviewFile.value = undefined;
    const file = await apiRequest<GeneratedFileContent>(
      `/api/agent/sessions/${sessionId}/code-generations/${generation.generationId}/files/${encodePath(relativePath)}`,
    );
    const currentGeneration = snapshot.value?.activeGeneration;
    if (
      currentGeneration?.generationId === file.generationId &&
      currentGeneration.generationRevision === file.generationRevision
    ) {
      generatedPreviewFile.value = file;
    }
    return file;
  }

  async function saveGeneratedFile(relativePath: string, content: string): Promise<ArtifactManifest | undefined> {
    const generation = snapshot.value?.activeGeneration;
    if (!snapshot.value || !currentUser.value || !generation) return;
    return runWithResult(async () => {
      const manifest = await apiRequest<ArtifactManifest>(
        `/api/agent/sessions/${snapshot.value!.sessionId}/code-generations/${generation.generationId}/files/${encodePath(relativePath)}`,
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
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/code-generations/${generation.generationId}/regenerate`, {
        method: "POST",
        rowVersion: snapshot.value!.rowVersion,
        idempotencyKey: crypto.randomUUID(),
        body: JSON.stringify({ generationRevision: generation.generationRevision }),
      });
      generatedFile.value = undefined;
      generatedDiff.value = undefined;
      generatedPreviewFile.value = undefined;
      await refresh();
    });
  }

  async function loadGenerationQuality(): Promise<void> {
    const generation = snapshot.value?.activeGeneration;
    if (!snapshot.value || !currentUser.value || !generation) return;
    qualityReport.value = await apiRequest<GenerationQualityReport>(
      `/api/agent/sessions/${snapshot.value.sessionId}/code-generations/${generation.generationId}/quality`,
    );
  }

  async function reverifyGeneration(skipAiReview = Boolean(qualityReport.value?.aiReviewSkipped)): Promise<void> {
    const generation = snapshot.value?.activeGeneration;
    if (!snapshot.value || !currentUser.value || !generation) return;
    await run(async () => {
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/code-generations/${generation.generationId}/reverify`, {
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
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/code-generations/${generation.generationId}/quality/start`, {
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
      await apiRequest(`/api/agent/sessions/${snapshot.value!.sessionId}/code-generations/${generation.generationId}/confirm-write`, {
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
    const [sessions, definitions, generations] = await Promise.all([
      apiRequest<ManagedSession[]>("/api/agent/management/sessions"),
      apiRequest<ManagedDefinition[]>("/api/agent/management/process-definitions"),
      apiRequest<ManagedGeneration[]>("/api/agent/management/code-generations"),
    ]);
    managedSessions.value = sessions;
    managedDefinitions.value = definitions;
    managedGenerations.value = generations;
  }

  async function command(path: string, idempotent: boolean): Promise<void> {
    if (!snapshot.value || !currentUser.value) return;
    await run(async () => {
      await apiRequest(path, {
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
      // Real output begins -> stop showing the transient reasoning buffer.
      reasoningText.value = "";
      const data = message.data as { delta: string; purpose?: string };
      if (data.purpose === "REVIEWER" || data.purpose === "REPAIR") qualityStream.value += data.delta;
      else streamingText.value += data.delta;
    } else if (message.event === "context.compacted") {
      lastCompaction.value = { ...(message.data as Omit<CompactionNotice, "createdAt">), createdAt: new Date().toISOString() };
    } else if (message.event === "generation.stage_changed") {
      streamingText.value = "";
      reasoningText.value = "";
      const stageState = (message.data as { state?: string }).state;
      // A fresh CODE_GENERATING stage marks the start of a new generation run.
      if (stageState === "CODE_GENERATING") generationLog.value = [];
      // A fresh CODE_VERIFYING stage marks the start of a new quality run.
      if (stageState === "CODE_VERIFYING") {
        verifyStages.value = initialVerifyStages();
        qualityLog.value = [];
        qualityStream.value = "";
      }
      void refresh();
    } else if (message.event === "tool.started") {
      const purpose = (message.data as { purpose?: string }).purpose;
      if (purpose === "GENERATOR" || purpose === "REVIEWER" || purpose === "REPAIR") {
        reasoningText.value = "";
        const data = message.data as { toolCallId: string; toolName: string; target?: string };
        const entry: GenerationLogEntry = { id: data.toolCallId, at: new Date().toISOString(), toolName: data.toolName, target: data.target, status: "running" };
        if (purpose === "GENERATOR") generationLog.value = [...generationLog.value, entry];
        else qualityLog.value = [...qualityLog.value, entry];
      }
    } else if (message.event === "tool.completed") {
      const purpose = (message.data as { purpose?: string }).purpose;
      if (purpose === "GENERATOR" || purpose === "REVIEWER" || purpose === "REPAIR") {
        const data = message.data as { toolCallId: string; isError?: boolean };
        const nextStatus: GenerationLogEntry["status"] = data.isError ? "error" : "completed";
        const advance = (entries: GenerationLogEntry[]) => entries.map((entry) => entry.id === data.toolCallId ? { ...entry, status: nextStatus } : entry);
        if (purpose === "GENERATOR") generationLog.value = advance(generationLog.value);
        else qualityLog.value = advance(qualityLog.value);
      }
    } else if (message.event === "generation.verify_stage") {
      const data = message.data as { stage: string; status: string; hardGate?: boolean };
      verifyStages.value = verifyStages.value.map((entry) => entry.stage === data.stage
        ? { ...entry, status: data.status, hardGate: data.hardGate ?? entry.hardGate }
        : entry);
    } else if (message.event === "reasoning.delta") {
      // Transient "model is thinking" buffer; cleared once real text/tools follow.
      reasoningText.value += (message.data as { delta: string }).delta;
    } else if (message.event === "reasoning.completed") {
      reasoningText.value = "";
    } else if (["assistant.completed", "requirement.ready", "workflow.state_changed", "process.validation_completed", "generation.file_changed", "generation.quality_completed"].includes(message.event)) {
      streamingText.value = "";
      qualityStream.value = "";
      reasoningText.value = "";
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
    if (generatedPreviewFile.value?.generationId !== nextSnapshot.activeGeneration?.generationId) {
      generatedPreviewFile.value = undefined;
    }
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

  function clearForAuthentication(): void {
    disconnect();
    currentUser.value = undefined;
    snapshot.value = undefined;
    error.value = "";
    streamingText.value = "";
    reasoningText.value = "";
    generatedFile.value = undefined;
    generatedDiff.value = undefined;
    qualityReport.value = undefined;
    managedSessions.value = [];
    managedDefinitions.value = [];
    managedGenerations.value = [];
    lastCompaction.value = undefined;
    generationLog.value = [];
    qualityLog.value = [];
    qualityStream.value = "";
    verifyStages.value = initialVerifyStages();
  }

  function clearSessionViewState(): void {
    streamingText.value = "";
    reasoningText.value = "";
    generatedFile.value = undefined;
    generatedDiff.value = undefined;
    generatedPreviewFile.value = undefined;
    qualityReport.value = undefined;
    lastCompaction.value = undefined;
    generationLog.value = [];
    qualityLog.value = [];
    qualityStream.value = "";
    verifyStages.value = initialVerifyStages();
  }

  async function run(operation: () => Promise<void>, refreshOnConflict = true): Promise<void> {
    busy.value = true;
    error.value = "";
    try {
      await operation();
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : String(cause);
      if (refreshOnConflict && cause instanceof ApiError && cause.status === 409) await refresh();
      error.value = message;
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
    generatedPreviewFile,
    qualityReport,
    managedSessions,
    managedDefinitions,
    managedGenerations,
    lastCompaction,
    generationLog,
    verifyStages,
    qualityLog,
    qualityStream,
    reasoningText,
    initialize,
    createSession,
    createQualityGateFixture,
    refresh,
    openSession,
    sendMessage,
    saveRequirement,
    confirmRequirement,
    reopenRequirement,
    resetSession,
    confirmProcess,
    retryProcess,
    startGeneration,
    loadGeneratedFile,
    loadGeneratedPreviewFile,
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
    clearForAuthentication,
  };
});

function encodePath(path: string): string {
  return path.split("/").map((segment) => encodeURIComponent(segment)).join("/");
}
