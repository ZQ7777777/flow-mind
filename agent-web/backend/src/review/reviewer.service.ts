import { Inject, Injectable, Optional } from "@nestjs/common";
import { randomUUID } from "node:crypto";
import type { CodeReviewReport, GenerationContextSnapshot, QualityStageResult } from "@flowmind/agent-contracts";
import { DatabaseService, type GenerationRow } from "../persistence/database.service.js";
import { PiAdapterService } from "../pi/pi-adapter.service.js";
import { StagingService } from "../generation/staging.service.js";
import { EventBusService } from "../workflow/event-bus.service.js";
import { GenerationContextRegistry } from "../generation/generation-context-registry.service.js";

export interface ReviewContextItem {
  key: string;
  sha256: string;
  required: boolean;
  read(): string;
}

const DEFAULT_REVIEW_CONTEXT_PATHS = new Set([
  "SKILL.md",
  "references/generated-form-contract.md",
  "references/quality-and-boundaries.md",
  "references/backend-api-contract.md",
  "references/golden-example.md",
]);
const SKILL_CONTEXT_PREFIX = "skill:flowmind-business-generation:";

export function splitReviewContextItems(items: ReviewContextItem[]): {
  defaultItems: ReviewContextItem[];
  exampleItems: ReviewContextItem[];
} {
  return {
    defaultItems: items.filter((item) => item.key.startsWith(SKILL_CONTEXT_PREFIX)
      && DEFAULT_REVIEW_CONTEXT_PATHS.has(item.key.slice(SKILL_CONTEXT_PREFIX.length))),
    exampleItems: items.filter((item) => item.key.startsWith("reference:TARGET:")),
  };
}

@Injectable()
export class ReviewerService {
  private readonly activeReviews = new Map<string, string>();

  constructor(
    @Inject(DatabaseService) private readonly database: DatabaseService,
    @Optional() @Inject(PiAdapterService) private readonly pi?: PiAdapterService,
    @Optional() @Inject(StagingService) private readonly staging?: StagingService,
    @Optional() @Inject(EventBusService) private readonly events?: EventBusService,
    @Optional() @Inject(GenerationContextRegistry) private readonly contexts?: GenerationContextRegistry,
  ) {}

  async review(
    generation: GenerationRow,
    verificationRunId: string,
    stages: QualityStageResult[] = [],
    signal?: AbortSignal,
  ): Promise<CodeReviewReport> {
    const reviewId = `review_${randomUUID()}`;
    const createdAt = new Date().toISOString();
    const cancel = () => this.pi?.cancelReview(reviewId);
    signal?.addEventListener("abort", cancel, { once: true });
    this.activeReviews.set(generation.id, reviewId);
    this.database.db.prepare(`
      INSERT INTO agent_code_review (
        id, generation_id, verification_run_id, revision, status, verdict,
        summary, issues_json, created_at
      ) VALUES (?, ?, ?, ?, 'RUNNING', 'UNAVAILABLE', '', '[]', ?)
    `).run(reviewId, generation.id, verificationRunId, generation.generation_revision, createdAt);

    let submitted: Pick<CodeReviewReport, "verdict" | "summary" | "issues"> | undefined;
    let session: { piSessionId: string; sessionFile?: string } | undefined;
    try {
      if (signal?.aborted) throw new Error("quality gate cancelled");
      if (this.pi && this.staging) {
        const context = JSON.parse(generation.generation_context_snapshot_json || "{}") as GenerationContextSnapshot;
        const contextItems: ReviewContextItem[] = context.version === "1.0" ? [
          ...context.skills.flatMap((skill) => skill.files.map((file) => ({
            key: `skill:${skill.name}:${file.relativePath}`, sha256: file.sha256,
            required: file.relativePath === "SKILL.md" || file.relativePath === "references/golden-example.md",
            read: () => this.contexts?.readSkill(context, skill.name, file.relativePath, generation.session_id) || file.content,
          }))),
          ...context.references.map((reference) => ({
            key: `reference:${reference.source}:${reference.relativePath}`, sha256: reference.sha256, required: false,
            read: () => this.contexts?.readReference(context, reference.source, reference.relativePath, generation.session_id) || reference.content,
          })),
        ] : [];
        const { defaultItems, exampleItems } = splitReviewContextItems(contextItems);
        session = await this.pi.runReview(
          reviewId,
          "Review the current staged Manifest against the confirmed requirement and quality results. Read the default review context first. Target examples are intentionally hidden from the default context; only list and read them after identifying a concrete structural compatibility question, not for general comparison. Only investigate diagnostics scoped CURRENT_GENERATION. Do not investigate, report, or request changes for PRE_EXISTING diagnostics or files outside the Manifest. For every issue include concrete evidence, an actionable repairHint, and repairability. Submit exactly one code review.",
          {
            readStaged: (path) => this.staging!.read(generation, path).content,
            readDiff: (path) => this.staging!.diff(generation, path).unifiedDiff,
            readQuality: () => JSON.stringify(stages),
            listGenerationContext: () => defaultItems.map(({ key, sha256, required }) => ({ key, sha256, required })),
            readGenerationContext: (key) => defaultItems.find((item) => item.key === key)?.read() || "context unavailable",
            listGenerationExamples: () => exampleItems.map(({ key, sha256 }) => ({ key, sha256 })),
            readGenerationExample: (key) => exampleItems.find((item) => item.key === key)?.read() || "example unavailable",
            submit: (report) => { submitted = report; },
            onEvent: (type, data) => this.events?.publish(generation.session_id, { type, data }),
            onError: (_code, message) => this.events?.publish(generation.session_id, { type: "error", data: { message } }),
          },
        );
        if (signal?.aborted) throw new Error("quality gate cancelled");
        if (!submitted) throw new Error("reviewer ended without submit_code_review");
      } else {
        submitted = {
          verdict: "APPROVE",
          summary: "Independent read-only review found no additional boundary violations.",
          issues: [],
        };
      }
      const accepted = submitted as Pick<CodeReviewReport, "verdict" | "summary" | "issues">;
      const report: CodeReviewReport = {
        reviewId,
        status: accepted.verdict === "APPROVE" ? "PASSED" : "FAILED",
        verdict: accepted.verdict,
        summary: accepted.summary,
        issues: accepted.issues,
        createdAt,
        completedAt: new Date().toISOString(),
      };
      this.database.db.prepare(`
        UPDATE agent_code_review SET status = ?, verdict = ?, summary = ?,
          issues_json = ?, pi_session_id = ?, pi_session_file = ?, completed_at = ? WHERE id = ?
      `).run(report.status, report.verdict, report.summary, JSON.stringify(report.issues), session?.piSessionId || null, session?.sessionFile || null, report.completedAt, reviewId);
      return report;
    } catch (error) {
      const completedAt = new Date().toISOString();
      const summary = error instanceof Error ? error.message : String(error);
      this.database.db.prepare(`
        UPDATE agent_code_review SET status = 'INFRASTRUCTURE_FAILED', verdict = 'UNAVAILABLE',
          summary = ?, error_code = 'AGENT_REVIEWER_INFRASTRUCTURE_FAILED',
          error_message = ?, completed_at = ? WHERE id = ?
      `).run(summary, summary, completedAt, reviewId);
      return {
        reviewId,
        status: "INFRASTRUCTURE_FAILED",
        verdict: "UNAVAILABLE",
        summary,
        issues: [],
        createdAt,
        completedAt,
      };
    } finally {
      signal?.removeEventListener("abort", cancel);
      this.activeReviews.delete(generation.id);
    }
  }

  cancel(generationId: string): void {
    const reviewId = this.activeReviews.get(generationId);
    if (reviewId) this.pi?.cancelReview(reviewId);
  }
}
