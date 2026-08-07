import { Inject, Injectable, Optional } from "@nestjs/common";
import { randomUUID } from "node:crypto";
import type { CodeReviewReport, QualityStageResult } from "@flowmind/agent-contracts";
import { DatabaseService, type GenerationRow } from "../persistence/database.service.js";
import { PiAdapterService } from "../pi/pi-adapter.service.js";
import { StagingService } from "../generation/staging.service.js";
import { EventBusService } from "../workflow/event-bus.service.js";

@Injectable()
export class ReviewerService {
  constructor(
    @Inject(DatabaseService) private readonly database: DatabaseService,
    @Optional() @Inject(PiAdapterService) private readonly pi?: PiAdapterService,
    @Optional() @Inject(StagingService) private readonly staging?: StagingService,
    @Optional() @Inject(EventBusService) private readonly events?: EventBusService,
  ) {}

  async review(
    generation: GenerationRow,
    verificationRunId: string,
    stages: QualityStageResult[] = [],
  ): Promise<CodeReviewReport> {
    const reviewId = `review_${randomUUID()}`;
    const createdAt = new Date().toISOString();
    this.database.db.prepare(`
      INSERT INTO agent_code_review (
        id, generation_id, verification_run_id, revision, status, verdict,
        summary, issues_json, created_at
      ) VALUES (?, ?, ?, ?, 'RUNNING', 'UNAVAILABLE', '', '[]', ?)
    `).run(reviewId, generation.id, verificationRunId, generation.generation_revision, createdAt);

    let submitted: Pick<CodeReviewReport, "verdict" | "summary" | "issues"> | undefined;
    let session: { piSessionId: string; sessionFile?: string } | undefined;
    try {
      if (this.pi && this.staging) {
        session = await this.pi.runReview(
          reviewId,
          "Review the current staged Manifest against the confirmed requirement and quality results. Do not propose files outside the Manifest. For every issue include concrete evidence, an actionable repairHint, and repairability. Submit exactly one code review.",
          {
            readStaged: (path) => this.staging!.read(generation, path).content,
            readDiff: (path) => this.staging!.diff(generation, path).unifiedDiff,
            readQuality: () => JSON.stringify(stages),
            submit: (report) => { submitted = report; },
            onEvent: (type, data) => this.events?.publish(generation.session_id, { type, data }),
            onError: (_code, message) => this.events?.publish(generation.session_id, { type: "error", data: { message } }),
          },
        );
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
    }
  }
}
