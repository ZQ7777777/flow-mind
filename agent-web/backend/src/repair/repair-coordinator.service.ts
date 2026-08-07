import { Inject, Injectable } from "@nestjs/common";
import { existsSync, readFileSync } from "node:fs";
import { isAbsolute, relative, resolve } from "node:path";
import { randomUUID } from "node:crypto";
import type {
  BusinessRequirement,
  CodeReviewIssue,
  CodeReviewReport,
  QualityDiagnostic,
  QualityStageResult,
  RepairAttemptSummary,
  RepairResolution,
} from "@flowmind/agent-contracts";
import { DatabaseService, type GenerationRow } from "../persistence/database.service.js";
import { EventBusService } from "../workflow/event-bus.service.js";
import { PiAdapterService, type GenerationPiCallbacks } from "../pi/pi-adapter.service.js";
import { deriveGenerationSpec } from "../generation/generation-spec.js";
import { generationContract, StagingService } from "../generation/staging.service.js";
import { sha256, TargetContractService } from "../generation/target-contract.service.js";
import { apiReferencePaths } from "../generation/target-contract.service.js";
import type { GenerationApiReferences } from "../pi/generation-prompt.js";
import { qualityDiagnostic, sanitizeDiagnosticEvidence } from "../verification/quality-diagnostic.js";

export interface RepairAttemptResult {
  repaired: boolean;
  infrastructureFailure: boolean;
  noEffect?: boolean;
  failureCode?: "REPAIR_NO_EFFECT" | "REPAIR_PROTOCOL_INVALID";
  changedFiles?: string[];
}

interface RepairPromptContext {
  generationId: string;
  generationRevision: number;
  verificationRunId: string;
}

interface PreviousRepairAttempt {
  changedFiles: string[];
  resolvedDiagnosticIds: string[];
  unresolvedDiagnosticIds: string[];
}

@Injectable()
export class RepairCoordinatorService {
  constructor(
    @Inject(DatabaseService) private readonly database: DatabaseService,
    @Inject(TargetContractService) private readonly targets: TargetContractService,
    @Inject(StagingService) private readonly staging: StagingService,
    @Inject(PiAdapterService) private readonly pi: PiAdapterService,
    @Inject(EventBusService) private readonly events: EventBusService,
  ) {}

  async attempt(
    generation: GenerationRow,
    nextRound: number,
    verificationRunId: string,
    stages: QualityStageResult[],
    review?: CodeReviewReport,
  ): Promise<RepairAttemptResult> {
    if (!generation.pi_session_file) return { repaired: false, infrastructureFailure: true };
    const previousRound = generation.repair_round;
    const now = new Date().toISOString();
    this.database.transaction(() => {
      this.database.db.prepare(`
        UPDATE agent_code_generation SET status = 'REPAIRING', repair_round = ?,
          can_write = 0, updated_at = ? WHERE id = ? AND generation_revision = ?
      `).run(nextRound, now, generation.id, generation.generation_revision);
      this.database.db.prepare(`
        UPDATE agent_session SET state = 'CODE_REPAIRING', row_version = row_version + 1,
          updated_at = ? WHERE id = ?
      `).run(now, generation.session_id);
    });
    const contract = generationContract(generation);
    const requirement = JSON.parse(generation.requirement_snapshot_json) as BusinessRequirement;
    const spec = deriveGenerationSpec(requirement, contract);
    const target = { targetRoot: generation.target_root, contract };
    const referencePaths = apiReferencePaths(contract);
    const apiReferences: GenerationApiReferences = {
      platformRuntime: this.targets.readReference(target, referencePaths.platformRuntime, generation.session_id),
      trustedUserContext: this.targets.readReference(target, referencePaths.trustedUserContext, generation.session_id),
    };
    const manifestPaths = this.staging.list(generation);
    const currentStages = attachVerificationContext(stages, verificationRunId, manifestPaths);
    const currentDiagnostics = currentStages.flatMap(({ diagnostics }) => diagnostics);
    const currentReview = review ? { ...review, issues: normalizeReviewIssues(review.issues, manifestPaths) } : undefined;
    const allDiagnosticIds = [
      ...currentDiagnostics.map(({ diagnosticId }) => diagnosticId),
      ...(currentReview?.issues.map(({ diagnosticId }) => diagnosticId) || []),
    ].filter((value): value is string => Boolean(value));
    const actionableDiagnostics = [
      ...currentDiagnostics
      .filter(({ repairability }) => repairability === "CODE_ACTIONABLE" || repairability === "UNKNOWN" || !repairability)
      .map(({ diagnosticId, relativePath }) => ({ diagnosticId, relativePath })),
      ...(currentReview?.issues
        .filter(({ repairability }) => repairability !== "INFRASTRUCTURE" && repairability !== "PROTECTED_FILE")
        .map(({ diagnosticId, relativePath }) => ({ diagnosticId, relativePath })) || []),
    ].filter((item): item is { diagnosticId: string; relativePath: string | undefined } => Boolean(item.diagnosticId));
    const actionableById = new Map(actionableDiagnostics.map((item) => [item.diagnosticId, item]));
    const beforeContents = new Map(this.staging.list(generation).map((path) => [path, this.staging.read(generation, path).content]));
    const beforeHashes = new Map([...beforeContents].map(([path, content]) => [path, sha256(content)]));
    const previousAttempt = this.previousAttempt(generation.id, allDiagnosticIds);
    let reported = false;
    let reportedFiles: string[] = [];
    let resolutions: RepairResolution[] = [];
    let modelError: Error | undefined;
    const callbacks: GenerationPiCallbacks = {
      requirement,
      contract,
      spec,
      onEvent: (type, data) => this.events.publish(generation.session_id, { type, data }),
      onError: (_code, message) => { modelError = new Error(message); },
      readReference: (path) => this.targets.readReference(target, path, generation.session_id),
      readStaged: (path) => this.staging.read(this.requiredRepairing(generation.id), path).content,
      listStaged: () => this.staging.list(this.requiredRepairing(generation.id)),
      writeStaged: (path, content) => this.staging.writeDuringRepair(this.requiredRepairing(generation.id), path, content),
      deleteStaged: () => { throw new Error("Repair cannot delete Manifest files."); },
      readVerificationDiagnostic: (diagnosticId) => this.readVerificationDiagnostic(
        generation.id,
        verificationRunId,
        currentStages,
        diagnosticId,
      ),
      reportComplete: (files, reportedResolutions = []) => {
        reportedFiles = files;
        resolutions = reportedResolutions;
        reported = true;
      },
    };
    try {
      await this.pi.runRepair(
        generation.id,
        generation.staging_dir,
        generation.pi_session_file,
        buildRepairPrompt(nextRound, currentStages, currentReview, apiReferences, {
          generationId: generation.id,
          generationRevision: generation.generation_revision,
          verificationRunId,
        }, previousAttempt),
        callbacks,
      );
      if (modelError) throw modelError;
      if (!reported) throw new Error("repair session ended without report_repair_complete");
      const current = this.requiredRepairing(generation.id);
      const actualFiles = this.staging.list(current).sort();
      const changedFiles = actualFiles.filter((path) => beforeHashes.get(path) !== sha256(this.staging.read(current, path).content));
      const reportedSet = [...new Set(reportedFiles)].sort();
      const protocolValid = sameStringSet(reportedSet, actualFiles)
        && validateRepairReport(actionableById, changedFiles, resolutions);
      if (!changedFiles.length || !protocolValid) {
        const failureCode = changedFiles.length ? "REPAIR_PROTOCOL_INVALID" : "REPAIR_NO_EFFECT";
        if (changedFiles.length) {
          for (const [path, content] of beforeContents) this.staging.writeDuringRepair(current, path, content);
        }
        this.recordAttempt(
          generation.id,
          verificationRunId,
          nextRound,
          allDiagnosticIds,
          changedFiles,
          resolutions,
          "NO_EFFECT",
          failureCode,
        );
        return { repaired: false, infrastructureFailure: false, noEffect: true, failureCode, changedFiles };
      }
      this.staging.completeRepair(current, reportedFiles);
      this.recordAttempt(generation.id, verificationRunId, nextRound, allDiagnosticIds, changedFiles, resolutions, "CHANGED");
      return { repaired: true, infrastructureFailure: false, changedFiles };
    } catch {
      const failedAt = new Date().toISOString();
      const repairing = this.database.getGeneration(generation.id);
      if (repairing?.status === "REPAIRING") {
        for (const [path, content] of beforeContents) this.staging.writeDuringRepair(repairing, path, content);
      }
      this.recordAttempt(generation.id, verificationRunId, nextRound, allDiagnosticIds, [], resolutions, "INFRASTRUCTURE_FAILED");
      this.database.transaction(() => {
        this.database.db.prepare(`
          UPDATE agent_code_generation SET status = 'VERIFYING', repair_round = ?,
            last_error_code = 'AGENT_REPAIR_INFRASTRUCTURE_FAILED', updated_at = ? WHERE id = ?
        `).run(previousRound, failedAt, generation.id);
        this.database.db.prepare(`
          UPDATE agent_session SET state = 'CODE_VERIFYING', row_version = row_version + 1,
            last_error_code = 'AGENT_REPAIR_INFRASTRUCTURE_FAILED', updated_at = ? WHERE id = ?
        `).run(failedAt, generation.session_id);
      });
      return { repaired: false, infrastructureFailure: true };
    }
  }

  history(generationId: string): RepairAttemptSummary[] {
    return (this.database.db.prepare(`
      SELECT round, verification_run_id, changed_files_json, resolutions_json,
        diagnostic_ids_json, outcome, failure_code, created_at
      FROM agent_repair_attempt WHERE generation_id = ? ORDER BY round, created_at
    `).all(generationId) as Array<Record<string, unknown>>).map((row) => ({
      round: Number(row.round),
      verificationRunId: String(row.verification_run_id),
      changedFiles: JSON.parse(String(row.changed_files_json)) as string[],
      resolutions: JSON.parse(String(row.resolutions_json)) as RepairResolution[],
      diagnosticIds: JSON.parse(String(row.diagnostic_ids_json)) as string[],
      outcome: row.outcome as RepairAttemptSummary["outcome"],
      failureCode: row.failure_code as RepairAttemptSummary["failureCode"] || undefined,
      createdAt: String(row.created_at),
    }));
  }

  private previousAttempt(generationId: string, currentDiagnosticIds: string[]): PreviousRepairAttempt | undefined {
    const previous = this.history(generationId).at(-1);
    if (!previous) return undefined;
    const currentIds = new Set(currentDiagnosticIds);
    return {
      changedFiles: previous.changedFiles,
      resolvedDiagnosticIds: previous.diagnosticIds.filter((id) => !currentIds.has(id)),
      unresolvedDiagnosticIds: previous.diagnosticIds.filter((id) => currentIds.has(id)),
    };
  }

  private recordAttempt(
    generationId: string,
    verificationRunId: string,
    round: number,
    diagnosticIds: string[],
    changedFiles: string[],
    resolutions: RepairResolution[],
    outcome: RepairAttemptSummary["outcome"],
    failureCode?: RepairAttemptSummary["failureCode"],
  ): void {
    const previousIds = new Set(this.history(generationId).at(-1)?.diagnosticIds || []);
    this.database.db.prepare(`
      INSERT INTO agent_repair_attempt (
        id, generation_id, verification_run_id, round, diagnostic_ids_json,
        changed_files_json, resolutions_json, outcome, failure_code, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      `repair_${randomUUID()}`,
      generationId,
      verificationRunId,
      round,
      JSON.stringify(diagnosticIds),
      JSON.stringify(changedFiles),
      JSON.stringify(resolutions),
      outcome,
      failureCode || null,
      new Date().toISOString(),
    );
    const generation = this.database.getGeneration(generationId);
    if (generation) {
      this.events.publish(generation.session_id, {
        type: "generation.repair_attempted",
        data: {
          generationId,
          verificationRunId,
          round,
          outcome,
          diagnosticCount: diagnosticIds.length,
          repeatedDiagnosticCount: diagnosticIds.filter((id) => previousIds.has(id)).length,
          changedFileCount: changedFiles.length,
          reportedResolvedCount: resolutions.filter(({ status }) => status === "RESOLVED").length,
        },
      });
    }
  }

  private readVerificationDiagnostic(
    generationId: string,
    verificationRunId: string,
    stages: QualityStageResult[],
    diagnosticId: string,
  ): { diagnostic: QualityDiagnostic; stdoutExcerpt?: string; stderrExcerpt?: string } {
    const stage = stages.find(({ diagnostics }) => diagnostics.some((item) => item.diagnosticId === diagnosticId));
    const diagnostic = stage?.diagnostics.find((item) => item.diagnosticId === diagnosticId);
    if (!stage || !diagnostic || diagnostic.verificationRunId !== verificationRunId) {
      throw new Error("Diagnostic is not part of the current verification run.");
    }
    if (!stage.logPath) return { diagnostic };
    return { diagnostic, ...readVerificationLogExcerpt(this.database.dataDir, generationId, stage.logPath) };
  }

  private requiredRepairing(generationId: string): GenerationRow {
    const generation = this.database.getGeneration(generationId);
    if (!generation || generation.status !== "REPAIRING") throw new Error("generation is no longer repairable");
    return generation;
  }
}

export function buildRepairPrompt(
  round: number,
  stages: QualityStageResult[],
  review?: CodeReviewReport,
  apiReferences?: GenerationApiReferences,
  context: RepairPromptContext = {
    generationId: "legacy-generation",
    generationRevision: 0,
    verificationRunId: "legacy-verification",
  },
  previousAttempt?: PreviousRepairAttempt,
): string {
  const failedStages = stages.filter(({ status }) => !["PASSED", "SKIPPED"].includes(status));
  const diagnostics = boundRepairDiagnostics(failedStages.flatMap(({ diagnostics }) => diagnostics));
  const brief = {
    ...context,
    round,
    actionableDiagnostics: diagnostics.filter(({ repairability }) =>
      repairability !== "INFRASTRUCTURE" && repairability !== "PROTECTED_FILE"),
    blockedDiagnostics: diagnostics.filter(({ repairability }) =>
      repairability === "INFRASTRUCTURE" || repairability === "PROTECTED_FILE"),
    reviewIssues: review?.issues.filter(({ repairability }) =>
      repairability !== "INFRASTRUCTURE" && repairability !== "PROTECTED_FILE") || [],
    blockedReviewIssues: review?.issues.filter(({ repairability }) =>
      repairability === "INFRASTRUCTURE" || repairability === "PROTECTED_FILE") || [],
    previousAttempt,
    repeatedDiagnostics: previousAttempt
      ? diagnostics.filter(({ diagnosticId }) => diagnosticId && previousAttempt.unresolvedDiagnosticIds.includes(diagnosticId))
        .map(({ diagnosticId, code, relativePath, actual, expected, evidence, repairHint, acceptedForms }) => ({
          diagnosticId,
          code,
          relativePath,
          actual,
          expected,
          evidence,
          repairHint,
          acceptedForms,
        }))
      : [],
  };
  return [
    `Repair round ${round} of 3.`,
    "This Repair Brief is the only authoritative diagnostic state for the current verification run; it supersedes historical errors in the session.",
    "Modify only existing Manifest-managed staged files. Do not add or delete files.",
    "Resolve all failed hard and soft quality stages and every reviewer issue, preserve the confirmed requirement, then call report_repair_complete.",
    "BACKEND_TESTS and FRONTEND_TESTS are actionable failures even though they are soft gates; do not stop after compilation, typecheck, or build passes.",
    "Before editing, read every referenced staged file. If evidence is insufficient, call read_verification_diagnostic with its diagnosticId.",
    "Follow expected, repairHint, and acceptedForms exactly. Make the smallest relevant changes and never weaken tests.",
    "If repeatedDiagnostics is non-empty, compare it with previousAttempt.changedFiles and fix the explicitly remaining subchecks; do not repeat the same syntactic guess.",
    "Report one RESOLVED resolution for every actionable diagnostic and actionable reviewer diagnostic. Do not claim blocked infrastructure or protected-file findings are resolved.",
    "Before editing, use the authoritative references below. Never guess Java packages, types, getters, or setters.",
    `Authoritative platform runtime API reference:\n${apiReferences?.platformRuntime || "Unavailable in legacy prompt test."}`,
    `Authoritative trusted user context source:\n${apiReferences?.trustedUserContext || "Unavailable in legacy prompt test."}`,
    `Current Repair Brief:\n${JSON.stringify(brief)}`,
  ].join("\n");
}

function attachVerificationContext(
  stages: QualityStageResult[],
  verificationRunId: string,
  manifestPaths: string[],
): QualityStageResult[] {
  return stages.map((stage) => ({
    ...stage,
    diagnostics: stage.diagnostics.map((item) => {
      const normalized = item.diagnosticId && item.stage ? item : qualityDiagnostic(stage.stage, item);
      return {
        ...normalized,
        relativePath: canonicalManifestPath(normalized.relativePath, manifestPaths),
        verificationRunId,
      };
    }),
  }));
}

function normalizeReviewIssues(issues: CodeReviewIssue[], manifestPaths: string[]): CodeReviewIssue[] {
  return issues.map((issue) => {
    const diagnostic = qualityDiagnostic("STATIC_VALIDATION", {
      code: `REVIEW_${issue.code}`,
      message: issue.message,
      hardGate: issue.severity === "BLOCKING",
      relativePath: canonicalManifestPath(issue.relativePath, manifestPaths),
      line: issue.line,
      evidence: issue.evidence || `${issue.title}: ${issue.message}`,
      repairHint: issue.repairHint || "Address the reviewer finding with the smallest change that preserves the confirmed requirement.",
      repairability: issue.repairability || "CODE_ACTIONABLE",
    });
    return {
      ...issue,
      relativePath: diagnostic.relativePath,
      diagnosticId: issue.diagnosticId || diagnostic.diagnosticId,
      evidence: diagnostic.evidence,
      repairHint: diagnostic.repairHint,
      repairability: diagnostic.repairability,
    };
  });
}

function canonicalManifestPath(path: string | undefined, manifestPaths: string[]): string | undefined {
  if (!path) return undefined;
  const normalized = path.replace(/\\/g, "/").replace(/^\.\//, "");
  if (manifestPaths.includes(normalized)) return normalized;
  const suffixMatches = manifestPaths.filter((candidate) =>
    candidate.endsWith(`/${normalized}`) || candidate.endsWith(`/${normalized.split("/").at(-1)}`));
  return suffixMatches.length === 1 ? suffixMatches[0] : undefined;
}

function validateRepairReport(
  actionableById: Map<string, { relativePath?: string }>,
  changedFiles: string[],
  resolutions: RepairResolution[],
): boolean {
  const actualChanges = new Set(changedFiles);
  const byId = new Map(resolutions.map((resolution) => [resolution.diagnosticId, resolution]));
  if ([...actionableById.keys()].some((id) => !byId.has(id))) return false;
  if (resolutions.some(({ diagnosticId }) => !actionableById.has(diagnosticId))) return false;
  return resolutions.every((resolution) => {
    if (!resolution.explanation.trim()) return false;
    if (resolution.changedFiles.some((path) => !actualChanges.has(path))) return false;
    const referencedPath = actionableById.get(resolution.diagnosticId)?.relativePath;
    const hasRelatedChange = referencedPath
      ? resolution.changedFiles.includes(referencedPath)
      : resolution.changedFiles.some((path) => actualChanges.has(path));
    return resolution.status === "RESOLVED" && hasRelatedChange;
  });
}

function sameStringSet(left: string[], right: string[]): boolean {
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

function boundRepairDiagnostics(diagnostics: QualityDiagnostic[]): QualityDiagnostic[] {
  const evidenceLimit = Math.max(300, Math.floor(24_000 / Math.max(1, diagnostics.length)));
  const tail = (value: string | undefined, limit: number) => value && value.length > limit
    ? `[earlier content omitted]\n${value.slice(-limit)}`
    : value;
  return diagnostics.map((item) => ({
    ...item,
    message: tail(item.message, 1_000) || item.message,
    actual: tail(item.actual, 1_000),
    expected: tail(item.expected, 1_000),
    evidence: tail(item.evidence, evidenceLimit),
    repairHint: tail(item.repairHint, 1_000),
  }));
}

export function readVerificationLogExcerpt(
  dataDir: string,
  generationId: string,
  logPath: string,
): { stdoutExcerpt?: string; stderrExcerpt?: string } {
  const allowedRoot = resolve(dataDir, "verification-logs", generationId);
  const candidate = resolve(logPath);
  const rel = relative(allowedRoot, candidate);
  if (rel === ".." || rel.startsWith("../") || rel.startsWith("..\\") || isAbsolute(rel) || !existsSync(candidate)) {
    throw new Error("Verification log is outside the current generation.");
  }
  const excerpt = sanitizeDiagnosticEvidence(readFileSync(candidate, "utf8").slice(-12_000));
  const [stdoutPart, stderrPart] = excerpt.split("[stderr]\n", 2);
  return {
    stdoutExcerpt: stdoutPart.replace(/^\[stdout\]\n/, "") || undefined,
    stderrExcerpt: stderrPart || undefined,
  };
}
