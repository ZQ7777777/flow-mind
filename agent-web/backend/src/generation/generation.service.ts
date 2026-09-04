import { HttpStatus, Inject, Injectable, Optional } from "@nestjs/common";
import { createHash, randomUUID } from "node:crypto";
import { join, resolve } from "node:path";
import { existsSync, realpathSync } from "node:fs";
import type {
  ArtifactManifest,
  BusinessRequirement,
  CodeGenerationSummary,
  GeneratedFileContent,
  GeneratedFileDiff,
  GenerationContextSnapshot,
  MockUser,
} from "@flowmind/agent-contracts";
import { AgentError } from "../common/agent-error.js";
import {
  ArtifactWriterService,
  type ConfirmArtifactWriteRequest,
} from "../artifact/artifact-writer.service.js";
import type { SoftGateScope } from "../verification/quality-gates.js";
import { DatabaseService, type GenerationRow, type SessionRow } from "../persistence/database.service.js";
import { PiAdapterService, type GenerationPiCallbacks } from "../pi/pi-adapter.service.js";
import { buildGenerationPrompt } from "../pi/generation-prompt.js";
import { EventBusService } from "../workflow/event-bus.service.js";
import { QualityPipelineService } from "../verification/quality-pipeline.service.js";
import {
  deriveGenerationSpec,
  GenerationRequirementError,
  validateGenerationRequirement,
} from "./generation-spec.js";
import { StagingService, generationContract, parseManifest } from "./staging.service.js";
import { TargetContractService, type ValidatedGenerationTarget } from "./target-contract.service.js";
import { GenerationContextRegistry } from "./generation-context-registry.service.js";
import { GenerationSkillRegistry } from "./generation-skill-registry.service.js";
import { PlatformClientService } from "../platform/platform-client.service.js";
import { loadFrozenTesterFixture } from "./frozen-tester-fixture.js";
import { createFakeGenerationFiles } from "../pi/fake-generation-files.js";
import { deriveRequirementIr, validateRequirementIr } from "../requirement/requirement-ir.js";

@Injectable()
export class GenerationService {
  private fallbackContexts?: GenerationContextRegistry;

  constructor(
    @Inject(DatabaseService) private readonly database: DatabaseService,
    @Inject(TargetContractService) private readonly targets: TargetContractService,
    @Inject(StagingService) private readonly staging: StagingService,
    @Inject(PiAdapterService) private readonly pi: PiAdapterService,
    @Inject(EventBusService) private readonly events: EventBusService,
    @Optional() @Inject(QualityPipelineService) private readonly quality?: QualityPipelineService,
    @Optional() @Inject(ArtifactWriterService) private readonly writer?: ArtifactWriterService,
    @Optional() @Inject(PlatformClientService) private readonly platform?: PlatformClientService,
    @Optional() @Inject(GenerationContextRegistry) private readonly contexts?: GenerationContextRegistry,
  ) {}

  start(
    sessionId: string,
    user: MockUser,
    rowVersion: number,
    idempotencyKey: string,
    targetRootInput?: string,
  ): { accepted: true; generationId: string; state: "CODE_GENERATING" } {
    requireKey(idempotencyKey, sessionId);
    const session = this.ownedSession(sessionId, user);
    const requestedRoot = targetRootInput?.trim() || session.target_root || "";
    const requestHash = digest({ targetRoot: requestedRoot });
    const replay = this.database.db.prepare(
      "SELECT * FROM agent_code_generation WHERE session_id = ? AND start_key = ?",
    ).get(sessionId, idempotencyKey) as GenerationRow | undefined;
    if (replay) {
      if (replay.start_hash !== requestHash) throw idempotencyConflict(sessionId);
      return JSON.parse(replay.start_result_json || "{}") as { accepted: true; generationId: string; state: "CODE_GENERATING" };
    }
    this.expectVersion(session, rowVersion);
    if (session.state !== "PROCESS_ACTIVE") throw stateError(session);
    const process = this.database.getProcessBySession(sessionId);
    if (!process || process.status !== "ACTIVE" || !process.platform_snapshot_json) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_PROCESS_NOT_ACTIVE", "an activated process snapshot is required", sessionId);
    }
    const requirement = JSON.parse(process.requirement_snapshot_json) as BusinessRequirement;
    const issues = validateGenerationRequirement(requirement);
    const requirementIr = deriveRequirementIr(requirement);
    const irValidation = validateRequirementIr(requirementIr);
    if (!irValidation.structurallyValid) {
      issues.push(...irValidation.schemaErrors.map(({ instancePath, message }) => `Requirement IR ${instancePath || "/"} ${message || "is invalid"}`));
    }
    issues.push(...irValidation.semanticErrors);
    issues.push(...requirementIr.ambiguities
      .filter(({ impact, status }) => impact === "BLOCKING" && status === "OPEN")
      .map(({ question }) => question));
    if (process.process_code !== requirement.businessCode) {
      issues.push(`激活流程编码 ${process.process_code} 与确认需求编码 ${requirement.businessCode} 不一致`);
    }
    if (issues.length) throw invalidRequirement(sessionId, issues);
    const target = this.targets.validate(requestedRoot, sessionId);
    try {
      deriveGenerationSpec(requirement, target.contract);
    } catch (error) {
      if (error instanceof GenerationRequirementError) throw invalidRequirement(sessionId, error.issues);
      throw error;
    }
    if (session.target_root && !samePath(session.target_root, target.targetRoot)) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_TARGET_ROOT_CONFLICT", "targetRoot cannot be switched after it is bound", sessionId);
    }
    return this.createGeneration(session, process.id, process.requirement_revision, requirement, JSON.parse(process.platform_snapshot_json), target, user, rowVersion, idempotencyKey, requestHash);
  }

  createTesterQualityFixture(user: MockUser, targetRootInput?: string): { sessionId: string; generationId: string } {
    if (user.userId !== "user_tester") {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_TEST_FIXTURE_FORBIDDEN", "Only the tester mock user can create a quality-gate fixture.");
    }
    const target = this.targets.validate(targetRootInput || "", "tester-fixture");
    const context = this.requireContexts("tester-fixture").capture(target, "tester-fixture");
    const fixture = loadFrozenTesterFixture();
    const spec = deriveGenerationSpec(fixture.requirement, target.contract);
    // The requirement/process snapshot is frozen, but artifact templates must
    // track the platform-starter API exposed by the selected target.
    const fixtureFiles = Object.entries(createFakeGenerationFiles(
      fixture.requirement,
      spec,
      target.contract,
      this.targets.readReference(target, `${target.contract.frontend.rootDir}/${target.contract.frontend.routeRegistry}`),
    )).map(([relativePath, content]) => ({ relativePath, content }));
    const requirementJson = JSON.stringify(fixture.requirement);
    const sessionId = `ags_tester_${randomUUID()}`;
    const processId = `apd_tester_${randomUUID()}`;
    const generationId = `acg_tester_${randomUUID()}`;
    const stagingDir = join(this.database.dataDir, "staging", sessionId, generationId);
    const now = new Date().toISOString();
    const snapshot = fixture.processSnapshot;
    snapshot.id = `definition_tester_${generationId}`;
    this.staging.prepare(stagingDir);
    this.database.transaction(() => {
      this.database.db.prepare(`
        INSERT INTO agent_session (
          id, owner_user_id, owner_user_name, owner_dept_id, owner_dept_name, target_root,
          state, row_version, requirement_revision, requirement_json, requirement_confirmed_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, 'CODE_GENERATING', 0, 1, ?, ?, ?, ?)
      `).run(sessionId, user.userId, user.userName, user.departmentId || null, user.departmentName || null,
        target.targetRoot, requirementJson, now, now, now);
      this.database.db.prepare(`
        INSERT INTO agent_process_definition (
          id, session_id, requirement_revision, platform_definition_id, process_code, process_name,
          definition_version, status, saga_step, requirement_snapshot_json, platform_snapshot_json, validation_json,
          create_operation_id, save_operation_id, publish_operation_id, activate_operation_id,
          created_by, created_at, activated_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 'ACTIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `).run(processId, sessionId, 1, `definition_tester_${generationId}`,
        fixture.requirement.businessCode, fixture.requirement.businessName, fixture.definitionVersion,
        requirementJson, JSON.stringify(snapshot), fixture.validationJson,
        `create_${generationId}`, `save_${generationId}`,
        `publish_${generationId}`, `activate_${generationId}`, user.userId, now, now, now);
      this.database.db.prepare(`
        INSERT INTO agent_code_generation (
          id, session_id, process_definition_record_id, requirement_revision, requirement_snapshot_json,
          process_snapshot_json, business_code, business_name, status, target_root, target_contract_version,
          target_contract_json, generation_context_snapshot_json, staging_dir, artifact_manifest_json, generation_revision,
          created_by, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'GENERATING', ?, ?, ?, ?, ?, '{}', 0, ?, ?, ?)
      `).run(generationId, sessionId, processId, 1, requirementJson, JSON.stringify(snapshot),
        fixture.requirement.businessCode, fixture.requirement.businessName, target.targetRoot, target.contract.contractVersion,
        JSON.stringify(target.contract), JSON.stringify(context), stagingDir, user.userId, now, now);
    });
    for (const { relativePath, content } of fixtureFiles) {
      this.staging.writeDuringGeneration(this.requiredGenerating(generationId), relativePath, content);
    }
    const manifest = this.staging.complete(
      this.requiredGenerating(generationId), target.contract, fixtureFiles.map(({ relativePath }) => relativePath),
    );
    this.events.publish(sessionId, { type: "generation.stage_changed", data: { generationId, state: "CODE_REVIEW", manifest } });
    return { sessionId, generationId };
  }

  get(sessionId: string, generationId: string, user: MockUser): CodeGenerationSummary {
    this.ownedSession(sessionId, user);
    return toSummary(this.ownedGeneration(sessionId, generationId, user));
  }

  readFile(sessionId: string, generationId: string, path: string, user: MockUser): GeneratedFileContent {
    const generation = this.manifestGeneration(sessionId, generationId, user);
    return this.staging.read(generation, path);
  }

  readDiff(sessionId: string, generationId: string, path: string, user: MockUser): GeneratedFileDiff {
    const generation = this.manifestGeneration(sessionId, generationId, user);
    return this.staging.diff(generation, path);
  }

  editFile(
    sessionId: string,
    generationId: string,
    path: string,
    content: string,
    generationRevision: number,
    user: MockUser,
  ): ArtifactManifest {
    const generation = this.reviewGeneration(sessionId, generationId, user);
    const manifest = this.staging.edit(generation, path, content, generationRevision);
    this.events.publish(sessionId, { type: "generation.file_changed", data: { generationId, relativePath: path, generationRevision: manifest.revision, editedByUser: true } });
    return manifest;
  }

  getQuality(sessionId: string, generationId: string, user: MockUser) {
    this.ownedGeneration(sessionId, generationId, user);
    return this.quality?.getReport(generationId) || null;
  }

  reverify(
    sessionId: string,
    generationId: string,
    user: MockUser,
    rowVersion: number,
    generationRevision: number,
    idempotencyKey: string,
    skipAiReview = false,
  ) {
    requireKey(idempotencyKey, sessionId);
    const session = this.ownedSession(sessionId, user);
    this.ownedGeneration(sessionId, generationId, user);
    const requestHash = digest({ generationRevision, skipAiReview });
    const replay = this.actionReplay<{ accepted: true; state: "CODE_VERIFYING" }>(
      generationId, "REVERIFY", idempotencyKey, requestHash, sessionId,
    );
    if (replay) return replay;
    this.expectVersion(session, rowVersion);
    if (!this.quality) throw new AgentError(HttpStatus.SERVICE_UNAVAILABLE, "AGENT_QUALITY_UNAVAILABLE", "Quality pipeline is unavailable.", sessionId);
    return this.quality.reverify(generationId, generationRevision, skipAiReview, {
      idempotencyKey,
      requestHash,
      expectedRowVersion: rowVersion,
    });
  }

  startQuality(
    sessionId: string,
    generationId: string,
    user: MockUser,
    rowVersion: number,
    generationRevision: number,
    skipAiReview: boolean,
    idempotencyKey: string,
  ) {
    requireKey(idempotencyKey, sessionId);
    const session = this.ownedSession(sessionId, user);
    const generation = this.ownedGeneration(sessionId, generationId, user);
    const requestHash = digest({ generationRevision, skipAiReview });
    const replay = this.actionReplay<{ accepted: true; state: "CODE_VERIFYING" }>(
      generationId, "START_QUALITY", idempotencyKey, requestHash, sessionId,
    );
    if (replay) return replay;
    this.expectVersion(session, rowVersion);
    const existingQuality = generation.quality_report_json
      ? JSON.parse(generation.quality_report_json) as CodeGenerationSummary["quality"]
      : undefined;
    const canRestartCancelledQuality = existingQuality?.pipelineState === "CANCELLED";
    if (generation.status !== "REVIEW" || generation.generation_revision !== generationRevision
      || (generation.quality_report_json && !canRestartCancelledQuality)) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_GENERATION_REVISION_CONFLICT", "Only an unverified current generation can enter the quality gate.", sessionId);
    }
    if (!this.quality) throw new AgentError(HttpStatus.SERVICE_UNAVAILABLE, "AGENT_QUALITY_UNAVAILABLE", "Quality pipeline is unavailable.", sessionId);
    return this.quality.reverify(generationId, generationRevision, skipAiReview, {
      idempotencyKey,
      requestHash,
      expectedRowVersion: rowVersion,
    });
  }

  stopQuality(
    sessionId: string,
    generationId: string,
    user: MockUser,
    rowVersion: number,
  ): { cancelled: true; state: "CODE_REVIEW" } {
    const session = this.ownedSession(sessionId, user);
    this.ownedGeneration(sessionId, generationId, user);
    this.expectVersion(session, rowVersion);
    if (!this.quality) throw new AgentError(HttpStatus.SERVICE_UNAVAILABLE, "AGENT_QUALITY_UNAVAILABLE", "Quality pipeline is unavailable.", sessionId);
    return this.quality.stop(generationId, rowVersion);
  }

  overrideQuality(
    sessionId: string,
    generationId: string,
    user: MockUser,
    rowVersion: number,
    generationRevision: number,
    scopes: SoftGateScope[],
    reason: string,
    idempotencyKey: string,
  ) {
    requireKey(idempotencyKey, sessionId);
    const session = this.ownedSession(sessionId, user);
    this.ownedGeneration(sessionId, generationId, user);
    const requestHash = digest({ generationRevision, scopes, reason });
    const replay = this.actionReplay<ReturnType<QualityPipelineService["override"]>>(
      generationId, "OVERRIDE_QUALITY", idempotencyKey, requestHash, sessionId,
    );
    if (replay) return replay;
    this.expectVersion(session, rowVersion);
    if (!this.quality) throw new AgentError(HttpStatus.SERVICE_UNAVAILABLE, "AGENT_QUALITY_UNAVAILABLE", "Quality pipeline is unavailable.", sessionId);
    return this.quality.override(generationId, generationRevision, scopes, reason, user.userId, {
      idempotencyKey,
      requestHash,
      expectedRowVersion: rowVersion,
    });
  }

  async confirmWrite(
    sessionId: string,
    generationId: string,
    user: MockUser,
    rowVersion: number,
    request: ConfirmArtifactWriteRequest,
    idempotencyKey: string,
  ): Promise<ReturnType<ArtifactWriterService["confirm"]>> {
    requireKey(idempotencyKey, sessionId);
    const session = this.ownedSession(sessionId, user);
    const generation = this.ownedGeneration(sessionId, generationId, user);
    if (!this.writer) throw new AgentError(HttpStatus.SERVICE_UNAVAILABLE, "AGENT_WRITER_UNAVAILABLE", "Artifact writer is unavailable.", sessionId);
    let result: ReturnType<ArtifactWriterService["confirm"]>;
    if (generation.status === "ENTRY_CONFIG_FAILED") {
      this.expectVersion(session, rowVersion);
      result = this.writer.resumeEntryConfiguration(generation, request);
    } else {
      const replay = this.writer.replay(generation, request, idempotencyKey);
      if (replay) {
        if (generation.status === "COMPLETED") return replay;
        throw new AgentError(
          HttpStatus.CONFLICT,
          "AGENT_GENERATION_STATE_CONFLICT",
          "Generation is already processing the confirmed write.",
          sessionId,
        );
      }
      this.expectVersion(session, rowVersion);
      result = this.writer.confirm(generation, request, idempotencyKey);
    }

    try {
      await this.configureBusinessEntry(this.ownedGeneration(sessionId, generationId, user), user);
      this.writer.completeEntryConfiguration(generationId, sessionId);
      return result;
    } catch (error) {
      const entryError = businessEntryConfigurationError(error, sessionId);
      this.writer.failEntryConfiguration(
        generationId,
        sessionId,
        "AGENT_BUSINESS_ENTRY_CONFIG_FAILED",
        "代码已写入，但业务入口登记失败；请重试入口登记。",
      );
      throw entryError;
    }
  }

  private async configureBusinessEntry(generation: GenerationRow, user: MockUser): Promise<void> {
    if (!this.platform) {
      throw new AgentError(
        HttpStatus.SERVICE_UNAVAILABLE,
        "AGENT_PLATFORM_UNAVAILABLE",
        "Business system client is unavailable.",
        generation.session_id,
      );
    }
    const processDefinition = this.database.getProcess(generation.process_definition_record_id);
    const definitionId = processDefinition?.platform_definition_id;
    if (!definitionId) {
      throw new AgentError(
        HttpStatus.CONFLICT,
        "AGENT_PROCESS_DEFINITION_MISSING",
        "The generated artifacts are not linked to a platform definition.",
        generation.session_id,
      );
    }
    const requirement = JSON.parse(generation.requirement_snapshot_json) as BusinessRequirement;
    const spec = deriveGenerationSpec(requirement, generationContract(generation));
    await this.platform.upsertBusinessEntryConfig(definitionId, {
      entryDisplayName: generation.business_name,
      entryPageUrl: spec.routePath,
      entrySource: "AGENT_GENERATED",
      enabled: true,
      generationId: generation.id,
      artifactRevision: String(generation.generation_revision),
    }, user);
  }

  async listDefinitions(user: MockUser) {
    const definitions = this.database.db.prepare(`
      SELECT id, session_id AS sessionId, platform_definition_id AS platformDefinitionId,
        process_code AS businessCode, process_name AS businessName, status,
        definition_version AS definitionVersion, activated_at AS activatedAt, created_at AS createdAt
      FROM agent_process_definition WHERE created_by = ? ORDER BY created_at DESC
    `).all(user.userId) as Array<{
      id: string;
      sessionId: string;
      platformDefinitionId: string | null;
      businessCode: string;
      businessName: string;
      status: string;
      definitionVersion: number | null;
      activatedAt: string | null;
      createdAt: string;
    }>;
    return Promise.all(definitions.map(async (definition) => {
      if (!this.platform || !definition.platformDefinitionId) return definition;
      try {
        const snapshot = await this.platform.getDefinition(definition.platformDefinitionId, user);
        return {
          ...definition,
          activationStatus: snapshot.activationStatus,
          definitionVersion: snapshot.version ?? definition.definitionVersion,
        };
      } catch {
        // The local Saga state remains a safe fallback while the platform is unavailable.
        return definition;
      }
    }));
  }

  listGenerations(user: MockUser) {
    return this.database.db.prepare(`
      SELECT id AS generationId, session_id AS sessionId, business_code AS businessCode,
        business_name AS businessName, status, generation_revision AS generationRevision,
        hard_gate_passed AS hardGatePassed, override_required AS overrideRequired,
        can_write AS canWrite, written_at AS writtenAt, created_at AS createdAt
      FROM agent_code_generation WHERE created_by = ? ORDER BY created_at DESC
    `).all(user.userId);
  }

  cancel(
    sessionId: string,
    generationId: string,
    user: MockUser,
    rowVersion: number,
  ): { cancelled: true; state: "PROCESS_ACTIVE" } {
    const session = this.ownedSession(sessionId, user);
    this.expectVersion(session, rowVersion);
    const generation = this.ownedGeneration(sessionId, generationId, user);
    if (session.state !== "CODE_GENERATING" || generation.status !== "GENERATING") throw stateError(session);
    this.pi.cancelGeneration(generationId);
    const now = new Date().toISOString();
    this.database.transaction(() => {
      this.database.db.prepare("UPDATE agent_code_generation SET status = 'CANCELLED', updated_at = ? WHERE id = ? AND status = 'GENERATING'").run(now, generationId);
      this.database.db.prepare(`UPDATE agent_session SET state = 'PROCESS_ACTIVE', row_version = row_version + 1,
        last_error_code = NULL, last_error_message = NULL, updated_at = ? WHERE id = ? AND row_version = ?`).run(now, sessionId, rowVersion);
    });
    const result = { cancelled: true as const, state: "PROCESS_ACTIVE" as const };
    this.events.publish(sessionId, { type: "workflow.state_changed", data: result });
    return result;
  }

  regenerate(
    sessionId: string,
    generationId: string,
    user: MockUser,
    rowVersion: number,
    generationRevision: number,
    idempotencyKey: string,
  ): { accepted: true; generationId: string; state: "CODE_GENERATING" } {
    requireKey(idempotencyKey, sessionId);
    const session = this.ownedSession(sessionId, user);
    const requestHash = digest({ supersedes: generationId, generationRevision });
    const replay = this.database.db.prepare(
      "SELECT * FROM agent_code_generation WHERE session_id = ? AND start_key = ?",
    ).get(sessionId, idempotencyKey) as GenerationRow | undefined;
    if (replay) {
      if (replay.start_hash !== requestHash) throw idempotencyConflict(sessionId);
      return JSON.parse(replay.start_result_json || "{}") as { accepted: true; generationId: string; state: "CODE_GENERATING" };
    }
    this.expectVersion(session, rowVersion);
    if (!["CODE_REVIEW", "CODE_PIPELINE_FAILED"].includes(session.state)) throw stateError(session);
    const old = this.ownedGeneration(sessionId, generationId, user);
    if (!["REVIEW", "FAILED"].includes(old.status) || old.generation_revision !== generationRevision) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_GENERATION_REVISION_CONFLICT", "generation cannot be regenerated from this revision", sessionId);
    }
    const target = this.targets.validate(old.target_root, sessionId);
    const requirement = JSON.parse(old.requirement_snapshot_json) as BusinessRequirement;
    const result = this.createGeneration(
      session,
      old.process_definition_record_id,
      old.requirement_revision,
      requirement,
      JSON.parse(old.process_snapshot_json),
      target,
      user,
      rowVersion,
      idempotencyKey,
      requestHash,
      old.id,
      parseGenerationContext(old.generation_context_snapshot_json, sessionId),
    );
    return result;
  }

  private createGeneration(
    session: SessionRow,
    processId: string,
    requirementRevision: number,
    requirement: BusinessRequirement,
    processSnapshot: Record<string, unknown>,
    target: ValidatedGenerationTarget,
    user: MockUser,
    rowVersion: number,
    idempotencyKey: string,
    requestHash: string,
    supersedes?: string,
    inheritedContext?: GenerationContextSnapshot,
  ): { accepted: true; generationId: string; state: "CODE_GENERATING" } {
    const generationId = `acg_${randomUUID()}`;
    const stagingDir = join(this.database.dataDir, "staging", session.id, generationId);
    this.staging.prepare(stagingDir);
    const now = new Date().toISOString();
    const context = inheritedContext || this.requireContexts(session.id).capture(target, session.id);
    const result = { accepted: true as const, generationId, state: "CODE_GENERATING" as const };
    this.database.transaction(() => {
      if (supersedes) this.database.db.prepare("UPDATE agent_code_generation SET status = 'SUPERSEDED', superseded_by = ?, updated_at = ? WHERE id = ?").run(generationId, now, supersedes);
      this.database.db.prepare(`
        INSERT INTO agent_code_generation (
          id, session_id, process_definition_record_id, requirement_revision, requirement_snapshot_json,
          process_snapshot_json, business_code, business_name, status, target_root, target_contract_version,
          target_contract_json, generation_context_snapshot_json, staging_dir, artifact_manifest_json, generation_revision,
          start_key, start_hash, start_result_json, created_by, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'GENERATING', ?, ?, ?, ?, ?, '{}', 0, ?, ?, ?, ?, ?, ?)
      `).run(
        generationId, session.id, processId, requirementRevision, JSON.stringify(requirement), JSON.stringify(processSnapshot),
        requirement.businessCode, requirement.businessName, target.targetRoot, target.contract.contractVersion,
        JSON.stringify(target.contract), JSON.stringify(context), stagingDir, idempotencyKey, requestHash, JSON.stringify(result), user.userId, now, now,
      );
      const updated = this.database.db.prepare(`UPDATE agent_session SET target_root = ?, state = 'CODE_GENERATING', row_version = row_version + 1,
        last_error_code = NULL, last_error_message = NULL, updated_at = ? WHERE id = ? AND row_version = ?`)
        .run(target.targetRoot, now, session.id, rowVersion);
      if (updated.changes !== 1) throw new AgentError(HttpStatus.CONFLICT, "AGENT_ROW_VERSION_CONFLICT", "row version changed while starting generation", session.id);
    });
    this.events.publish(session.id, { type: "generation.stage_changed", data: result });
    this.events.publish(session.id, { type: "workflow.state_changed", data: result });
    setImmediate(() => void this.run(generationId));
    return result;
  }

  private async run(generationId: string): Promise<void> {
    let generation = this.database.getGeneration(generationId);
    if (!generation || generation.status !== "GENERATING") return;
    const contract = generationContract(generation);
    const requirement = JSON.parse(generation.requirement_snapshot_json) as BusinessRequirement;
    const requirementIr = deriveRequirementIr(requirement);
    const irValidation = validateRequirementIr(requirementIr);
    if (!irValidation.generationReady) {
      this.fail(generationId, "AGENT_REQUIREMENT_IR_NOT_READY", [
        ...irValidation.semanticErrors,
        ...requirementIr.ambiguities.filter(({ impact, status }) => impact === "BLOCKING" && status === "OPEN").map(({ question }) => question),
      ].join("; ") || "Requirement IR is not generation-ready.");
      return;
    }
    const spec = deriveGenerationSpec(requirement, contract);
    const target: ValidatedGenerationTarget = { targetRoot: generation.target_root, contract };
    const context = parseGenerationContext(generation.generation_context_snapshot_json, generation.session_id);
    const contextAccess = buildContextAccess(this.requireContexts(generation.session_id), context, spec.hasBusinessApi, generation.session_id);
    const apiReferences = {
      businessReferenceData: contract.frontend.apiReferences?.businessReferenceData
        ? this.targets.readReference(target, contract.frontend.apiReferences.businessReferenceData, generation.session_id)
        : undefined,
      contextSummary: JSON.stringify(this.requireContexts(generation.session_id).summary(context), null, 2),
    };
    const callbacks: GenerationPiCallbacks = {
      requirement,
      contract,
      spec,
      onEvent: (type, data) => this.events.publish(generation!.session_id, { type, data }),
      onError: (code, message) => this.fail(generationId, code, message),
      readReference: (path) => this.targets.readReference(target, path, generation!.session_id),
      readStaged: (path) => this.staging.read(this.requiredGenerating(generationId), path).content,
      listStaged: () => this.staging.list(this.requiredGenerating(generationId)),
      writeStaged: (path, content) => this.staging.writeDuringGeneration(this.requiredGenerating(generationId), path, content),
      deleteStaged: (path) => this.staging.deleteDuringGeneration(this.requiredGenerating(generationId), path),
      ...contextAccess,
      reportComplete: (files) => {
        const current = this.requiredGenerating(generationId);
        const manifest = this.staging.complete(current, contract, files);
        this.events.publish(current.session_id, { type: "generation.stage_changed", data: { generationId, state: "CODE_REVIEW", manifest } });
        this.events.publish(current.session_id, { type: "workflow.state_changed", data: { state: "CODE_REVIEW" } });
      },
    };
    try {
      const piSession = await this.pi.runGeneration(
        generationId,
        generation.staging_dir,
        buildGenerationPrompt(requirement, requirementIr, JSON.parse(generation.process_snapshot_json), contract, spec, apiReferences),
        callbacks,
      );
      this.database.db.prepare("UPDATE agent_code_generation SET pi_session_id = ?, pi_session_file = ?, updated_at = ? WHERE id = ?")
        .run(piSession.piSessionId, piSession.sessionFile || null, new Date().toISOString(), generationId);
      generation = this.database.getGeneration(generationId);
      if (generation?.status === "GENERATING") this.fail(generationId, "AGENT_GENERATION_INCOMPLETE", "generator ended without report_generation_complete");
    } catch (error) {
      const current = this.database.getGeneration(generationId);
      if (current?.status === "GENERATING") this.fail(generationId, "AGENT_GENERATION_FAILED", error instanceof Error ? error.message : String(error));
    }
  }

  private fail(generationId: string, code: string, message: string): void {
    const generation = this.database.getGeneration(generationId);
    if (!generation || generation.status !== "GENERATING") return;
    const now = new Date().toISOString();
    this.database.transaction(() => {
      this.database.db.prepare("UPDATE agent_code_generation SET status = 'FAILED', last_error_code = ?, last_error_message = ?, updated_at = ? WHERE id = ? AND status = 'GENERATING'").run(code, message, now, generationId);
      this.database.db.prepare(`UPDATE agent_session SET state = 'CODE_PIPELINE_FAILED', row_version = row_version + 1,
        last_error_code = ?, last_error_message = ?, updated_at = ? WHERE id = ? AND state = 'CODE_GENERATING'`).run(code, message, now, generation.session_id);
    });
    this.events.publish(generation.session_id, { type: "error", data: { code, message, generationId } });
    this.events.publish(generation.session_id, { type: "workflow.state_changed", data: { state: "CODE_PIPELINE_FAILED" } });
  }

  private requireContexts(sessionId?: string): GenerationContextRegistry {
    if (this.contexts) return this.contexts;
    // Keep direct service construction used by tests and embedders on the same
    // mandatory snapshot path as Nest-managed production instances.
    this.fallbackContexts ||= new GenerationContextRegistry(new GenerationSkillRegistry(), this.targets);
    return this.fallbackContexts;
  }

  private requiredGenerating(id: string): GenerationRow {
    const generation = this.database.getGeneration(id);
    if (!generation || generation.status !== "GENERATING") throw new Error("generation is no longer writable");
    return generation;
  }

  private reviewGeneration(sessionId: string, generationId: string, user: MockUser): GenerationRow {
    const generation = this.ownedGeneration(sessionId, generationId, user);
    if (!["REVIEW", "FAILED"].includes(generation.status)) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_GENERATION_STATE_CONFLICT", "generation is not ready for file review", sessionId);
    }
    return generation;
  }

  private manifestGeneration(sessionId: string, generationId: string, user: MockUser): GenerationRow {
    const generation = this.ownedGeneration(sessionId, generationId, user);
    if (!parseManifest(generation).files.length) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_GENERATION_STATE_CONFLICT", "Generation Manifest is not available.", sessionId);
    }
    return generation;
  }

  private ownedGeneration(sessionId: string, generationId: string, user: MockUser): GenerationRow {
    const generation = this.database.getGeneration(generationId);
    if (!generation || generation.session_id !== sessionId) throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_GENERATION_NOT_FOUND", "generation was not found", sessionId);
    if (generation.created_by !== user.userId) throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_GENERATION_FORBIDDEN", "generation belongs to another user", sessionId);
    return generation;
  }

  private ownedSession(sessionId: string, user: MockUser): SessionRow {
    const session = this.database.getSession(sessionId);
    if (!session) throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_SESSION_NOT_FOUND", "session not found", sessionId);
    if (session.owner_user_id !== user.userId) throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_SESSION_FORBIDDEN", "session belongs to another user", sessionId);
    return session;
  }

  private expectVersion(session: SessionRow, expected: number): void {
    if (!Number.isInteger(expected) || session.row_version !== expected) throw new AgentError(HttpStatus.CONFLICT, "AGENT_ROW_VERSION_CONFLICT", "row version is stale", session.id, { expected: session.row_version });
  }

  private actionReplay<T>(
    generationId: string,
    action: string,
    idempotencyKey: string,
    requestHash: string,
    sessionId: string,
  ): T | undefined {
    const replay = this.database.db.prepare(`
      SELECT request_hash, result_json FROM agent_generation_action
      WHERE generation_id = ? AND action = ? AND idempotency_key = ?
    `).get(generationId, action, idempotencyKey) as { request_hash: string; result_json: string } | undefined;
    if (!replay) return undefined;
    if (replay.request_hash !== requestHash) throw idempotencyConflict(sessionId);
    return JSON.parse(replay.result_json) as T;
  }
}

export function toSummary(generation: GenerationRow): CodeGenerationSummary {
  const manifest = parseManifest(generation);
  const quality = generation.quality_report_json
    ? JSON.parse(generation.quality_report_json) as CodeGenerationSummary["quality"]
    : undefined;
  return {
    generationId: generation.id,
    status: generation.status as CodeGenerationSummary["status"],
    generationRevision: generation.generation_revision,
    targetRoot: generation.target_root,
    contractVersion: generation.target_contract_version,
    manifest: manifest.files.length ? manifest : undefined,
    lastError: generation.last_error_code ? { code: generation.last_error_code, message: generation.last_error_message || "" } : undefined,
    quality,
    backendRestartRequired: false,
    context: generation.generation_context_snapshot_json && generation.generation_context_snapshot_json !== "{}"
      ? summarizeContext(parseGenerationContext(generation.generation_context_snapshot_json, generation.session_id))
      : undefined,
    createdAt: generation.created_at,
    updatedAt: generation.updated_at,
  };
}

function parseGenerationContext(value: string, sessionId?: string): GenerationContextSnapshot {
  try {
    const parsed = JSON.parse(value || "{}") as GenerationContextSnapshot;
    if (parsed.version !== "1.0" || !parsed.sha256 || !Array.isArray(parsed.skills) || !Array.isArray(parsed.references)) throw new Error("invalid snapshot");
    return parsed;
  } catch {
    throw new AgentError(HttpStatus.CONFLICT, "AGENT_GENERATION_CONTEXT_MISSING", "generation context snapshot is missing; start a new generation", sessionId);
  }
}

function summarizeContext(snapshot: GenerationContextSnapshot) {
  return {
    sha256: snapshot.sha256,
    skills: snapshot.skills.map(({ name, sha256 }) => ({ name, sha256 })),
    references: snapshot.references.map(({ source, relativePath, sha256 }) => ({ source, relativePath, sha256 })),
  };
}

function buildContextAccess(
  registry: GenerationContextRegistry,
  snapshot: GenerationContextSnapshot,
  includeApiExamples: boolean,
  sessionId?: string,
): Pick<GenerationPiCallbacks, "listGenerationContext" | "readGenerationContext" | "requiredGenerationContextKeys"> {
  const items = [
    ...snapshot.skills.flatMap((skill) => skill.files.map((file) => ({
      key: `skill:${skill.name}:${file.relativePath}`,
      sha256: file.sha256,
      required: file.relativePath === "SKILL.md" || file.relativePath === "references/golden-example.md",
      read: () => registry.readSkill(snapshot, skill.name, file.relativePath, sessionId),
    }))),
    ...snapshot.references.map((reference) => ({
      key: `reference:${reference.source}:${reference.relativePath}`,
      sha256: reference.sha256,
      required: reference.source === "REPOSITORY" || includeApiExamples,
      read: () => registry.readReference(snapshot, reference.source, reference.relativePath, sessionId),
    })),
  ];
  return {
    listGenerationContext: () => items.map(({ key, sha256, required }) => ({ key, sha256, required })),
    readGenerationContext: (key) => {
      const item = items.find((candidate) => candidate.key === key);
      if (!item) throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_GENERATION_CONTEXT_FILE_NOT_FOUND", "generation context file was not found", sessionId);
      return item.read();
    },
    requiredGenerationContextKeys: items.filter(({ required }) => required).map(({ key }) => key),
  };
}

function samePath(left: string, right: string): boolean {
  const normalize = (value: string) => {
    const resolved = resolve(value);
    return existsSync(resolved) ? realpathSync.native(resolved) : resolved;
  };
  const normalizedLeft = normalize(left);
  const normalizedRight = normalize(right);
  return process.platform === "win32"
    ? normalizedLeft.toLowerCase() === normalizedRight.toLowerCase()
    : normalizedLeft === normalizedRight;
}

function digest(value: unknown): string { return createHash("sha256").update(JSON.stringify(value)).digest("hex"); }
function requireKey(value: string, sessionId: string): void { if (!value?.trim()) throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required", sessionId); }
function idempotencyConflict(sessionId: string): AgentError { return new AgentError(HttpStatus.CONFLICT, "AGENT_IDEMPOTENCY_CONFLICT", "idempotency key was reused with different content", sessionId); }
function stateError(session: SessionRow): AgentError { return new AgentError(HttpStatus.CONFLICT, "AGENT_STATE_CONFLICT", "current state does not allow this operation", session.id, { currentState: session.state }); }
function invalidRequirement(sessionId: string, issues: string[]): AgentError {
  return new AgentError(
    HttpStatus.BAD_REQUEST,
    "AGENT_GENERATION_REQUIREMENT_INVALID",
    "confirmed requirement cannot be safely generated",
    sessionId,
    { issues: [...new Set(issues)] },
  );
}

function businessEntryConfigurationError(error: unknown, sessionId: string): AgentError {
  const status = error instanceof AgentError ? error.getStatus() : HttpStatus.BAD_GATEWAY;
  const upstream = error instanceof AgentError
    ? error.getResponse()
    : { message: error instanceof Error ? error.message : String(error) };
  return new AgentError(
    status,
    "AGENT_BUSINESS_ENTRY_CONFIG_FAILED",
    "代码已写入，但业务入口登记失败；请重试入口登记。",
    sessionId,
    { upstream },
  );
}
