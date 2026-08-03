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
} from "@flowmind/agent-contracts";
import { loadConfig } from "../config.js";
import { DatabaseService } from "../persistence/database.service.js";
import { REQUIREMENT_SYSTEM_PROMPT } from "./requirement-prompt.js";

interface SessionHandle {
  sessionId: string;
  sessionFile?: string;
  prompt(text: string): Promise<void>;
  messages(): ConversationMessage[];
  dispose(): void;
}

export interface PiCallbacks {
  onEvent(type: string, data: unknown): void;
  onError(code: string, message: string): void;
  onRequirement(requirement: BusinessRequirement, missingItems: string[], ambiguities: string[]): Promise<void>;
}

@Injectable()
export class PiAdapterService implements OnModuleDestroy {
  private readonly config = loadConfig();
  private readonly handles = new Map<string, SessionHandle>();
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
    const loader = new pi.DefaultResourceLoader({
      cwd,
      agentDir,
      systemPromptOverride: () => REQUIREMENT_SYSTEM_PROMPT,
      additionalExtensionPaths: [],
      extensionFactories: [],
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

  private async getModelRuntime(): Promise<any> {
    if (!this.modelRuntime) {
      const pi: any = await import("@earendil-works/pi-coding-agent");
      this.modelRuntime = await pi.ModelRuntime.create();
    }
    return this.modelRuntime;
  }

  onModuleDestroy(): void {
    for (const handle of this.handles.values()) handle.dispose();
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
