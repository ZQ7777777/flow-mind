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
  previousDiagnosticIds: string[];
  outcome?: RepairAttemptSummary["outcome"];
  failureCode?: RepairAttemptSummary["failureCode"];
}

interface RelatedSourceFile {
  relativePath: string;
  content: string;
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
    signal?: AbortSignal,
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
      .filter(({ repairability, derivedFrom, classification }) => !derivedFrom?.length && classification !== "BLOCKED"
        && (repairability === "CODE_ACTIONABLE" || repairability === "UNKNOWN" || !repairability))
      .filter(isCurrentGenerationRepairDiagnostic)
      .map(({ diagnosticId, relativePath }) => ({ diagnosticId, relativePath })),
      ...(currentReview?.issues
        .filter(({ repairability }) => repairability !== "INFRASTRUCTURE" && repairability !== "PROTECTED_FILE")
        .map(({ diagnosticId, relativePath }) => ({ diagnosticId, relativePath })) || []),
    ].filter((item): item is { diagnosticId: string; relativePath: string | undefined } => Boolean(item.diagnosticId));
    const actionableById = new Map(actionableDiagnostics.map((item) => [item.diagnosticId, item]));
    const beforeContents = new Map(this.staging.list(generation).map((path) => [path, this.staging.read(generation, path).content]));
    const beforeHashes = new Map([...beforeContents].map(([path, content]) => [path, sha256(content)]));
    const previousAttempt = this.previousAttempt(generation.id, allDiagnosticIds);
    const relatedSource = relatedSourceFiles(beforeContents, currentStages, currentReview);
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
      if (signal?.aborted) throw new Error("quality gate cancelled");
      await this.pi.runRepair(
        generation.id,
        generation.staging_dir,
        generation.pi_session_file,
        buildRepairPrompt(nextRound, currentStages, currentReview, apiReferences, {
          generationId: generation.id,
          generationRevision: generation.generation_revision,
          verificationRunId,
        }, previousAttempt, relatedSource),
        callbacks,
      );
      if (signal?.aborted) throw new Error("quality gate cancelled");
      if (modelError) throw modelError;
      if (!reported) throw new Error("repair session ended without report_repair_complete");
      const current = this.requiredRepairing(generation.id);
      const actualFiles = this.staging.list(current).sort();
      const changedFiles = actualFiles.filter((path) => beforeHashes.get(path) !== sha256(this.staging.read(current, path).content));
      const reportedSet = [...new Set(reportedFiles)].sort();
      const fileSetValid = sameStringSet(reportedSet, actualFiles);
      if (!changedFiles.length || !fileSetValid) {
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
      const protocolValid = validateRepairReport(actionableById, changedFiles, resolutions);
      this.staging.completeRepair(current, reportedFiles);
      this.recordAttempt(
        generation.id,
        verificationRunId,
        nextRound,
        allDiagnosticIds,
        changedFiles,
        protocolValid ? resolutions : [],
        "CHANGED",
      );
      return { repaired: true, infrastructureFailure: false, changedFiles };
    } catch {
      if (signal?.aborted) {
        const current = this.database.getGeneration(generation.id) || generation;
        this.staging.restoreRepairSnapshot(current, beforeContents);
        return { repaired: false, infrastructureFailure: false, changedFiles: [] };
      }
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

  cancel(generationId: string): void {
    this.pi.cancelRepair(generationId);
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
      previousDiagnosticIds: previous.diagnosticIds,
      outcome: previous.outcome,
      failureCode: previous.failureCode,
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
  sourceContext: RelatedSourceFile[] = [],
): string {
  const failedStages = stages.filter(({ status }) => status !== "PASSED");
  const diagnostics = boundRepairDiagnostics(failedStages.flatMap(({ diagnostics }) => diagnostics));
  const actionableDiagnostics = diagnostics.filter(({ repairability, derivedFrom, classification }) =>
    !derivedFrom?.length && classification !== "BLOCKED"
    && repairability !== "INFRASTRUCTURE" && repairability !== "PROTECTED_FILE")
    .filter(isCurrentGenerationRepairDiagnostic);
  const diagnosticDelta = previousAttempt ? repairDiagnosticDelta(previousAttempt, diagnostics) : undefined;
  const ineffectiveRepairSignals = previousAttempt && diagnosticDelta
    ? repairIneffectiveSignals(previousAttempt, diagnosticDelta, diagnostics.length)
    : undefined;
  const brief = {
    ...context,
    round,
    failedStages: failedStages.map(({ stage, status, hardGate, summary, command, exitCode, diagnostics: stageDiagnostics }) => ({
      stage,
      status,
      hardGate,
      summary,
      command,
      exitCode,
      diagnosticIds: stageDiagnostics.map(({ diagnosticId }) => diagnosticId).filter(Boolean),
    })),
    actionableDiagnostics,
    derivedDiagnostics: diagnostics.filter(({ derivedFrom }) => Boolean(derivedFrom?.length)),
    blockedDiagnostics: diagnostics.filter((diagnostic) =>
      diagnostic.classification === "BLOCKED"
      || diagnostic.repairability === "INFRASTRUCTURE"
      || diagnostic.repairability === "PROTECTED_FILE"
      || !isCurrentGenerationRepairDiagnostic(diagnostic)),
    reviewIssues: review?.issues.filter(({ repairability }) =>
      repairability !== "INFRASTRUCTURE" && repairability !== "PROTECTED_FILE") || [],
    blockedReviewIssues: review?.issues.filter(({ repairability }) =>
      repairability === "INFRASTRUCTURE" || repairability === "PROTECTED_FILE") || [],
    relatedSourceFiles: boundRelatedSourceFiles(sourceContext),
    previousAttempt,
    diagnosticDelta,
    ineffectiveRepairSignals,
    repeatedDiagnostics: previousAttempt
      ? diagnostics.filter(({ diagnosticId }) => diagnosticId && previousAttempt.unresolvedDiagnosticIds.includes(diagnosticId))
        .map(({ diagnosticId, stage, code, relativePath, line, column, actual, expected, evidence, command, exitCode, repairHint, acceptedForms }) => ({
          diagnosticId,
          stage,
          code,
          relativePath,
          line,
          column,
          command,
          exitCode,
          actual,
          expected,
          evidence,
          repairHint,
          acceptedForms,
        }))
      : [],
  };
  return [
    round <= 3 ? `Repair round ${round} of 3.` : "Unblock extension repair round 4 (the only permitted extension).",
    "This Repair Brief is the only authoritative diagnostic state for the current verification run; it supersedes historical errors in the session.",
    "Modify only existing Manifest-managed staged files. Do not add or delete files.",
    "Resolve all failed hard and soft quality stages and every reviewer issue, preserve the confirmed requirement, then call report_repair_complete.",
    "BACKEND_TESTS and FRONTEND_TESTS are actionable failures even though they are soft gates; do not stop after compilation, typecheck, or build passes.",
    "For each actionable diagnostic, first read the relatedSourceFiles entry or the referenced staged file, then inspect any directly related types, DTOs, interfaces, tests, or callers before editing.",
    "If a diagnostic reports method not found, constructor mismatch, cannot find symbol, incompatible types, property missing, or argument mismatch, re-check the actual API, DTO, imports, dependencies, and language constraints before editing; do not guess signatures.",
    "Use command, exitCode, file, line, column, code, expected, actual, and evidence as the repair checklist for each diagnostic.",
    "Do not delete business logic, validation, or exception handling; do not comment out code, skip tests, disable rules, change commands, or weaken quality gates.",
    "Follow expected, repairHint, and acceptedForms exactly. Make the smallest relevant changes and never weaken tests.",
    "If repeatedDiagnostics or ineffectiveRepairSignals.requiresRootCauseRecheck is non-empty, compare previousAttempt.changedFiles with the explicitly remaining subchecks, then stop continuing the same edit pattern: re-confirm the root cause from authoritative source, API/type definitions, imports, Maven/npm dependencies, and Java/TypeScript version limits.",
    "Report one RESOLVED resolution for every actionable diagnostic and actionable reviewer diagnostic. This is a repair claim only; the next verification run decides whether the diagnostic is actually RESOLVED. Do not claim blocked infrastructure, protected-file, or derived findings are resolved.",
    "Before editing, use the authoritative references below. Never guess Java packages, types, getters, setters, Vue props, or TypeScript payload shapes.",
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
      const externalScoped = normalized.scope === "PRE_EXISTING" || normalized.scope === "INTEGRATION_IMPACT";
      return {
        ...normalized,
        relativePath: externalScoped
          ? normalized.relativePath?.replace(/\\/g, "/")
          : canonicalManifestPath(normalized.relativePath, manifestPaths),
        verificationRunId,
      };
    }),
  }));
}

function normalizeReviewIssues(issues: CodeReviewIssue[], manifestPaths: string[]): CodeReviewIssue[] {
  return issues.map((issue) => {
    const manifestPath = canonicalManifestPath(issue.relativePath, manifestPaths);
    const externalPath = issue.relativePath && !manifestPath
      ? issue.relativePath.replace(/\\/g, "/")
      : undefined;
    const diagnostic = qualityDiagnostic("STATIC_VALIDATION", {
      code: `REVIEW_${issue.code}`,
      message: issue.message,
      hardGate: issue.severity === "BLOCKING",
      relativePath: manifestPath || externalPath,
      line: issue.line,
      evidence: issue.evidence || `${issue.title}: ${issue.message}`,
      repairHint: issue.repairHint || "Address the reviewer finding with the smallest change that preserves the confirmed requirement.",
      repairability: externalPath ? "PROTECTED_FILE" : issue.repairability || "CODE_ACTIONABLE",
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

function isCurrentGenerationRepairDiagnostic(diagnostic: QualityDiagnostic): boolean {
  return !diagnostic.scope || diagnostic.scope === "CURRENT_GENERATION";
}

function sameStringSet(left: string[], right: string[]): boolean {
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

function relatedSourceFiles(
  beforeContents: Map<string, string>,
  stages: QualityStageResult[],
  review?: CodeReviewReport,
): RelatedSourceFile[] {
  const paths = new Set<string>();
  for (const diagnostic of stages.flatMap(({ diagnostics }) => diagnostics)) {
    if (diagnostic.relativePath) paths.add(diagnostic.relativePath);
  }
  for (const issue of review?.issues || []) {
    if (issue.relativePath) paths.add(issue.relativePath);
  }
  return [...paths]
    .filter((path) => beforeContents.has(path))
    .slice(0, 8)
    .map((relativePath) => ({
      relativePath,
      content: beforeContents.get(relativePath) || "",
    }));
}

function repairDiagnosticDelta(previousAttempt: PreviousRepairAttempt, diagnostics: QualityDiagnostic[]) {
  const currentIds = diagnostics.map(({ diagnosticId }) => diagnosticId).filter((value): value is string => Boolean(value));
  const previousDiagnosticIds = previousAttempt.previousDiagnosticIds || [
    ...previousAttempt.resolvedDiagnosticIds,
    ...previousAttempt.unresolvedDiagnosticIds,
  ];
  const previousIds = new Set(previousDiagnosticIds);
  return {
    previousDiagnosticCount: previousDiagnosticIds.length,
    currentDiagnosticCount: currentIds.length,
    resolvedDiagnosticIds: previousAttempt.resolvedDiagnosticIds,
    persistingDiagnosticIds: previousAttempt.unresolvedDiagnosticIds.filter((id) => currentIds.includes(id)),
    newDiagnosticIds: currentIds.filter((id) => !previousIds.has(id)),
  };
}

function repairIneffectiveSignals(
  previousAttempt: PreviousRepairAttempt,
  delta: ReturnType<typeof repairDiagnosticDelta>,
  currentDiagnosticCount: number,
) {
  const sortedPrevious = [...(previousAttempt.previousDiagnosticIds || [
    ...previousAttempt.resolvedDiagnosticIds,
    ...previousAttempt.unresolvedDiagnosticIds,
  ])].sort();
  const sortedCurrent = [...delta.persistingDiagnosticIds, ...delta.newDiagnosticIds].sort();
  const diagnosticsBasicallySame = sameStringSet(sortedPrevious, sortedCurrent)
    || (currentDiagnosticCount > 0 && delta.persistingDiagnosticIds.length / currentDiagnosticCount >= 0.8);
  const repairHadNoEffectiveDiff = previousAttempt.outcome === "NO_EFFECT" || previousAttempt.changedFiles.length === 0;
  const errorCountIncreased = delta.currentDiagnosticCount > delta.previousDiagnosticCount;
  const persistentDiagnosticsRemain = delta.persistingDiagnosticIds.length > 0;
  return {
    diagnosticsBasicallySame,
    repairHadNoEffectiveDiff,
    persistentDiagnosticIds: delta.persistingDiagnosticIds,
    persistentDiagnosticsRemain,
    errorCountIncreased,
    previousOutcome: previousAttempt.outcome,
    previousFailureCode: previousAttempt.failureCode,
    requiresRootCauseRecheck: diagnosticsBasicallySame || persistentDiagnosticsRemain || repairHadNoEffectiveDiff || errorCountIncreased,
  };
}

function boundRelatedSourceFiles(files: RelatedSourceFile[]): RelatedSourceFile[] {
  return files.map(({ relativePath, content }) => ({
    relativePath,
    content: content.length > 8_000 ? `[earlier content omitted]\n${content.slice(-8_000)}` : content,
  }));
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
