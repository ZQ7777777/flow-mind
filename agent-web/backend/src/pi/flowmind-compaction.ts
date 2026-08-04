import { complete } from "@earendil-works/pi-ai/compat";
import { uuidv7 } from "@earendil-works/pi-ai";
import { convertToLlm, serializeConversation, type ExtensionAPI } from "@earendil-works/pi-coding-agent";
import type { DatabaseService } from "../persistence/database.service.js";

export const COMPACTION_SECTIONS = [
  "## Current task and workflow state",
  "## Confirmed requirement",
  "## Activated process definition",
  "## Generated files and revision",
  "## Technology and generation boundaries",
  "## Latest quality state",
  "## Open issues and next step",
] as const;

export function calculateCompactionSettings(contextWindow: number): { reserveTokens: number; keepRecentTokens: number } {
  return {
    reserveTokens: Math.min(16384, Math.max(8192, Math.floor(contextWindow * 0.2))),
    keepRecentTokens: Math.min(12000, Math.max(4096, Math.floor(contextWindow * 0.15))),
  };
}

export function isValidFlowMindSummary(summary: string): boolean {
  const estimatedTokens = Math.ceil(summary.length / 4);
  return Boolean(summary.trim()) && estimatedTokens <= 4096 && COMPACTION_SECTIONS.every((section) => summary.includes(section));
}

export function createFlowMindCompactionExtension(options: {
  database: DatabaseService;
  onEvent(type: string, data: unknown): void;
  piSessionId: () => string;
  modelName: string;
  context: () => string;
}) {
  return (pi: ExtensionAPI) => {
    let pendingStatId: string | null = null;

    pi.on("session_compact", async (event) => {
      if (!pendingStatId || !event.fromExtension) return;
      options.database.db.prepare(
        "UPDATE agent_compaction_stat SET entry_id = ? WHERE id = ?",
      ).run(event.compactionEntry.id, pendingStatId);
      pendingStatId = null;
    });

    pi.on("session_before_compact", async (event, ctx) => {
      const started = Date.now();
      const statId = `acs_${uuidv7()}`;
      const tokensBefore = event.preparation.tokensBefore;
      const reason = event.reason;
      let status = "FALLBACK";
      let errorCode: string | null = null;
      let summaryTokens: number | null = null;
      try {
        const [provider, ...modelParts] = options.modelName.split("/");
        const model = provider && modelParts.length ? ctx.modelRegistry.find(provider, modelParts.join("/")) : undefined;
        if (!model) { errorCode = "COMPACTION_MODEL_NOT_FOUND"; return; }
        const auth = await ctx.modelRegistry.getApiKeyAndHeaders(model);
        if (!auth.ok || !auth.apiKey) { errorCode = "COMPACTION_MODEL_NOT_AUTHENTICATED"; return; }
        const allMessages = [...event.preparation.messagesToSummarize, ...event.preparation.turnPrefixMessages];
        const conversation = serializeConversation(convertToLlm(allMessages));
        const response = await complete(model, {
          messages: [{
            role: "user",
            content: [{
              type: "text",
              text: `Create a concise continuation summary using every heading below exactly once. Do not omit a heading. Maximum 4096 tokens.\n\n${COMPACTION_SECTIONS.join("\n")}\n\nAuthoritative Flow Mind state:\n${options.context()}\n\nPrevious summary:\n${event.preparation.previousSummary || "none"}\n\nConversation:\n${conversation}`,
            }],
            timestamp: Date.now(),
          }],
        }, {
          apiKey: auth.apiKey,
          headers: auth.headers,
          env: auth.env,
          maxTokens: 4096,
          signal: event.signal,
          cacheRetention: "none",
          sessionId: uuidv7(),
        });
        const summary = response.content.filter((item): item is { type: "text"; text: string } => item.type === "text").map((item) => item.text).join("\n");
        if (!isValidFlowMindSummary(summary)) { errorCode = "COMPACTION_SUMMARY_INVALID"; return; }
        status = "SUCCESS";
        summaryTokens = Math.ceil(summary.length / 4);
        options.onEvent("context.compacted", { reason, tokensBefore, summaryTokens });
        return {
          compaction: {
            summary,
            firstKeptEntryId: event.preparation.firstKeptEntryId,
            tokensBefore,
            usage: response.usage,
          },
        };
      } catch (error) {
        errorCode = event.signal.aborted ? "COMPACTION_ABORTED" : "COMPACTION_FAILED";
        return;
      } finally {
        options.database.db.prepare(`
          INSERT INTO agent_compaction_stat (
            id, pi_session_id, entry_id, reason, tokens_before, summary_tokens,
            duration_ms, status, error_code, created_at
          ) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?)
        `).run(
          statId, options.piSessionId(), reason, tokensBefore, summaryTokens,
          Date.now() - started, status, errorCode, new Date().toISOString(),
        );
        if (status === "SUCCESS") pendingStatId = statId;
      }
    });
  };
}
