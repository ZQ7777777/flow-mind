import { Inject, Injectable, OnModuleDestroy } from "@nestjs/common";
import { mkdirSync, appendFileSync, existsSync, readFileSync, rmSync } from "node:fs";
import { isAbsolute, join, relative, resolve } from "node:path";
import { randomUUID } from "node:crypto";
import { Type } from "@sinclair/typebox";
import {
  ENTRY_APPLICATION_REQUIREMENT,
  businessRequirementSchema,
  type BusinessRequirement,
  type ConversationMessage,
  type GenerationTargetContract,
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
  reportComplete(files: string[]): void;
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
          await callbacks.onRequirement(ENTRY_APPLICATION_REQUIREMENT, [], []);
          const content = "入金申请结构化需求已准备完成，请在右侧预览并确认。";
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
      systemPromptOverride: () => "You are a constrained Flow Mind code generator. Follow the user prompt and use only registered staging tools.",
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
    const pathParameters = Type.Object({ path: Type.String({ minLength: 1 }) });
    const tools = [
      pi.defineTool({ name: "read_generation_contract_file", label: "Read generation contract reference", description: "Read one target reference allowed by generation-target.json.", parameters: pathParameters, execute: async (_id: string, params: any) => textResult(callbacks.readReference(params.path)) }),
      pi.defineTool({ name: "read_staged_file", label: "Read staged file", description: "Read one file in this generation staging area.", parameters: pathParameters, execute: async (_id: string, params: any) => textResult(callbacks.readStaged(params.path)) }),
      pi.defineTool({ name: "list_staged_files", label: "List staged files", description: "List files staged by this generation.", parameters: Type.Object({}), execute: async () => textResult(JSON.stringify(callbacks.listStaged())) }),
      pi.defineTool({ name: "write_staged_file", label: "Write staged file", description: "Write UTF-8 content to an allowed staged path.", parameters: Type.Object({ path: Type.String(), content: Type.String() }), execute: async (_id: string, params: any) => { callbacks.writeStaged(params.path, params.content); return textResult("staged"); } }),
      pi.defineTool({ name: "delete_staged_file", label: "Delete staged file", description: "Delete an unconfirmed staged file.", parameters: pathParameters, execute: async (_id: string, params: any) => { callbacks.deleteStaged(params.path); return textResult("deleted"); } }),
      pi.defineTool({ name: "report_generation_complete", label: "Report generation complete", description: "Report the exact complete staged file set.", parameters: Type.Object({ files: Type.Array(Type.String()) }), execute: async (_id: string, params: any) => { callbacks.reportComplete(params.files); return textResult("generation accepted for human review"); } }),
    ];
    const created = await pi.createAgentSession({
      cwd, agentDir, model, modelRuntime: runtime, thinkingLevel: this.config.thinkingLevel,
      sessionManager: pi.SessionManager.create(cwd, sessionDir), settingsManager, resourceLoader: loader,
      noTools: "builtin", customTools: tools,
    });
    const session = created.session;
    activePiSessionId = session.sessionId;
    activePiSessionId = session.sessionId;
    session.subscribe((event: any) => mapGenerationEvent(event, callbacks));
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

function mapGenerationEvent(event: any, callbacks: GenerationPiCallbacks): void {
  if (event.type === "message_update" && event.assistantMessageEvent?.type === "text_delta") {
    callbacks.onEvent("assistant.delta", { delta: event.assistantMessageEvent.delta, purpose: "GENERATOR" });
  } else if (event.type === "message_end" && (event.message?.stopReason === "error" || event.message?.errorMessage)) {
    callbacks.onError("AGENT_MODEL_ERROR", event.message.errorMessage || "generator model failed");
  } else if (event.type === "agent_start") callbacks.onEvent("agent.started", { purpose: "GENERATOR" });
  else if (event.type === "agent_end") callbacks.onEvent("agent.completed", { purpose: "GENERATOR" });
  else if (event.type === "tool_execution_start") callbacks.onEvent("tool.started", { toolName: event.toolName, purpose: "GENERATOR" });
  else if (event.type === "tool_execution_end") callbacks.onEvent("tool.completed", { toolName: event.toolName, isError: event.isError, purpose: "GENERATOR" });
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
