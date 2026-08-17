import { HttpStatus, Inject, Injectable, Optional } from "@nestjs/common";
import { randomUUID } from "node:crypto";
import type {
  BusinessRequirement,
  CodeReviewReport,
  GenerationQualityReport,
  QualityOverrideSummary,
  QualityStageName,
  QualityStageResult,
} from "@flowmind/agent-contracts";
import { DatabaseService } from "../persistence/database.service.js";
import { AgentError } from "../common/agent-error.js";
import { EventBusService } from "../workflow/event-bus.service.js";
import { deriveGenerationSpec } from "../generation/generation-spec.js";
import { generationContract, parseManifest, StagingService } from "../generation/staging.service.js";
import { TargetContractService } from "../generation/target-contract.service.js";
import { StaticValidatorService } from "../validation/static-validator.service.js";
import { ReviewerService } from "../review/reviewer.service.js";
import { evaluateQualityGates, nextRepairDecision, type SoftGateScope } from "./quality-gates.js";
import { RepairCoordinatorService } from "../repair/repair-coordinator.service.js";
import {
  VerificationWorkerService,
  type VerificationCommandExecutor,
  type VerificationCommandStage,
} from "./verification-worker.service.js";
import { qualityDiagnostic } from "./quality-diagnostic.js";

interface QualityActionRequest {
  idempotencyKey: string;
  requestHash: string;
  expectedRowVersion: number;
}

@Injectable()
export class QualityPipelineService {
  private readonly activeRuns = new Map<string, AbortController>();

  constructor(
    @Inject(DatabaseService) private readonly database: DatabaseService,
    @Inject(StagingService) private readonly staging: StagingService,
    @Inject(TargetContractService) private readonly targets: TargetContractService,
    @Inject(StaticValidatorService) private readonly staticValidator: StaticValidatorService,
    @Inject(VerificationWorkerService) private readonly worker: VerificationWorkerService,
    @Inject(ReviewerService) private readonly reviewer: ReviewerService,
    @Inject(EventBusService) private readonly events: EventBusService,
    @Optional() @Inject(RepairCoordinatorService) private readonly repair?: RepairCoordinatorService,
  ) {}

  start(generationId: string, trigger: "GENERATION" | "REVERIFY" | "REPAIR" = "GENERATION"): void {
    const active = this.activeRuns.get(generationId);
    if (active && !active.signal.aborted) return;
    const controller = new AbortController();
    this.activeRuns.set(generationId, controller);
    setImmediate(() => void this.run(generationId, trigger, controller));
  }

  getReport(generationId: string): GenerationQualityReport | undefined {
    const generation = this.database.getGeneration(generationId);
    if (!generation?.quality_report_json) return undefined;
    return JSON.parse(generation.quality_report_json) as GenerationQualityReport;
  }

  reverify(
    generationId: string,
    revision: number,
    skipAiReview = false,
    action?: QualityActionRequest,
  ): { accepted: true; state: "CODE_VERIFYING" } {
    const generation = this.database.getGeneration(generationId);
    if (!generation || !["REVIEW", "FAILED"].includes(generation.status)
      || generation.generation_revision !== revision) {
      throw new AgentError(
        HttpStatus.CONFLICT,
        "AGENT_GENERATION_REVISION_CONFLICT",
        "Only the current review revision can be reverified.",
        generation?.session_id,
      );
    }
    const now = new Date().toISOString();
    const result = { accepted: true as const, state: "CODE_VERIFYING" as const };
    this.database.transaction(() => {
      this.database.db.prepare(`
        UPDATE agent_quality_override SET invalidated_at = ?
        WHERE generation_id = ? AND invalidated_at IS NULL
      `).run(now, generationId);
      this.database.db.prepare("DELETE FROM agent_repair_attempt WHERE generation_id = ?").run(generationId);
      this.database.db.prepare(`
        UPDATE agent_code_generation SET status = 'VERIFYING', quality_revision = ?,
          quality_report_json = NULL, latest_verification_run_id = NULL, latest_review_id = NULL,
          hard_gate_passed = 0, override_required = 0, quality_override_id = NULL,
          skip_ai_review = ?, can_write = 0, repair_round = 0, max_repair_rounds = 3,
          last_error_code = NULL, last_error_message = NULL, updated_at = ?
        WHERE id = ? AND generation_revision = ?
      `).run(revision, skipAiReview ? 1 : 0, now, generationId, revision);
      const sessionUpdate = this.database.db.prepare(`
        UPDATE agent_session SET state = 'CODE_VERIFYING', row_version = row_version + 1,
          last_error_code = NULL, last_error_message = NULL, updated_at = ?
        WHERE id = ? AND (? IS NULL OR row_version = ?)
      `).run(now, generation.session_id, action?.expectedRowVersion ?? null, action?.expectedRowVersion ?? null);
      if (sessionUpdate.changes !== 1) {
        throw new AgentError(HttpStatus.CONFLICT, "AGENT_ROW_VERSION_CONFLICT", "row version is stale", generation.session_id);
      }
      if (action) this.insertAction(generationId, "REVERIFY", action, result, now);
    });
    this.start(generationId, "REVERIFY");
    return result;
  }
  stop(generationId: string, expectedRowVersion: number): { cancelled: true; state: "CODE_REVIEW" } {
    const generation = this.database.getGeneration(generationId);
    if (!generation || !["VERIFYING", "REVIEWING", "REPAIRING"].includes(generation.status)) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_QUALITY_NOT_RUNNING", "Quality gate is not running.", generation?.session_id);
    }
    const session = this.database.getSession(generation.session_id);
    if (!session || !["CODE_VERIFYING", "CODE_REVIEWING", "CODE_REPAIRING"].includes(session.state)) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_STATE_CONFLICT", "current state does not allow this operation", generation.session_id);
    }
    if (!Number.isInteger(expectedRowVersion) || session.row_version !== expectedRowVersion) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_ROW_VERSION_CONFLICT", "row version is stale", generation.session_id, { expected: session.row_version });
    }
    this.activeRuns.get(generationId)?.abort();
    this.reviewer.cancel(generationId);
    this.repair?.cancel(generationId);

    const now = new Date().toISOString();
    const runningRuns = this.database.db.prepare(`
      SELECT id, stage_results_json FROM agent_verification_run
      WHERE generation_id = ? AND status = 'RUNNING'
      ORDER BY created_at DESC
    `).all(generationId) as Array<{ id: string; stage_results_json: string }>;
    const stages = runningRuns[0]?.stage_results_json
      ? JSON.parse(runningRuns[0].stage_results_json) as QualityStageResult[]
      : [];
    const report: GenerationQualityReport = {
      generationId,
      revision: generation.generation_revision,
      pipelineState: "CANCELLED",
      repairRound: 0,
      maxRepairRounds: 3,
      stages,
      repairAttempts: this.repair?.history(generationId),
      aiReviewSkipped: Boolean(generation.skip_ai_review),
      hardGatePassed: false,
      overrideRequired: false,
      canWrite: false,
      updatedAt: now,
    };
    this.database.transaction(() => {
      for (const run of runningRuns) {
        this.database.db.prepare(`
          UPDATE agent_verification_run SET status = 'CANCELLED',
            stage_results_json = ?, completed_at = ? WHERE id = ?
        `).run(JSON.stringify(stages), now, run.id);
      }
      this.database.db.prepare(`
        UPDATE agent_code_generation SET status = 'REVIEW', repair_round = 0,
          max_repair_rounds = 3, quality_revision = ?, latest_verification_run_id = ?,
          quality_report_json = ?, hard_gate_passed = 0, override_required = 0,
          quality_override_id = NULL, can_write = 0, last_error_code = NULL,
          last_error_message = NULL, updated_at = ?
        WHERE id = ? AND status IN ('VERIFYING', 'REVIEWING', 'REPAIRING')
      `).run(generation.generation_revision, runningRuns[0]?.id || null, JSON.stringify(report), now, generationId);
      this.database.db.prepare(`
        UPDATE agent_session SET state = 'CODE_REVIEW', row_version = row_version + 1,
          last_error_code = NULL, last_error_message = NULL, updated_at = ?
        WHERE id = ? AND row_version = ?
      `).run(now, generation.session_id, expectedRowVersion);
    });
    this.events.publish(generation.session_id, { type: "generation.quality_completed", data: report });
    this.events.publish(generation.session_id, { type: "workflow.state_changed", data: { state: "CODE_REVIEW" } });
    return { cancelled: true, state: "CODE_REVIEW" };
  }

  override(
    generationId: string,
    revision: number,
    scopes: SoftGateScope[],
    reason: string,
    createdBy: string,
    action?: QualityActionRequest,
  ): GenerationQualityReport {
    const generation = this.database.getGeneration(generationId);
    const report = this.getReport(generationId);
    const normalizedScopes = [...new Set(scopes)];
    if (!generation || generation.status !== "REVIEW" || generation.generation_revision !== revision
      || generation.quality_revision !== revision || !report) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_GENERATION_REVISION_CONFLICT", "Quality result is not current.");
    }
    if (reason.trim().length < 10) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_QUALITY_OVERRIDE_REASON_REQUIRED", "Override reason must contain at least 10 characters.", generation.session_id);
    }
    if (!normalizedScopes.length || normalizedScopes.some((scope) =>
      !["BACKEND_TESTS", "FRONTEND_TESTS", "REVIEWER"].includes(scope))) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_QUALITY_OVERRIDE_SCOPE_INVALID", "Only JUnit, Vitest, or Reviewer failures can be overridden.", generation.session_id);
    }
    const current = evaluateQualityGates(report.stages, report.review, [], Boolean(report.aiReviewSkipped));
    if (!current.hardGatePassed || normalizedScopes.some((scope) => !current.softFailures.includes(scope))) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_QUALITY_OVERRIDE_FORBIDDEN", "Hard gates and passing soft gates cannot be overridden.", generation.session_id);
    }
    const override: QualityOverrideSummary = {
      overrideId: `override_${randomUUID()}`,
      revision,
      scopes: normalizedScopes,
      reason: reason.trim(),
      createdBy,
      createdAt: new Date().toISOString(),
    };
    const decision = evaluateQualityGates(report.stages, report.review, normalizedScopes, Boolean(report.aiReviewSkipped));
    const updated: GenerationQualityReport = {
      ...report,
      override,
      overrideRequired: decision.overrideRequired,
      canWrite: decision.canWrite,
      updatedAt: new Date().toISOString(),
    };
    this.database.transaction(() => {
      this.database.db.prepare(`
        INSERT INTO agent_quality_override (
          id, generation_id, revision, scopes_json, reason, created_by, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
      `).run(override.overrideId, generationId, revision, JSON.stringify(normalizedScopes), override.reason, createdBy, override.createdAt);
      this.database.db.prepare(`
        UPDATE agent_code_generation SET quality_override_id = ?, override_required = ?,
          can_write = ?, quality_report_json = ?, updated_at = ? WHERE id = ?
      `).run(override.overrideId, decision.overrideRequired ? 1 : 0, decision.canWrite ? 1 : 0, JSON.stringify(updated), updated.updatedAt, generationId);
      const sessionUpdate = this.database.db.prepare(`
        UPDATE agent_session SET row_version = row_version + 1, updated_at = ?
        WHERE id = ? AND (? IS NULL OR row_version = ?)
      `).run(updated.updatedAt, generation.session_id, action?.expectedRowVersion ?? null, action?.expectedRowVersion ?? null);
      if (sessionUpdate.changes !== 1) {
        throw new AgentError(HttpStatus.CONFLICT, "AGENT_ROW_VERSION_CONFLICT", "row version is stale", generation.session_id);
      }
      if (action) this.insertAction(generationId, "OVERRIDE_QUALITY", action, updated, updated.updatedAt);
    });
    return updated;

  }

  private insertAction(
    generationId: string,
    actionName: string,
    action: QualityActionRequest,
    result: unknown,
    createdAt: string,
  ): void {
    this.database.db.prepare(`
      INSERT INTO agent_generation_action (
        id, generation_id, action, idempotency_key, request_hash, result_json, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?)
    `).run(
      `action_${randomUUID()}`,
      generationId,
      actionName,
      action.idempotencyKey,
      action.requestHash,
      JSON.stringify(result),
      createdAt,
    );
  }

  async run(
    generationId: string,
    trigger: "GENERATION" | "REVERIFY" | "REPAIR" = "GENERATION",
    controller = new AbortController(),
  ): Promise<void> {
    const signal = controller.signal;
    let generation = this.database.getGeneration(generationId);
    if (signal.aborted || !generation || generation.status !== "VERIFYING") {
      if (this.activeRuns.get(generationId) === controller) this.activeRuns.delete(generationId);
      return;
    }
    const previousStageRuns = (this.database.db.prepare(`
      SELECT stage_results_json FROM agent_verification_run
      WHERE generation_id = ? AND status != 'RUNNING'
      ORDER BY created_at
    `).all(generationId) as Array<{ stage_results_json: string }>).map(({ stage_results_json }) =>
      JSON.parse(stage_results_json) as QualityStageResult[]);
    const previousStages = previousStageRuns.at(-1);
    const reverifyStages = trigger === "REPAIR"
      ? selectRepairVerificationStages(previousStages || [], this.repair?.history(generationId).at(-1)?.changedFiles || [])
      : undefined;
    const runId = `verification_${randomUUID()}`;
    const startedAt = new Date().toISOString();
    this.database.db.prepare(`
      INSERT INTO agent_verification_run (
        id, generation_id, revision, repair_round, trigger, status,
        stage_results_json, started_at, created_at
      ) VALUES (?, ?, ?, ?, ?, 'RUNNING', '[]', ?, ?)
    `).run(
      runId,
      generation.id,
      generation.generation_revision,
      generation.repair_round,
      trigger,
      startedAt,
      startedAt,
    );
    this.events.publish(generation.session_id, {
      type: "generation.stage_changed",
      data: { generationId, state: "CODE_VERIFYING", runId },
    });

    try {
      const target = this.targets.validate(generation.target_root, generation.session_id);
      const contract = generationContract(generation);
      const requirement = JSON.parse(generation.requirement_snapshot_json) as BusinessRequirement;
      const spec = deriveGenerationSpec(requirement, contract);
      const manifest = parseManifest(generation);
      const files = new Map(manifest.files.map(({ relativePath }) => [
        relativePath,
        this.staging.read(generation!, relativePath).content,
      ]));
      this.publishVerifyStage(generation.session_id, generationId, runId, "STATIC_VALIDATION", "RUNNING", true);
      const staticResult = this.staticValidator.validate({
        generationId,
        revision: generation.generation_revision,
        requirement,
        contract,
        spec,
        manifest,
        files,
      });
      this.publishVerifyStage(generation.session_id, generationId, runId, "STATIC_VALIDATION", staticResult.status, staticResult.hardGate);
      if (signal.aborted) return;
      const staticBlockingDiagnostics = staticResult.diagnostics.filter(({ severity, hardGate }) => severity === "ERROR" && hardGate);
      const invalidPaths = new Set(staticBlockingDiagnostics.map(({ relativePath }) => relativePath).filter(Boolean));
      const hasGlobalError = staticBlockingDiagnostics.some(({ relativePath }) => !relativePath);
      const validatedManifest = {
        ...manifest,
        files: manifest.files.map((file) => ({
          ...file,
          validationStatus: staticResult.status === "PASSED"
            ? "VALID" as const
            : hasGlobalError || invalidPaths.has(file.relativePath) ? "INVALID" as const : "VALID" as const,
        })),
      };
      this.database.db.prepare("UPDATE agent_code_generation SET artifact_manifest_json = ?, updated_at = ? WHERE id = ? AND generation_revision = ?")
        .run(JSON.stringify(validatedManifest), new Date().toISOString(), generation.id, generation.generation_revision);
      if (signal.aborted) return;
      const sessionId = generation.session_id;
      let stages: QualityStageResult[] = [staticResult];
      if (staticResult.status === "PASSED") {
        const workerResult = await this.worker.run({
          generationId,
          revision: generation.generation_revision,
          targetRoot: target.targetRoot,
          stagingDir: generation.staging_dir,
          contract,
          manifest,
          dataDir: this.database.dataDir,
          execute: fakeExecutor(),
          stages: reverifyStages,
          signal,
          onStage: (stage, status, hardGate) => this.publishVerifyStage(sessionId, generationId, runId, stage, status, hardGate),
        });
        if (signal.aborted) return;
        stages = mergeVerificationStages(staticResult, workerResult.stages, previousStages, reverifyStages);
      } else {
        const skipped = skippedCommandStages();
        for (const stage of skipped) {
          this.publishVerifyStage(generation.session_id, generationId, runId, stage.stage, stage.status, stage.hardGate);
        }
        stages.push(...skipped);
      }
      attachRunToDiagnostics(stages, runId);
      classifyDiagnostics(stages, previousStageRuns.at(-1));
      const resolvedDiagnostics = collectResolvedDiagnostics(stages, previousStageRuns.at(-1));

      generation = this.database.getGeneration(generationId);
      if (signal.aborted || !generation || generation.status !== "VERIFYING" || generation.generation_revision !== manifest.revision) return;
      let infrastructureFailure = stages.some(({ status }) =>
        status === "INFRASTRUCTURE_FAILED" || status === "CANCELLED",
      );
      const hardFailure = stages.some(({ hardGate, status }) => hardGate && status !== "PASSED");
      const hasIntegrationImpact = stages.some(({ diagnostics }) =>
        diagnostics.some(({ scope }) => scope === "INTEGRATION_IMPACT"));
      let review: CodeReviewReport | undefined;
      if (!hardFailure && !infrastructureFailure && (!generation.skip_ai_review || hasIntegrationImpact)) {
        this.transition(generation.id, generation.session_id, "REVIEWING", "CODE_REVIEWING", runId);
        generation = this.database.getGeneration(generationId)!;
        review = await this.reviewer.review(generation, runId, stages, signal);
        if (signal.aborted) return;
      }
      const aiReviewSkipped = Boolean(generation.skip_ai_review && !review);
      const decision = evaluateQualityGates(stages, review, [], aiReviewSkipped);
      const reviewerInfrastructureFailure = review?.status === "INFRASTRUCTURE_FAILED";
      infrastructureFailure = infrastructureFailure || reviewerInfrastructureFailure;
      const needsRepair = !decision.hardGatePassed || decision.softFailures.length > 0;
      const firstUnblockedFailure = hasFirstUnblockedFailure(stages, previousStageRuns);
      const repairDecision = nextRepairDecision(
        generation.repair_round,
        needsRepair,
        infrastructureFailure,
        firstUnblockedFailure,
      );
      let repairFailureCode: "REPAIR_NO_EFFECT" | "REPAIR_PROTOCOL_INVALID" | undefined;
      if (repairDecision.repair && this.repair) {
        this.events.publish(generation.session_id, {
          type: "generation.stage_changed",
          data: { generationId: generation.id, state: "CODE_REPAIRING", runId },
        });
        const repairResult = await this.repair.attempt(generation, repairDecision.nextRound, runId, stages, review, signal);
        if (signal.aborted) return;
        if (repairResult.repaired) {
          const repairedAt = new Date().toISOString();
          this.database.db.prepare(`
            UPDATE agent_verification_run SET status = 'FAILED', hard_gate_passed = ?,
              soft_gate_passed = ?, stage_results_json = ?, completed_at = ? WHERE id = ?
          `).run(
            decision.hardGatePassed ? 1 : 0,
            decision.softFailures.length ? 0 : 1,
            JSON.stringify(stages),
            repairedAt,
            runId,
          );
          setImmediate(() => this.start(generationId, "REPAIR"));
          return;
        }
        repairFailureCode = repairResult.failureCode;
        infrastructureFailure = infrastructureFailure || repairResult.infrastructureFailure;
        generation = this.database.getGeneration(generationId) || generation;
      }
      const now = new Date().toISOString();
      const report: GenerationQualityReport = {
        generationId,
        revision: generation.generation_revision,
        pipelineState: decision.hardGatePassed ? "PASSED" : "FAILED",
        repairRound: generation.repair_round,
        maxRepairRounds: 3,
        unblockExtensionUsed: generation.repair_round > 3,
        stages,
        resolvedDiagnostics,
        review,
        repairAttempts: this.repair?.history(generationId),
        aiReviewSkipped,
        hardGatePassed: decision.hardGatePassed,
        overrideRequired: decision.overrideRequired,
        canWrite: decision.canWrite,
        updatedAt: now,
      };
      const finalGenerationStatus = decision.hardGatePassed ? "REVIEW" : "FAILED";
      const finalSessionState = decision.hardGatePassed ? "CODE_REVIEW" : "CODE_PIPELINE_FAILED";
      const finalErrorCode = repairFailureCode
        || (infrastructureFailure ? "AGENT_VERIFICATION_INFRASTRUCTURE_FAILED"
          : decision.hardGatePassed ? null : "AGENT_QUALITY_HARD_GATE_FAILED");
      const finalErrorMessage = repairFailureCode === "REPAIR_NO_EFFECT"
        ? "Repair reported completion without changing staged files."
        : repairFailureCode === "REPAIR_PROTOCOL_INVALID"
          ? "Repair changes were not accepted because the per-diagnostic completion report was invalid."
          : decision.hardGatePassed ? null : "Generated code did not pass the required quality gates.";
      this.database.transaction(() => {
        this.database.db.prepare(`
          UPDATE agent_verification_run SET status = ?, hard_gate_passed = ?,
            soft_gate_passed = ?, stage_results_json = ?, completed_at = ?
          WHERE id = ?
        `).run(
          infrastructureFailure ? "INFRASTRUCTURE_FAILED" : hardFailure ? "FAILED" : "PASSED",
          decision.hardGatePassed ? 1 : 0,
          decision.softFailures.length ? 0 : 1,
          JSON.stringify(stages),
          now,
          runId,
        );
        this.database.db.prepare(`
          UPDATE agent_code_generation SET status = ?, quality_revision = ?,
            latest_verification_run_id = ?, latest_review_id = ?, quality_report_json = ?,
            hard_gate_passed = ?, override_required = ?, can_write = ?,
            last_error_code = ?, last_error_message = ?, updated_at = ?
          WHERE id = ? AND generation_revision = ?
        `).run(
          finalGenerationStatus,
          generation!.generation_revision,
          runId,
          review?.reviewId || null,
          JSON.stringify(report),
          decision.hardGatePassed ? 1 : 0,
          decision.overrideRequired ? 1 : 0,
          decision.canWrite ? 1 : 0,
          finalErrorCode,
          finalErrorMessage,
          now,
          generation!.id,
          generation!.generation_revision,
        );
        this.database.db.prepare(`
          UPDATE agent_session SET state = ?, row_version = row_version + 1,
            last_error_code = ?, last_error_message = ?, updated_at = ?
          WHERE id = ?
        `).run(
          finalSessionState,
          finalErrorCode || (decision.hardGatePassed ? null : "AGENT_CODE_PIPELINE_FAILED"),
          finalErrorMessage,
          now,
          generation!.session_id,
        );
      });
      this.events.publish(generation.session_id, {
        type: "generation.quality_completed",
        data: report,
      });
      this.events.publish(generation.session_id, {
        type: "workflow.state_changed",
        data: { state: finalSessionState },
      });
    } catch (error) {
      const failedGeneration = this.database.getGeneration(generationId);
      if (!signal.aborted && failedGeneration) this.failRun(failedGeneration, runId, error);
    } finally {
      if (this.activeRuns.get(generationId) === controller) this.activeRuns.delete(generationId);
    }
  }

  private publishVerifyStage(
    sessionId: string,
    generationId: string,
    runId: string,
    stage: QualityStageName,
    status: QualityStageResult["status"],
    hardGate: boolean,
  ): void {
    this.events.publish(sessionId, {
      type: "generation.verify_stage",
      data: { generationId, runId, stage, status, hardGate },
    });
  }

  private transition(
    generationId: string,
    sessionId: string,
    generationStatus: "REVIEWING",
    sessionState: "CODE_REVIEWING",
    runId: string,
  ): void {
    const now = new Date().toISOString();
    this.database.transaction(() => {
      this.database.db.prepare("UPDATE agent_code_generation SET status = ?, updated_at = ? WHERE id = ?")
        .run(generationStatus, now, generationId);
      this.database.db.prepare("UPDATE agent_session SET state = ?, row_version = row_version + 1, updated_at = ? WHERE id = ?")
        .run(sessionState, now, sessionId);
    });
    this.events.publish(sessionId, {
      type: "generation.stage_changed",
      data: { generationId, state: sessionState, runId },
    });
  }

  private failRun(generation: NonNullable<ReturnType<DatabaseService["getGeneration"]>>, runId: string, error: unknown): void {
    const message = error instanceof Error ? error.message : String(error);
    const now = new Date().toISOString();
    this.database.transaction(() => {
      this.database.db.prepare(`
        UPDATE agent_verification_run SET status = 'INFRASTRUCTURE_FAILED',
          error_code = 'AGENT_VERIFICATION_INFRASTRUCTURE_FAILED',
          error_message = ?, completed_at = ? WHERE id = ?
      `).run(message, now, runId);
      this.database.db.prepare(`
        UPDATE agent_code_generation SET status = 'FAILED', can_write = 0,
          last_error_code = 'AGENT_VERIFICATION_INFRASTRUCTURE_FAILED',
          last_error_message = ?, updated_at = ? WHERE id = ?
      `).run(message, now, generation.id);
      this.database.db.prepare(`
        UPDATE agent_session SET state = 'CODE_PIPELINE_FAILED', row_version = row_version + 1,
          last_error_code = 'AGENT_VERIFICATION_INFRASTRUCTURE_FAILED',
          last_error_message = ?, updated_at = ? WHERE id = ?
      `).run(message, now, generation.session_id);
    });
  }
}

function fakeExecutor(): VerificationCommandExecutor | undefined {
  if (process.env.AGENT_FAKE_PI !== "true") return undefined;
  return async () => ({
    exitCode: 0,
    stdout: "controlled verification passed",
    stderr: "",
    timedOut: false,
    cancelled: false,
  });
}

function attachRunToDiagnostics(stages: QualityStageResult[], verificationRunId: string): void {
  for (const stage of stages) {
    stage.diagnostics = stage.diagnostics.map((item) => ({
      ...(item.diagnosticId && item.stage ? item : qualityDiagnostic(stage.stage, item)),
      verificationRunId,
    }));
  }
}

const COMMAND_STAGE_ORDER: readonly VerificationCommandStage[] = [
  "BACKEND_COMPILE",
  "FRONTEND_TYPECHECK",
  "FRONTEND_BUILD",
  "BACKEND_TESTS",
  "FRONTEND_TESTS",
];
const HARD_COMMAND_STAGES = new Set<VerificationCommandStage>([
  "BACKEND_COMPILE",
  "FRONTEND_TYPECHECK",
  "FRONTEND_BUILD",
]);
const SOFT_COMMAND_STAGES = new Set<VerificationCommandStage>(["BACKEND_TESTS", "FRONTEND_TESTS"]);

// Mirrors the worker's command stages so skipped command stages carry the same
// hard-gate flags as when they actually run.
const SKIPPABLE_COMMAND_STAGES: ReadonlyArray<{ stage: VerificationCommandStage; hardGate: boolean }> =
  COMMAND_STAGE_ORDER.map((stage) => ({ stage, hardGate: HARD_COMMAND_STAGES.has(stage) }));

export function selectRepairVerificationStages(
  previousStages: QualityStageResult[],
  changedFiles: string[],
): VerificationCommandStage[] {
  const selected = new Set<VerificationCommandStage>();
  const previousByStage = new Map(previousStages.map((stage) => [stage.stage, stage]));
  const impact = classifyRepairImpact(changedFiles);
  if (impact.fullHard) {
    for (const stage of HARD_COMMAND_STAGES) selected.add(stage);
  } else {
    if (impact.backend) selected.add("BACKEND_COMPILE");
    if (impact.frontend) {
      selected.add("FRONTEND_TYPECHECK");
      selected.add("FRONTEND_BUILD");
    }
  }

  for (const stage of HARD_COMMAND_STAGES) {
    if (previousByStage.get(stage)?.status !== "PASSED") selected.add(stage);
  }
  for (const stage of SOFT_COMMAND_STAGES) {
    const previous = previousByStage.get(stage);
    const wasUnverified = !previous || (previous.status !== "PASSED" && Boolean(previous.blockedBy?.length));
    const failedDirectly = previous?.status === "FAILED" || previous?.status === "CANCELLED" || previous?.status === "INFRASTRUCTURE_FAILED";
    if (wasUnverified || failedDirectly) selected.add(stage);
  }

  if (!selected.size) {
    for (const stage of HARD_COMMAND_STAGES) selected.add(stage);
  }
  return COMMAND_STAGE_ORDER.filter((stage) => selected.has(stage));
}

function classifyRepairImpact(changedFiles: string[]): { backend: boolean; frontend: boolean; fullHard: boolean } {
  if (!changedFiles.length) return { backend: true, frontend: true, fullHard: true };
  let backend = false;
  let frontend = false;
  for (const rawPath of changedFiles) {
    const path = rawPath.replace(/\\/g, "/");
    const buildConfig = /(^|\/)(pom\.xml|package(?:-lock)?\.json|tsconfig(?:\.[^/]*)?\.json|vite\.config\.[tj]s)$/.test(path);
    if (buildConfig || path.startsWith("shared/")) return { backend: true, frontend: true, fullHard: true };
    if (path.startsWith("backend/") && path.endsWith(".java")) {
      backend = true;
      continue;
    }
    if (path.startsWith("frontend/src/") && /\.(vue|ts|tsx|js|jsx)$/.test(path)) {
      frontend = true;
      continue;
    }
    return { backend: true, frontend: true, fullHard: true };
  }
  return { backend, frontend, fullHard: backend && frontend };
}

function mergeVerificationStages(
  staticResult: QualityStageResult,
  currentCommandStages: QualityStageResult[],
  previousStages?: QualityStageResult[],
  selectedStages?: VerificationCommandStage[],
): QualityStageResult[] {
  if (!selectedStages) return [staticResult, ...currentCommandStages];
  const currentByStage = new Map(currentCommandStages.map((stage) => [stage.stage, stage]));
  const previousByStage = new Map((previousStages || []).map((stage) => [stage.stage, stage]));
  return [
    staticResult,
    ...COMMAND_STAGE_ORDER.map((stage) => {
      const current = currentByStage.get(stage);
      if (current) return current;
      const previous = previousByStage.get(stage);
      if (previous?.status === "PASSED") return previous;
      return notReverifiedStage(stage, selectedStages);
    }),
  ];
}

function notReverifiedStage(stage: VerificationCommandStage, selectedStages: VerificationCommandStage[]): QualityStageResult {
  const diagnostic = qualityDiagnostic(stage, {
    code: "QUALITY_STAGE_NOT_REVERIFIED",
    message: `${stage} was not selected for this targeted repair verification and has no prior passing result.`,
    hardGate: HARD_COMMAND_STAGES.has(stage),
    expected: "A stage can be reused only when the previous current result passed for the same staging revision lineage.",
    repairHint: "Run a broader verification scope before writing if this stage remains unverified.",
    repairability: "UNKNOWN",
  });
  return {
    stage,
    status: "SKIPPED",
    hardGate: HARD_COMMAND_STAGES.has(stage),
    summary: `Not selected for targeted repair verification. Selected stages: ${selectedStages.join(", ")}.`,
    diagnostics: [{ ...diagnostic, classification: "BLOCKED" }],
  };
}

/** When static validation fails, the five command stages cannot run. Emit them
 * as SKIPPED (blocked by STATIC_VALIDATION) so the report still covers all six
 * stages and downstream gating/review logic sees every stage. */
function skippedCommandStages(): QualityStageResult[] {
  return SKIPPABLE_COMMAND_STAGES.map(({ stage, hardGate }) => {
    const diagnostic = qualityDiagnostic(stage, {
      code: "QUALITY_STAGE_BLOCKED",
      message: `${stage} was skipped because STATIC_VALIDATION did not pass.`,
      hardGate,
      expected: "STATIC_VALIDATION must pass before command verification can run.",
      repairHint: "Resolve the static validation findings; command stages run automatically on the next verification.",
      repairability: "UNKNOWN",
    });
    return {
      stage,
      status: "SKIPPED",
      hardGate,
      summary: "Blocked by STATIC_VALIDATION.",
      diagnostics: [{ ...diagnostic, classification: "BLOCKED" }],
      blockedBy: ["STATIC_VALIDATION"],
    };
  });
}

export function classifyDiagnostics(stages: QualityStageResult[], previousStages?: QualityStageResult[]): void {
  const previousFingerprints = new Set(
    (previousStages || []).flatMap(({ diagnostics }) => diagnostics)
      .map(({ fingerprint, diagnosticId }) => fingerprint || diagnosticId)
      .filter((value): value is string => Boolean(value)),
  );
  for (const stage of stages) {
    stage.diagnostics = stage.diagnostics.map((diagnostic) => ({
      ...diagnostic,
      classification: diagnostic.classification === "BLOCKED"
        ? "BLOCKED"
        : previousFingerprints.has(diagnostic.fingerprint || diagnostic.diagnosticId || "")
          ? "PERSISTING"
          : "NEW",
    }));
  }
}

export function collectResolvedDiagnostics(
  stages: QualityStageResult[],
  previousStages?: QualityStageResult[],
) {
  const currentFingerprints = new Set(stages.flatMap(({ diagnostics }) => diagnostics)
    .map(({ fingerprint, diagnosticId }) => fingerprint || diagnosticId)
    .filter((value): value is string => Boolean(value)));
  return (previousStages || []).flatMap(({ diagnostics }) => diagnostics)
    .filter(({ classification, fingerprint, diagnosticId }) => classification !== "BLOCKED"
      && !currentFingerprints.has(fingerprint || diagnosticId || ""))
    .map((diagnostic) => ({ ...diagnostic, classification: "RESOLVED" as const }));
}

export function hasFirstUnblockedFailure(
  stages: QualityStageResult[],
  previousRuns: QualityStageResult[][],
): boolean {
  return stages.some((current) => {
    if (current.status !== "FAILED") return false;
    const history = previousRuns
      .map((run) => run.find(({ stage }) => stage === current.stage))
      .filter((stage): stage is QualityStageResult => Boolean(stage));
    return history.length > 0
      && history.every(({ status, blockedBy }) => status === "SKIPPED" && Boolean(blockedBy?.length));
  });
}
