import { Inject, Injectable, OnModuleDestroy } from "@nestjs/common";
import { mkdirSync, appendFileSync, existsSync, readFileSync, rmSync } from "node:fs";
import { isAbsolute, join, relative, resolve } from "node:path";
import { randomUUID } from "node:crypto";
import { Type } from "@sinclair/typebox";
import {
  ENTRY_APPLICATION_REQUIREMENT,
  WAREHOUSE_PLEDGE_REQUIREMENT,
  businessRequirementSchema,
  type BusinessRequirement,
  type ConversationMessage,
  type CodeReviewIssue,
  type GenerationTargetContract,
  type QualityDiagnostic,
  type RepairResolution,
} from "@flowmind/agent-contracts";
import type { GenerationSpec } from "../generation/generation-spec.js";
import { loadConfig } from "../config.js";
import { DatabaseService } from "../persistence/database.service.js";
import { REQUIREMENT_SYSTEM_PROMPT } from "./requirement-prompt.js";
import { createFakeGenerationFiles } from "./fake-generation-files.js";
import { calculateCompactionSettings, createFlowMindCompactionExtension } from "./flowmind-compaction.js";

interface SessionHandle {
  sessionId: string;
  sessionFile?: string;
  prompt(text: string): Promise<void>;
  messages(): ConversationMessage[];
  dispose(): void;
  abort?(): void;
}

export interface PiCallbacks {
  onEvent(type: string, data: unknown): void;
  onError(code: string, message: string): void;
  onRequirement(requirement: BusinessRequirement, missingItems: string[], ambiguities: string[]): Promise<void>;
}

export interface GenerationPiCallbacks {
  requirement: BusinessRequirement;
  contract: GenerationTargetContract;
  spec: GenerationSpec;
  onEvent(type: string, data: unknown): void;
  onError(code: string, message: string): void;
  readReference(path: string): string;
  readStaged(path: string): string;
  listStaged(): string[];
  writeStaged(path: string, content: string): void;
  deleteStaged(path: string): void;
  listGenerationContext(): Array<{ key: string; sha256: string; required: boolean }>;
  readGenerationContext(key: string): string;
  requiredGenerationContextKeys: string[];
  readVerificationDiagnostic?(diagnosticId: string): {
    diagnostic: QualityDiagnostic;
    stdoutExcerpt?: string;
    stderrExcerpt?: string;
  };
  reportComplete(files: string[], resolutions?: RepairResolution[]): void;
}
/** Minimal sink shared by every PI event mapper so generation/repair/review can
 * forward streaming progress through the same code path with a distinct purpose. */
export interface PiEventSink {
  onEvent(type: string, data: unknown): void;
  onError(code: string, message: string): void;
}

export interface ReviewPiCallbacks extends PiEventSink {
  readStaged(path: string): string;
  readDiff(path: string): string;
  readQuality(): string;
  listGenerationContext?(): Array<{ key: string; sha256: string; required: boolean }>;
  readGenerationContext?(key: string): string;
  submit(report: {
    verdict: "APPROVE" | "CHANGES_REQUESTED";
    summary: string;
    issues: CodeReviewIssue[];
  }): void;
}

export function assertRequiredGenerationSkillsRead(
  requiredSkillNames: string[],
  readSkillNames: Set<string>,
): void {
  const missing = requiredSkillNames.filter((name) => !readSkillNames.has(name));
  if (missing.length) {
    throw new Error(`Required generation skills were not read: ${missing.join(", ")}`);
  }
}

export function assertRequiredGenerationContextRead(requiredKeys: string[], readKeys: Set<string>): void {
  const missing = requiredKeys.filter((key) => !readKeys.has(key));
  if (missing.length) throw new Error(`Required generation context was not read: ${missing.join(", ")}`);
}

@Injectable()
export class PiAdapterService implements OnModuleDestroy {
  private readonly config = loadConfig();
  private readonly handles = new Map<string, SessionHandle>();
  private readonly generationHandles = new Map<string, SessionHandle>();
  private modelRuntime: any;

  constructor(@Inject(DatabaseService) private readonly database: DatabaseService) {}

  async ready(): Promise<{ ready: boolean; message?: string }> {
    if (this.config.fakePi) return { ready: true };
    if (!this.config.piModel.includes("/")) return { ready: false, message: "PI_MODEL must use provider/model format" };
    try {
      const runtime = await this.getModelRuntime();
      const available = await runtime.getAvailable();
      const [provider, ...modelParts] = this.config.piModel.split("/");
      const modelId = modelParts.join("/");
      const found = available.some((item: any) => item.provider === provider && item.id === modelId);
      return found ? { ready: true } : { ready: false, message: `model is not authenticated: ${this.config.piModel}` };
    } catch (error) {
      return { ready: false, message: error instanceof Error ? error.message : String(error) };
    }
  }

  async ensureSession(agentSessionId: string, callbacks: PiCallbacks): Promise<{ piSessionId: string; sessionFile?: string }> {
    const existing = this.handles.get(agentSessionId);
    if (existing) return { piSessionId: existing.sessionId, sessionFile: existing.sessionFile };
    const row = this.database.getSession(agentSessionId);
    if (this.config.fakePi) {
      const handle = this.createFakeHandle(agentSessionId, row?.pi_session_file || undefined, callbacks);
      this.handles.set(agentSessionId, handle);
      return { piSessionId: handle.sessionId, sessionFile: handle.sessionFile };
    }
    const handle = await this.createRealHandle(agentSessionId, row?.pi_session_file || undefined, callbacks);
    this.handles.set(agentSessionId, handle);
    return { piSessionId: handle.sessionId, sessionFile: handle.sessionFile };
  }

  async prompt(agentSessionId: string, text: string, callbacks: PiCallbacks): Promise<void> {
    const handle = await this.ensureSession(agentSessionId, callbacks);
    const active = this.handles.get(agentSessionId);
    if (!active || active.sessionId !== handle.piSessionId) throw new Error("Pi session was not initialized");
    await active.prompt(text);
  }

  async getMessages(agentSessionId: string, callbacks: PiCallbacks): Promise<ConversationMessage[]> {
    const handle = await this.ensureSession(agentSessionId, callbacks);
    return this.handles.get(agentSessionId)?.messages() || [];
  }

  /** Dispose the active agent and start a fresh Pi conversation for this workflow session. */
  async resetSession(agentSessionId: string, callbacks: PiCallbacks): Promise<{ piSessionId: string; sessionFile?: string }> {
    const row = this.database.getSession(agentSessionId);
    this.handles.get(agentSessionId)?.dispose();
    this.handles.delete(agentSessionId);
    this.deletePersistedSessionFile(row?.pi_session_file || undefined);

    const handle = this.config.fakePi
      ? this.createFakeHandle(agentSessionId, undefined, callbacks, true)
      : await this.createRealHandle(agentSessionId, undefined, callbacks);
    this.handles.set(agentSessionId, handle);
    return { piSessionId: handle.sessionId, sessionFile: handle.sessionFile };
  }

  async runGeneration(
    generationId: string,
    stagingDir: string,
    prompt: string,
    callbacks: GenerationPiCallbacks,
  ): Promise<{ piSessionId: string; sessionFile?: string }> {
    if (this.generationHandles.has(generationId)) throw new Error("generation Pi session is already active");
    const handle = this.config.fakePi
      ? this.createFakeGenerationHandle(generationId, callbacks)
      : await this.createRealGenerationHandle(generationId, stagingDir, callbacks);
    this.generationHandles.set(generationId, handle);
    this.database.db.prepare("UPDATE agent_code_generation SET pi_session_id = ?, pi_session_file = ?, updated_at = ? WHERE id = ?")
      .run(handle.sessionId, handle.sessionFile || null, new Date().toISOString(), generationId);
    try {
      await handle.prompt(prompt);
      return { piSessionId: handle.sessionId, sessionFile: handle.sessionFile };
    } finally {
      handle.dispose();
      this.generationHandles.delete(generationId);
    }
  }

  cancelGeneration(generationId: string): void {
    this.generationHandles.get(generationId)?.abort?.();
  }

  cancelRepair(generationId: string): void {
    this.generationHandles.get(`${generationId}:repair`)?.abort?.();
  }

  cancelReview(reviewId: string): void {
    this.generationHandles.get(`review:${reviewId}`)?.abort?.();
  }

  async runRepair(
    generationId: string,
    stagingDir: string,
    sessionFile: string,
    prompt: string,
    callbacks: GenerationPiCallbacks,
  ): Promise<{ piSessionId: string; sessionFile?: string }> {
    const key = `${generationId}:repair`;
    if (this.generationHandles.has(key)) throw new Error("generation repair session is already active");
    const handle = this.config.fakePi
      ? this.createFakeRepairHandle(generationId, callbacks)
      : await this.createRealGenerationHandle(generationId, stagingDir, callbacks, sessionFile, true);
    this.generationHandles.set(key, handle);
    try {
      await handle.prompt(prompt);
      return { piSessionId: handle.sessionId, sessionFile: handle.sessionFile };
    } finally {
      handle.dispose();
      this.generationHandles.delete(key);
    }
  }

  async runReview(
    reviewId: string,
    prompt: string,
    callbacks: ReviewPiCallbacks,
  ): Promise<{ piSessionId: string; sessionFile?: string }> {
    const key = `review:${reviewId}`;
    if (this.generationHandles.has(key)) throw new Error("review session is already active");
    const handle = this.config.fakePi
      ? this.createFakeReviewHandle(reviewId, callbacks)
      : await this.createRealReviewHandle(reviewId, callbacks);
    this.generationHandles.set(key, handle);
    try {
      await handle.prompt(prompt);
      return { piSessionId: handle.sessionId, sessionFile: handle.sessionFile };
    } finally {
      handle.dispose();
      this.generationHandles.delete(key);
    }
  }

  private async createRealHandle(agentSessionId: string, sessionFile: string | undefined, callbacks: PiCallbacks): Promise<SessionHandle> {
    const pi: any = await import("@earendil-works/pi-coding-agent");
    const runtime = await this.getModelRuntime();
    const [provider, ...modelParts] = this.config.piModel.split("/");
    const modelId = modelParts.join("/");
    const model = runtime.getModel(provider, modelId);
    if (!model) throw new Error(`PI model not found: ${this.config.piModel}`);

    const sessionDir = join(this.database.dataDir, "pi-sessions");
    const cwd = join(this.database.dataDir, "controlled-cwd", agentSessionId);
    const agentDir = join(this.database.dataDir, "controlled-pi-agent");
    mkdirSync(cwd, { recursive: true });
    mkdirSync(agentDir, { recursive: true });
    const sessionManager = sessionFile
      ? pi.SessionManager.open(sessionFile, sessionDir)
      : pi.SessionManager.create(cwd, sessionDir);
    const compaction = calculateCompactionSettings(Number(model.contextWindow || 128000));
    const settingsManager = pi.SettingsManager.inMemory({ compaction: { enabled: true, ...compaction } });
    let activePiSessionId = agentSessionId;
    const loader = new pi.DefaultResourceLoader({
      cwd,
      agentDir,
      settingsManager,
      systemPromptOverride: () => REQUIREMENT_SYSTEM_PROMPT,
      additionalExtensionPaths: [],
      extensionFactories: [createFlowMindCompactionExtension({
        database: this.database,
        piSessionId: () => activePiSessionId,
        modelName: this.config.compactionModel,
        context: () => this.compactionContext(agentSessionId),
        onEvent: callbacks.onEvent,
      })],
    });
    await loader.reload();
    const submitTool = pi.defineTool({
      name: "submit_requirement_snapshot",
      label: "Submit requirement snapshot",
      description: "Submit the complete structured business requirement for human review.",
      parameters: Type.Object({
        // Keep the tool contract aligned with the AJV schema that persists the
        // requirement.  Type.Any() made the model infer a shape at submission
        // time, which easily diverged from BusinessRequirement 1.0.
        requirement: Type.Unsafe<BusinessRequirement>(businessRequirementSchema),
        missingItems: Type.Array(Type.String()),
        ambiguities: Type.Array(Type.String()),
        readyForReview: Type.Boolean(),
      }),
      execute: async (_callId: string, params: any) => {
        if (!params.readyForReview) throw new Error("Incomplete requirements must be discussed instead of submitted");
        await callbacks.onRequirement(params.requirement, params.missingItems, params.ambiguities);
        return { content: [{ type: "text", text: "结构化需求已提交，等待人工确认。" }], details: {} };
      },
    });
    const created = await pi.createAgentSession({
      cwd,
      agentDir,
      model,
      modelRuntime: runtime,
      thinkingLevel: this.config.thinkingLevel,
      sessionManager,
      settingsManager,
      resourceLoader: loader,
      noTools: "builtin",
      customTools: [submitTool],
    });
    const session = created.session;
    session.subscribe((event: any) => {
      if (event.type === "message_update" && event.assistantMessageEvent?.type === "text_delta") {
        callbacks.onEvent("assistant.delta", { delta: event.assistantMessageEvent.delta });
      } else if (event.type === "message_end") {
        const errorMessage = event.message?.errorMessage;
        if (event.message?.stopReason === "error" || errorMessage) {
          callbacks.onError("AGENT_MODEL_ERROR", errorMessage || "The model did not complete its response");
        } else {
          callbacks.onEvent("assistant.completed", toConversationMessage(event.message));
        }
      } else if (event.type === "agent_start") {
        callbacks.onEvent("agent.started", {});
      } else if (event.type === "agent_end") {
        callbacks.onEvent("agent.completed", {});
      } else if (event.type === "tool_execution_start") {
        callbacks.onEvent("tool.started", { toolName: event.toolName });
      } else if (event.type === "tool_execution_end") {
        callbacks.onEvent("tool.completed", { toolName: event.toolName, isError: event.isError });
      }
    });
    return {
      sessionId: session.sessionId,
      sessionFile: session.sessionFile,
      prompt: (text) => session.prompt(text),
      messages: () => (session.messages || []).map(toConversationMessage).filter(Boolean),
      dispose: () => session.dispose(),
    };
  }

  private createFakeHandle(
    agentSessionId: string,
    existingFile: string | undefined,
    callbacks: PiCallbacks,
    fresh = false,
  ): SessionHandle {
    const sessionFile = existingFile || join(
      this.database.dataDir,
      "pi-sessions",
      fresh ? `${agentSessionId}.${randomUUID()}.fake.jsonl` : `${agentSessionId}.fake.jsonl`,
    );
    const messages: ConversationMessage[] = [];
    if (existsSync(sessionFile)) {
      for (const line of readFileSync(sessionFile, "utf8").split(/\r?\n/).filter(Boolean)) {
        try { messages.push(JSON.parse(line)); } catch { /* ignore incomplete test line */ }
      }
    }
    const persist = (message: ConversationMessage) => {
      messages.push(message);
      appendFileSync(sessionFile, `${JSON.stringify(message)}\n`, "utf8");
    };
    return {
      sessionId: fresh ? `pi_fake_${randomUUID()}` : `pi_fake_${agentSessionId}`,
      sessionFile,
      prompt: async (text: string) => {
        const userMessage: ConversationMessage = { id: `msg_${Date.now()}_u`, role: "user", content: text, createdAt: new Date().toISOString() };
        persist(userMessage);
        callbacks.onEvent("agent.started", {});
        if (messages.filter((message) => message.role === "user").length < 2) {
          const content = "请补充参与角色、表单字段、银行回单材料，以及经理审批和财务确认规则。";
          persist({ id: `msg_${Date.now()}_a`, role: "assistant", content, createdAt: new Date().toISOString() });
          callbacks.onEvent("assistant.delta", { delta: content });
          callbacks.onEvent("assistant.completed", messages[messages.length - 1]);
        } else {
          const isPledgeRequest = messages.some((message) => message.role === "user" && /质押/.test(message.content));
          const requirement = isPledgeRequest ? WAREHOUSE_PLEDGE_REQUIREMENT : ENTRY_APPLICATION_REQUIREMENT;
          await callbacks.onRequirement(requirement, [], []);
          const content = `${requirement.businessName}结构化需求已准备完成，请在右侧预览并确认。`;
          persist({ id: `msg_${Date.now()}_a`, role: "assistant", content, createdAt: new Date().toISOString() });
          callbacks.onEvent("assistant.delta", { delta: content });
          callbacks.onEvent("assistant.completed", messages[messages.length - 1]);
        }
        callbacks.onEvent("agent.completed", {});
      },
      messages: () => [...messages],
      dispose: () => undefined,
    };
  }

  private async createRealGenerationHandle(
    generationId: string,
    stagingDir: string,
    callbacks: GenerationPiCallbacks,
    existingSessionFile?: string,
    repairOnly = false,
  ): Promise<SessionHandle> {
    const pi: any = await import("@earendil-works/pi-coding-agent");
    const runtime = await this.getModelRuntime();
    const [provider, ...modelParts] = this.config.piModel.split("/");
    const model = runtime.getModel(provider, modelParts.join("/"));
    if (!model) throw new Error(`PI model not found: ${this.config.piModel}`);
    const sessionDir = join(this.database.dataDir, "pi-sessions");
    const cwd = join(this.database.dataDir, "controlled-cwd", generationId);
    const agentDir = join(this.database.dataDir, "controlled-pi-agent");
    mkdirSync(cwd, { recursive: true });
    mkdirSync(stagingDir, { recursive: true });
    const compaction = calculateCompactionSettings(Number(model.contextWindow || 128000));
    const settingsManager = pi.SettingsManager.inMemory({ compaction: { enabled: true, ...compaction } });
    let activePiSessionId = generationId;
    const generation = this.database.getGeneration(generationId);
    const loader = new pi.DefaultResourceLoader({
      cwd,
      agentDir,
      settingsManager,
      systemPromptOverride: () => repairOnly ? "You repair only existing Flow Mind Manifest files. Use only registered staging tools." : "You are a constrained Flow Mind code generator. Follow the user prompt and use only registered staging tools.",
      additionalExtensionPaths: [],
      extensionFactories: [createFlowMindCompactionExtension({
        database: this.database,
        piSessionId: () => activePiSessionId,
        modelName: this.config.compactionModel,
        context: () => this.compactionContext(generation?.session_id || generationId, generationId),
        onEvent: callbacks.onEvent,
      })],
    });
    await loader.reload();
    const textResult = (text: string) => ({ content: [{ type: "text", text }], details: {} });
    const readContextKeys = new Set<string>();
    const pathParameters = Type.Object({ path: Type.String({ minLength: 1 }) });
    const baseTools = [
      pi.defineTool({ name: "list_generation_context", label: "List immutable generation context", description: "List the snapshotted project skill and golden references available to this generation.", parameters: Type.Object({}), execute: async () => textResult(JSON.stringify(callbacks.listGenerationContext())) }),
      pi.defineTool({ name: "read_generation_context", label: "Read immutable generation context", description: "Read one snapshotted skill or golden-reference file by key.", parameters: Type.Object({ key: Type.String({ minLength: 1 }) }), execute: async (_id: string, params: any) => { const content = callbacks.readGenerationContext(params.key); readContextKeys.add(params.key); return textResult(content); } }),
      pi.defineTool({ name: "read_generation_contract_file", label: "Read generation contract reference", description: "Read one target reference allowed by generation-target.json.", parameters: pathParameters, execute: async (_id: string, params: any) => textResult(callbacks.readReference(params.path)) }),
      pi.defineTool({ name: "read_staged_file", label: "Read staged file", description: "Read one file in this generation staging area.", parameters: pathParameters, execute: async (_id: string, params: any) => textResult(callbacks.readStaged(params.path)) }),
      pi.defineTool({ name: "list_staged_files", label: "List staged files", description: "List files staged by this generation.", parameters: Type.Object({}), execute: async () => textResult(JSON.stringify(callbacks.listStaged())) }),
      pi.defineTool({ name: "write_staged_file", label: "Write staged file", description: "Write UTF-8 content to an allowed staged path.", parameters: Type.Object({ path: Type.String(), content: Type.String() }), execute: async (_id: string, params: any) => { callbacks.writeStaged(params.path, params.content); return textResult("staged"); } }),
    ];
    if (repairOnly && callbacks.readVerificationDiagnostic) {
      baseTools.push(pi.defineTool({
        name: "read_verification_diagnostic",
        label: "Read verification diagnostic",
        description: "Read the current verification run's bounded, redacted evidence for one diagnostic id.",
        parameters: Type.Object({ diagnosticId: Type.String({ minLength: 1 }) }),
        execute: async (_id: string, params: any) => textResult(JSON.stringify(callbacks.readVerificationDiagnostic!(params.diagnosticId))),
      }));
    }
    const repairResolution = Type.Object({
      diagnosticId: Type.String({ minLength: 1 }),
      status: Type.Union([Type.Literal("RESOLVED"), Type.Literal("BLOCKED")]),
      changedFiles: Type.Array(Type.String()),
      explanation: Type.String({ minLength: 1 }),
    });
    const completionTool = pi.defineTool({
      name: repairOnly ? "report_repair_complete" : "report_generation_complete",
      label: repairOnly ? "Report repair complete" : "Report generation complete",
      description: repairOnly
        ? "Report the exact staged Manifest file set and the outcome for every current actionable diagnostic."
        : "Report the exact complete staged Manifest file set.",
      parameters: repairOnly
        ? Type.Object({ files: Type.Array(Type.String()), resolutions: Type.Array(repairResolution) })
        : Type.Object({ files: Type.Array(Type.String()) }),
      execute: async (_id: string, params: any) => {
        assertRequiredGenerationContextRead(callbacks.requiredGenerationContextKeys, readContextKeys);
        callbacks.reportComplete(params.files, params.resolutions);
        return textResult(repairOnly ? "repair accepted for verification" : "generation accepted for verification");
      },
    });
    const tools = repairOnly ? [...baseTools, completionTool] : [
      ...baseTools,
      pi.defineTool({ name: "delete_staged_file", label: "Delete staged file", description: "Delete an unconfirmed staged file.", parameters: pathParameters, execute: async (_id: string, params: any) => { callbacks.deleteStaged(params.path); return textResult("deleted"); } }),
      completionTool,
    ];
    const created = await pi.createAgentSession({
      cwd, agentDir, model, modelRuntime: runtime, thinkingLevel: this.config.thinkingLevel,
      sessionManager: existingSessionFile
        ? pi.SessionManager.open(existingSessionFile, sessionDir)
        : pi.SessionManager.create(cwd, sessionDir), settingsManager, resourceLoader: loader,
      noTools: "builtin", customTools: tools,
    });
    const session = created.session;
    activePiSessionId = session.sessionId;
    activePiSessionId = session.sessionId;
    session.subscribe((event: any) => mapPiEvent(event, callbacks, repairOnly ? "REPAIR" : "GENERATOR"));
    return {
      sessionId: session.sessionId,
      sessionFile: session.sessionFile,
      prompt: (text) => session.prompt(text),
      messages: () => [],
      abort: () => session.abort(),
      dispose: () => session.dispose(),
    };
  }

  private createFakeGenerationHandle(generationId: string, callbacks: GenerationPiCallbacks): SessionHandle {
    const sessionFile = join(this.database.dataDir, "pi-sessions", `${generationId}.fake.jsonl`);
    let cancelled = false;
    return {
      sessionId: `pi_fake_${generationId}`,
      sessionFile,
      prompt: async () => {
        callbacks.onEvent("agent.started", { purpose: "GENERATOR" });
        for (const item of callbacks.listGenerationContext().filter(({ required }) => required)) callbacks.readGenerationContext(item.key);
        const existingRoutes = callbacks.readReference(callbacks.spec.paths.routeRegistry);
        for (const [path, content] of Object.entries(createFakeGenerationFiles(callbacks.requirement, callbacks.spec, callbacks.contract, existingRoutes))) {
          if (cancelled) throw new Error("generation cancelled");
          callbacks.writeStaged(path, content);
          callbacks.onEvent("generation.file_changed", { generationId, relativePath: path });
          await Promise.resolve();
        }
        callbacks.reportComplete(callbacks.listStaged());
        appendFileSync(sessionFile, `${JSON.stringify({ type: "generation_complete", files: callbacks.listStaged() })}\n`, "utf8");
        callbacks.onEvent("agent.completed", { purpose: "GENERATOR" });
      },
      messages: () => [],
      abort: () => { cancelled = true; },
      dispose: () => undefined,
    };
  }

  private createFakeRepairHandle(generationId: string, callbacks: GenerationPiCallbacks): SessionHandle {
    const sessionFile = join(this.database.dataDir, "pi-sessions", `${generationId}.fake.jsonl`);
    let cancelled = false;
    return {
      sessionId: `pi_fake_repair_${generationId}`,
      sessionFile,
      prompt: async () => {
        for (const item of callbacks.listGenerationContext().filter(({ required }) => required)) callbacks.readGenerationContext(item.key);
        callbacks.onEvent("agent.started", { purpose: "REPAIR" });
        if (cancelled) throw new Error("repair cancelled");
        callbacks.reportComplete(callbacks.listStaged());
        callbacks.onEvent("agent.completed", { purpose: "REPAIR" });
      },
      messages: () => [],
      abort: () => { cancelled = true; },
      dispose: () => undefined,
    };
  }

  private createFakeReviewHandle(reviewId: string, callbacks: ReviewPiCallbacks): SessionHandle {
    const sessionFile = join(this.database.dataDir, "pi-sessions", `${reviewId}.fake.jsonl`);
    return {
      sessionId: `pi_fake_${reviewId}`,
      sessionFile,
      prompt: async () => {
        callbacks.readQuality();
        callbacks.submit({
          verdict: "APPROVE",
          summary: "Independent read-only review found no additional boundary violations.",
          issues: [],
        });
        appendFileSync(sessionFile, JSON.stringify({ type: "review_complete", verdict: "APPROVE" }) + "\n", "utf8");
      },
      messages: () => [],
      dispose: () => undefined,
    };
  }

  private async createRealReviewHandle(reviewId: string, callbacks: ReviewPiCallbacks): Promise<SessionHandle> {
    const pi: any = await import("@earendil-works/pi-coding-agent");
    const runtime = await this.getModelRuntime();
    const [provider, ...modelParts] = this.config.piModel.split("/");
    const model = runtime.getModel(provider, modelParts.join("/"));
    if (!model) throw new Error(`PI model not found: ${this.config.piModel}`);
    const sessionDir = join(this.database.dataDir, "pi-sessions");
    const cwd = join(this.database.dataDir, "controlled-cwd", reviewId);
    const agentDir = join(this.database.dataDir, "controlled-pi-agent");
    mkdirSync(cwd, { recursive: true });
    mkdirSync(agentDir, { recursive: true });
    const settingsManager = pi.SettingsManager.inMemory({ compaction: { enabled: false } });
    const loader = new pi.DefaultResourceLoader({
      cwd,
      agentDir,
      settingsManager,
      systemPromptOverride: () => "You are an independent read-only Flow Mind code reviewer. You cannot modify files. Inspect staged files, diffs, and quality results, then call submit_code_review exactly once.",
      additionalExtensionPaths: [],
      extensionFactories: [],
    });
    await loader.reload();
    const textResult = (text: string) => ({ content: [{ type: "text", text }], details: {} });
    const pathParameters = Type.Object({ path: Type.String({ minLength: 1 }) });
    const issue = Type.Object({
      code: Type.String({ minLength: 1 }),
      title: Type.String({ minLength: 1 }),
      message: Type.String({ minLength: 1 }),
      severity: Type.Union([Type.Literal("BLOCKING"), Type.Literal("WARNING"), Type.Literal("INFO")]),
      relativePath: Type.Optional(Type.String()),
      line: Type.Optional(Type.Integer({ minimum: 1 })),
      evidence: Type.Optional(Type.String()),
      repairHint: Type.Optional(Type.String()),
      repairability: Type.Optional(Type.Union([
        Type.Literal("CODE_ACTIONABLE"),
        Type.Literal("INFRASTRUCTURE"),
        Type.Literal("PROTECTED_FILE"),
        Type.Literal("UNKNOWN"),
      ])),
    });
    const tools = [
      pi.defineTool({ name: "list_generation_context", label: "List immutable generation context", description: "List the snapshotted skill and golden references used by this generation.", parameters: Type.Object({}), execute: async () => textResult(JSON.stringify(callbacks.listGenerationContext?.() || [])) }),
      pi.defineTool({ name: "read_generation_context", label: "Read immutable generation context", description: "Read one snapshotted skill or golden reference by key.", parameters: Type.Object({ key: Type.String({ minLength: 1 }) }), execute: async (_id: string, params: any) => textResult(callbacks.readGenerationContext?.(params.key) || "context unavailable") }),
      pi.defineTool({ name: "read_staged_file", label: "Read staged file", description: "Read one Manifest-managed staged file.", parameters: pathParameters, execute: async (_id: string, params: any) => textResult(callbacks.readStaged(params.path)) }),
      pi.defineTool({ name: "read_staged_diff", label: "Read staged diff", description: "Read one Manifest-managed staged diff.", parameters: pathParameters, execute: async (_id: string, params: any) => textResult(callbacks.readDiff(params.path)) }),
      pi.defineTool({ name: "read_quality_report", label: "Read quality report", description: "Read normalized static and command verification results.", parameters: Type.Object({}), execute: async () => textResult(callbacks.readQuality()) }),
      pi.defineTool({
        name: "submit_code_review",
        label: "Submit code review",
        description: "Submit the independent read-only review result.",
        parameters: Type.Object({
          verdict: Type.Union([Type.Literal("APPROVE"), Type.Literal("CHANGES_REQUESTED")]),
          summary: Type.String(),
          issues: Type.Array(issue),
        }),
        execute: async (_id: string, params: any) => {
          callbacks.submit(params);
          return textResult("review submitted");
        },
      }),
    ];
    const created = await pi.createAgentSession({
      cwd,
      agentDir,
      model,
      modelRuntime: runtime,
      thinkingLevel: this.config.thinkingLevel,
      sessionManager: pi.SessionManager.create(cwd, sessionDir),
      settingsManager,
      resourceLoader: loader,
      noTools: "builtin",
      customTools: tools,
    });
    const session = created.session;
    session.subscribe((event: any) => mapPiEvent(event, callbacks, "REVIEWER"));
    return {
      sessionId: session.sessionId,
      sessionFile: session.sessionFile,
      prompt: (text) => session.prompt(text),
      messages: () => [],
      abort: () => session.abort(),
      dispose: () => session.dispose(),
    };
  }
  private compactionContext(agentSessionId: string, generationId?: string): string {
    const session = this.database.getSession(agentSessionId);
    const process = this.database.getProcessBySession(agentSessionId);
    const generation = generationId ? this.database.getGeneration(generationId) : this.database.getLatestGenerationBySession(agentSessionId);
    return JSON.stringify({
      workflowState: session?.state || "UNKNOWN",
      requirementRevision: session?.requirement_revision || 0,
      requirement: session?.requirement_json ? JSON.parse(session.requirement_json) : null,
      platformDefinitionId: process?.platform_definition_id || null,
      processStatus: process?.status || null,
      processSnapshot: process?.platform_snapshot_json ? JSON.parse(process.platform_snapshot_json) : null,
      generationId: generation?.id || null,
      generationRevision: generation?.generation_revision || 0,
      generatedFiles: generation?.artifact_manifest_json ? JSON.parse(generation.artifact_manifest_json).files || [] : [],
      constraints: ["Java 8", "Spring Boot 2.7.18", "Vue 3", "startAndSubmit only", "confirmed business generation boundary"],
      qualityState: "M4_NOT_STARTED",
      nextStep: generation?.status === "REVIEW" ? "human code review" : "continue the current workflow stage",
    });
  }

  private async getModelRuntime(): Promise<any> {
    if (!this.modelRuntime) {
      const pi: any = await import("@earendil-works/pi-coding-agent");
      this.modelRuntime = await pi.ModelRuntime.create();
    }
    return this.modelRuntime;
  }

  onModuleDestroy(): void {
    for (const handle of this.handles.values()) handle.dispose();
    for (const handle of this.generationHandles.values()) handle.dispose();
  }

  private deletePersistedSessionFile(sessionFile: string | undefined): void {
    if (!sessionFile) return;
    const sessionDir = resolve(this.database.dataDir, "pi-sessions");
    const candidate = resolve(sessionFile);
    const pathFromSessionDir = relative(sessionDir, candidate);
    if (!pathFromSessionDir || pathFromSessionDir.startsWith("..") || isAbsolute(pathFromSessionDir)) return;
    if (existsSync(candidate)) rmSync(candidate, { force: true });
  }
}

function mapPiEvent(event: any, callbacks: PiEventSink, purpose: "GENERATOR" | "REPAIR" | "REVIEWER" = "GENERATOR"): void {
  if (event.type === "message_update") {
    const am = event.assistantMessageEvent;
    if (am?.type === "text_delta") {
      callbacks.onEvent("assistant.delta", { delta: am.delta, purpose });
    } else if (am?.type === "thinking_delta") {
      // Reasoning models stream their thinking separately from the visible
      // text. Forward it so the UI can show a transient "thinking" buffer that
      // is discarded once real output or a tool call begins.
      callbacks.onEvent("reasoning.delta", { delta: am.delta, purpose });
    } else if (am?.type === "thinking_end") {
      callbacks.onEvent("reasoning.completed", { purpose });
    }
  } else if (event.type === "message_end" && (event.message?.stopReason === "error" || event.message?.errorMessage)) {
    callbacks.onError("AGENT_MODEL_ERROR", event.message.errorMessage || "generator model failed");
  } else if (event.type === "agent_start") callbacks.onEvent("agent.started", { purpose });
  else if (event.type === "agent_end") callbacks.onEvent("agent.completed", { purpose });
  else if (event.type === "tool_execution_start") callbacks.onEvent("tool.started", { toolName: event.toolName, toolCallId: event.toolCallId, ...summarizeToolArgs(event.args), purpose });
  else if (event.type === "tool_execution_end") callbacks.onEvent("tool.completed", { toolName: event.toolName, toolCallId: event.toolCallId, isError: event.isError, purpose });
}

/**
 * Extract a compact, safe identifier from a tool call's arguments so the UI can
 * show "which file" without leaking large payloads (e.g. write_staged_file.content)
 * over the event stream. Returns the staged/reference path, or a diagnostic id.
 */
function summarizeToolArgs(args: any): { target?: string } {
  if (!args || typeof args !== "object") return {};
  const target = typeof args.path === "string" && args.path.trim()
    ? args.path
    : typeof args.diagnosticId === "string" && args.diagnosticId.trim()
      ? args.diagnosticId
      : undefined;
  return target ? { target } : {};
}

function toConversationMessage(message: any): ConversationMessage {
  const role = message?.role === "user" ? "user" : message?.role === "toolResult" ? "tool" : "assistant";
  const content = Array.isArray(message?.content)
    ? message.content.filter((item: any) => item?.type === "text").map((item: any) => item.text).join("")
    : typeof message?.content === "string" ? message.content : "";
  return {
    id: message?.id || `msg_${Date.now()}_${Math.random().toString(16).slice(2)}`,
    role,
    content,
    createdAt: message?.timestamp ? new Date(message.timestamp).toISOString() : undefined,
  };
}
