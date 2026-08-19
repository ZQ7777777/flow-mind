import { HttpStatus, Inject, Injectable } from "@nestjs/common";
import { createHash, randomUUID } from "node:crypto";
import { isAbsolute, normalize, resolve } from "node:path";
import {
  DEFAULT_SYSTEM_CODE,
  createDefaultUserTaskConfigs,
  type BusinessRequirement,
  type MockUser,
  type ProcessPreview,
  type RequirementRevision,
  type WorkflowSnapshot,
  type WorkflowState,
} from "@flowmind/agent-contracts";
import { AgentError } from "../common/agent-error.js";
import { DatabaseService, type ProcessRow, type SessionRow } from "../persistence/database.service.js";
import {
  PiAdapterService,
  type PiCallbacks,
  type RegisteredRole,
  type RequirementSubmissionResult,
} from "../pi/pi-adapter.service.js";
import { validateRequirement } from "../requirement/requirement-validator.js";
import { EventBusService } from "./event-bus.service.js";
import { PlatformClientService } from "../platform/platform-client.service.js";
import { toSummary } from "../generation/generation.service.js";

@Injectable()
export class WorkflowService {
  private readonly commandChains = new Map<string, Promise<void>>();

  constructor(
    @Inject(DatabaseService) private readonly database: DatabaseService,
    @Inject(PiAdapterService) private readonly pi: PiAdapterService,
    @Inject(EventBusService) private readonly events: EventBusService,
    @Inject(PlatformClientService) private readonly platform: PlatformClientService,
  ) {}

  async createSession(user: MockUser, targetRoot?: string): Promise<WorkflowSnapshot> {
    const readiness = await this.pi.ready();
    if (!readiness.ready) {
      throw new AgentError(HttpStatus.SERVICE_UNAVAILABLE, "AGENT_MODEL_NOT_READY", readiness.message || "model is not ready");
    }
    const normalizedTarget = normalizeOptionalTarget(targetRoot);
    const id = `ags_${randomUUID()}`;
    const now = new Date().toISOString();
    this.database.db.prepare(`
      INSERT INTO agent_session (
        id, owner_user_id, owner_user_name, owner_dept_id, owner_dept_name, target_root,
        state, row_version, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, 'COLLECTING', 0, ?, ?)
    `).run(
      id, user.userId, user.userName, user.departmentId || null, user.departmentName || null,
      normalizedTarget || null, now, now,
    );
    try {
      const piSession = await this.pi.ensureSession(id, this.callbacks(id));
      this.database.db.prepare(
        "UPDATE agent_session SET pi_session_id = ?, pi_session_file = ?, updated_at = ? WHERE id = ?",
      ).run(piSession.piSessionId, piSession.sessionFile || null, new Date().toISOString(), id);
    } catch (error) {
      this.database.db.prepare("DELETE FROM agent_session WHERE id = ?").run(id);
      throw error;
    }
    return this.getSnapshot(id, user);
  }

  async getSnapshot(sessionId: string, user: MockUser): Promise<WorkflowSnapshot> {
    const session = this.ownedSession(sessionId, user);
    const requirement = toRequirementRevision(session);
    const process = hasProcessPreview(session.state) ? this.database.getProcessBySession(sessionId) : undefined;
    const preview = process ? toProcessPreview(process) : undefined;
    const generation = hasGenerationPreview(session.state)
      ? this.database.getLatestGenerationBySession(sessionId)
      : undefined;
    const messages = await this.pi.getMessages(sessionId, this.callbacks(sessionId));
    return {
      sessionId,
      ownerUserId: session.owner_user_id,
      state: session.state,
      rowVersion: session.row_version,
      targetRoot: session.target_root || undefined,
      businessName: requirement?.requirement.businessName,
      messages,
      requirement,
      processPreview: preview,
      activeGeneration: generation && generation.status !== "CANCELLED" ? toSummary(generation) : undefined,
      lastError: session.last_error_code
        ? { code: session.last_error_code, message: session.last_error_message || "" }
        : undefined,
      allowedActions: allowedActions(session.state, Boolean(preview?.validation.valid)),
    };
  }

  listSessions(user: MockUser): Array<{
    sessionId: string;
    businessName: string;
    state: WorkflowState;
    rowVersion: number;
    lastError?: { code: string; message: string };
    createdAt: string;
    updatedAt: string;
  }> {
    const sessions = this.database.db.prepare(`
      SELECT id, state, row_version, requirement_json, last_error_code,
        last_error_message, created_at, updated_at
      FROM agent_session
      WHERE owner_user_id = ?
      ORDER BY updated_at DESC
    `).all(user.userId) as Array<Pick<SessionRow,
      "id" | "state" | "row_version" | "requirement_json" | "last_error_code"
      | "last_error_message" | "created_at" | "updated_at"
    >>;
    return sessions.map((session) => ({
      sessionId: session.id,
      businessName: sessionBusinessName(session.requirement_json),
      state: session.state,
      rowVersion: session.row_version,
      lastError: session.last_error_code
        ? { code: session.last_error_code, message: session.last_error_message || "" }
        : undefined,
      createdAt: session.created_at,
      updatedAt: session.updated_at,
    }));
  }

  async queueMessage(sessionId: string, user: MockUser, rowVersion: number, content: string): Promise<{ accepted: true }> {
    const session = this.ownedSession(sessionId, user);
    this.expectVersion(session, rowVersion);
    this.expectState(session, ["COLLECTING"]);
    if (!content?.trim()) throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_MESSAGE_EMPTY", "message must not be empty", sessionId);
    const previous = this.commandChains.get(sessionId) || Promise.resolve();
    const next = previous
      .catch(() => undefined)
      .then(async () => {
        const current = this.ownedSession(sessionId, user);
        if (current.state !== "COLLECTING") return;
        this.clearSessionError(sessionId);
        await this.pi.prompt(sessionId, content.trim(), this.callbacks(sessionId));
      })
      .catch((error) => {
        this.setSessionError(sessionId, "AGENT_PI_ERROR", error instanceof Error ? error.message : String(error));
        this.events.publish(sessionId, { type: "error", data: { code: "AGENT_PI_ERROR", message: String(error) } });
      })
      .finally(() => {
        if (this.commandChains.get(sessionId) === next) this.commandChains.delete(sessionId);
      });
    this.commandChains.set(sessionId, next);
    return { accepted: true };
  }

  getRequirement(sessionId: string, user: MockUser): RequirementRevision {
    const session = this.ownedSession(sessionId, user);
    this.expectState(session, [
      "REQUIREMENT_REVIEW", "PROCESS_PROVISIONING", "PROCESS_PROVISION_FAILED",
      "PROCESS_REVIEW", "PROCESS_ACTIVATING", "PROCESS_ACTIVATION_FAILED", "PROCESS_ACTIVE",
      "CODE_GENERATING", "CODE_REVIEW", "CODE_PIPELINE_FAILED",
    ]);
    const revision = toRequirementRevision(session);
    if (!revision) throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_REQUIREMENT_NOT_FOUND", "requirement not found", sessionId);
    return revision;
  }

  async updateRequirement(
    sessionId: string,
    user: MockUser,
    rowVersion: number,
    requirement: unknown,
  ): Promise<RequirementRevision> {
    const session = this.ownedSession(sessionId, user);
    this.expectVersion(session, rowVersion);
    this.expectState(session, ["REQUIREMENT_REVIEW"]);
    const normalizedRequirement = applyRequirementDefaults(requirement);
    const validation = validateRequirement(normalizedRequirement);
    if (!validation.structurallyValid) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_REQUIREMENT_SCHEMA_INVALID", "requirement schema is invalid", sessionId, {
        errors: validation.schemaErrors,
      });
    }
    const revision = session.requirement_revision + 1;
    const now = new Date().toISOString();
    this.database.db.prepare(`
      UPDATE agent_session SET
        requirement_revision = ?, requirement_json = ?, requirement_missing_items_json = ?,
        requirement_ambiguities_json = ?, requirement_ready_for_review = ?, requirement_source = 'USER_EDIT',
        requirement_confirmed_at = NULL, row_version = row_version + 1,
        last_error_code = NULL, last_error_message = NULL, updated_at = ?
      WHERE id = ? AND row_version = ?
    `).run(
      revision, JSON.stringify(normalizedRequirement), JSON.stringify(validation.missingItems),
      JSON.stringify(validation.ambiguities), validation.readyForReview ? 1 : 0, now, sessionId, rowVersion,
    );
    this.publishSnapshot(sessionId, user);
    return this.getRequirement(sessionId, user);
  }

  async confirmRequirement(
    sessionId: string,
    user: MockUser,
    rowVersion: number,
    requirementRevision: number,
    idempotencyKey: string,
  ): Promise<{ accepted: true; sessionId: string; state: WorkflowState }> {
    requireIdempotencyKey(idempotencyKey, sessionId);
    const session = this.ownedSession(sessionId, user);
    const requestHash = hash({ requirementRevision });
    const replay = checkReplay<{ accepted: true; sessionId: string; state: WorkflowState }>(
      session.requirement_confirm_key, session.requirement_confirm_hash, session.requirement_confirm_result_json,
      idempotencyKey, requestHash, sessionId,
    );
    if (replay) return replay;
    this.expectVersion(session, rowVersion);
    this.expectState(session, ["REQUIREMENT_REVIEW"]);
    if (session.requirement_revision !== requirementRevision) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_REQUIREMENT_REVISION_CONFLICT", "requirement revision is stale", sessionId);
    }
    if (!session.requirement_ready_for_review || !session.requirement_json) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_REQUIREMENT_INCOMPLETE", "requirement is not ready for confirmation", sessionId);
    }
    const requirement = JSON.parse(session.requirement_json) as BusinessRequirement;
    const now = new Date().toISOString();
    const result = { accepted: true as const, sessionId, state: "PROCESS_PROVISIONING" as WorkflowState };
    this.database.transaction(() => {
      this.database.db.prepare(`
        UPDATE agent_session SET state = 'PROCESS_PROVISIONING', row_version = row_version + 1,
          requirement_confirmed_at = ?, requirement_confirm_key = ?, requirement_confirm_hash = ?,
          requirement_confirm_result_json = ?, last_error_code = NULL, last_error_message = NULL, updated_at = ?
        WHERE id = ? AND row_version = ?
      `).run(now, idempotencyKey, requestHash, JSON.stringify(result), now, sessionId, rowVersion);
      this.database.db.prepare(`
        INSERT INTO agent_process_definition (
          id, session_id, requirement_revision, process_code, process_name, status, saga_step,
          requirement_snapshot_json, create_operation_id, save_operation_id, publish_operation_id,
          activate_operation_id, created_by, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, 'DRAFT', 'PENDING_CREATE', ?, ?, ?, ?, ?, ?, ?, ?)
      `).run(
        `apd_${randomUUID()}`, sessionId, requirementRevision, requirement.businessCode, requirement.businessName,
        JSON.stringify(requirement), `op_create_${randomUUID()}`, `op_save_${randomUUID()}`,
        `op_publish_${randomUUID()}`, `op_activate_${randomUUID()}`, user.userId, now, now,
      );
    });
    this.events.publish(sessionId, { type: "workflow.state_changed", data: result });
    setImmediate(() => void this.provision(sessionId, user));
    return result;
  }

  async reopenRequirement(sessionId: string, user: MockUser, rowVersion: number): Promise<WorkflowSnapshot> {
    const session = this.ownedSession(sessionId, user);
    this.expectVersion(session, rowVersion);
    this.expectState(session, ["REQUIREMENT_REVIEW"]);
    this.database.db.prepare(`
      UPDATE agent_session SET state = 'COLLECTING', row_version = row_version + 1,
        last_error_code = NULL, last_error_message = NULL, updated_at = ? WHERE id = ? AND row_version = ?
    `).run(new Date().toISOString(), sessionId, rowVersion);
    await this.publishSnapshot(sessionId, user);
    return this.getSnapshot(sessionId, user);
  }

  async resetSession(sessionId: string, user: MockUser, rowVersion: number): Promise<WorkflowSnapshot> {
    const session = this.ownedSession(sessionId, user);
    this.expectVersion(session, rowVersion);
    this.expectState(session, resettableStates);

    const previous = this.commandChains.get(sessionId) || Promise.resolve();
    const reset = previous
      .catch(() => undefined)
      .then(async () => {
        const current = this.ownedSession(sessionId, user);
        this.expectState(current, resettableStates);
        const piSession = await this.pi.resetSession(sessionId, this.callbacks(sessionId));
        const now = new Date().toISOString();
        this.database.db.prepare(`
          UPDATE agent_session SET
            state = 'COLLECTING', row_version = row_version + 1,
            requirement_json = NULL, requirement_missing_items_json = '[]',
            requirement_ambiguities_json = '[]', requirement_ready_for_review = 0,
            requirement_source = NULL, requirement_confirmed_at = NULL,
            requirement_confirm_key = NULL, requirement_confirm_hash = NULL,
            requirement_confirm_result_json = NULL,
            pi_session_id = ?, pi_session_file = ?,
            last_error_code = NULL, last_error_message = NULL, updated_at = ?
          WHERE id = ?
        `).run(piSession.piSessionId, piSession.sessionFile || null, now, sessionId);
        this.database.db.prepare(`
          UPDATE agent_code_generation SET status = 'SUPERSEDED', updated_at = ?
          WHERE session_id = ? AND status IN ('REVIEW', 'FAILED')
        `).run(now, sessionId);
        await this.publishSnapshot(sessionId, user);
      })
      .finally(() => {
        if (this.commandChains.get(sessionId) === reset) this.commandChains.delete(sessionId);
      });
    this.commandChains.set(sessionId, reset);
    await reset;
    return this.getSnapshot(sessionId, user);
  }

  getProcessPreview(sessionId: string, user: MockUser): ProcessPreview {
    const session = this.ownedSession(sessionId, user);
    this.expectState(session, ["PROCESS_REVIEW", "PROCESS_ACTIVATING", "PROCESS_ACTIVATION_FAILED", "PROCESS_ACTIVE"]);
    const process = this.database.getProcessBySession(sessionId);
    const preview = process && toProcessPreview(process);
    if (!preview) throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_PROCESS_PREVIEW_NOT_FOUND", "process preview not found", sessionId);
    return preview;
  }

  async confirmProcess(
    sessionId: string,
    user: MockUser,
    rowVersion: number,
    body: { platformDefinitionId: string; requirementRevision: number },
    idempotencyKey: string,
  ): Promise<{ accepted: true; sessionId: string; state: WorkflowState }> {
    requireIdempotencyKey(idempotencyKey, sessionId);
    const session = this.ownedSession(sessionId, user);
    const process = this.requiredProcess(sessionId);
    const requestHash = hash(body);
    const replay = checkReplay<{ accepted: true; sessionId: string; state: WorkflowState }>(
      process.process_confirm_key, process.process_confirm_hash, process.process_confirm_result_json,
      idempotencyKey, requestHash, sessionId,
    );
    if (replay) return replay;
    this.expectVersion(session, rowVersion);
    this.expectState(session, ["PROCESS_REVIEW"]);
    if (process.platform_definition_id !== body.platformDefinitionId || process.requirement_revision !== body.requirementRevision) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_PROCESS_PREVIEW_CONFLICT", "process preview is stale", sessionId);
    }
    const validation = parseJson(process.validation_json, { valid: false, issues: [] });
    if (!validation.valid) {
      throw new AgentError(HttpStatus.UNPROCESSABLE_ENTITY, "AGENT_PROCESS_VALIDATION_FAILED", "process validation has blocking issues", sessionId, validation);
    }
    const now = new Date().toISOString();
    const result = { accepted: true as const, sessionId, state: "PROCESS_ACTIVATING" as WorkflowState };
    this.database.transaction(() => {
      this.database.db.prepare(`
        UPDATE agent_session SET state = 'PROCESS_ACTIVATING', row_version = row_version + 1,
          last_error_code = NULL, last_error_message = NULL, updated_at = ?
        WHERE id = ? AND row_version = ?
      `).run(now, sessionId, rowVersion);
      this.database.db.prepare(`
        UPDATE agent_process_definition SET process_confirm_key = ?, process_confirm_hash = ?,
          process_confirm_result_json = ?, last_error_code = NULL, last_error_message = NULL, updated_at = ?
        WHERE id = ?
      `).run(idempotencyKey, requestHash, JSON.stringify(result), now, process.id);
    });
    this.events.publish(sessionId, { type: "workflow.state_changed", data: result });
    setImmediate(() => void this.activate(sessionId, user));
    return result;
  }

  async retryProcess(
    sessionId: string,
    user: MockUser,
    rowVersion: number,
    idempotencyKey: string,
  ): Promise<{ accepted: true; sessionId: string; state: WorkflowState }> {
    requireIdempotencyKey(idempotencyKey, sessionId);
    const session = this.ownedSession(sessionId, user);
    const process = this.requiredProcess(sessionId);
    const requestHash = hash({ failedState: session.state, sagaStep: process.saga_step });
    const replay = checkReplay<{ accepted: true; sessionId: string; state: WorkflowState }>(
      process.retry_key, process.retry_hash, process.retry_result_json,
      idempotencyKey, requestHash, sessionId,
    );
    if (replay) return replay;
    this.expectVersion(session, rowVersion);
    this.expectState(session, ["PROCESS_PROVISION_FAILED", "PROCESS_ACTIVATION_FAILED"]);
    const nextState: WorkflowState = session.state === "PROCESS_PROVISION_FAILED" ? "PROCESS_PROVISIONING" : "PROCESS_ACTIVATING";
    const result = { accepted: true as const, sessionId, state: nextState };
    const now = new Date().toISOString();
    this.database.transaction(() => {
      this.database.db.prepare(`
        UPDATE agent_session SET state = ?, row_version = row_version + 1,
          last_error_code = NULL, last_error_message = NULL, updated_at = ?
        WHERE id = ? AND row_version = ?
      `).run(nextState, now, sessionId, rowVersion);
      this.database.db.prepare(`
        UPDATE agent_process_definition SET retry_key = ?, retry_hash = ?, retry_result_json = ?,
          last_error_code = NULL, last_error_message = NULL, updated_at = ? WHERE id = ?
      `).run(idempotencyKey, requestHash, JSON.stringify(result), now, process.id);
    });
    this.events.publish(sessionId, { type: "workflow.state_changed", data: result });
    setImmediate(() => nextState === "PROCESS_PROVISIONING"
      ? void this.provision(sessionId, user)
      : void this.activate(sessionId, user));
    return result;
  }

  private async saveAgentRequirement(
    sessionId: string,
    requirement: BusinessRequirement,
    missingItems: string[],
    ambiguities: string[],
  ): Promise<RequirementSubmissionResult> {
    const session = this.database.getSession(sessionId);
    if (!session || session.state !== "COLLECTING") {
      return {
        accepted: false,
        action: "ASK_USER",
        missingItems: [],
        ambiguities: ["需求会话状态已变化，请刷新后继续"],
      };
    }
    const normalizedRequirement = applyRequirementDefaults(requirement);
    const validation = validateRequirement(normalizedRequirement);
    if (!validation.structurallyValid) {
      return {
        accepted: false,
        action: "ASK_USER",
        missingItems: [],
        ambiguities: [`需求结构不符合约束：${formatSchemaErrors(validation.schemaErrors)}`],
        schemaErrors: validation.schemaErrors,
      };
    }
    const mergedMissing = [...new Set([...missingItems, ...validation.missingItems])];
    const mergedAmbiguities = [...new Set([...ambiguities, ...validation.ambiguities])];
    let availableRoles: RegisteredRole[];
    try {
      availableRoles = await this.listRegisteredRoles(sessionId);
    } catch (error) {
      const roleLookupError = toErrorDescriptor(error);
      return {
        accepted: false,
        action: "ASK_USER",
        missingItems: mergedMissing,
        ambiguities: [...mergedAmbiguities, `无法读取角色中心：${roleLookupError.message}`],
        roleLookupError,
      };
    }
    const roleIssues = validateRequirementRoles(normalizedRequirement as BusinessRequirement, availableRoles);
    mergedMissing.push(...roleIssues);
    if (!validation.readyForReview || mergedMissing.length || mergedAmbiguities.length) {
      return {
        accepted: false,
        action: "ASK_USER",
        missingItems: [...new Set(mergedMissing)],
        ambiguities: mergedAmbiguities,
        availableRoles,
      };
    }
    const now = new Date().toISOString();
    this.database.db.prepare(`
      UPDATE agent_session SET state = 'REQUIREMENT_REVIEW', row_version = row_version + 1,
        requirement_revision = requirement_revision + 1, requirement_json = ?,
        requirement_missing_items_json = '[]', requirement_ambiguities_json = '[]',
        requirement_ready_for_review = 1, requirement_source = 'AGENT',
        last_error_code = NULL, last_error_message = NULL, updated_at = ?
      WHERE id = ? AND state = 'COLLECTING'
    `).run(JSON.stringify(normalizedRequirement), now, sessionId);
    this.events.publish(sessionId, { type: "requirement.ready", data: { revision: session.requirement_revision + 1 } });
    this.events.publish(sessionId, { type: "workflow.state_changed", data: { state: "REQUIREMENT_REVIEW" } });
    return { accepted: true };
  }

  private async listRegisteredRoles(sessionId: string): Promise<RegisteredRole[]> {
    const session = this.database.getSession(sessionId);
    if (!session) throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_SESSION_NOT_FOUND", "session not found", sessionId);
    const options = await this.platform.getOrganizationOptions({
      userId: session.owner_user_id,
      userName: session.owner_user_name,
      departmentId: session.owner_dept_id || undefined,
      departmentName: session.owner_dept_name || undefined,
    });
    const roles = Array.isArray(options?.roles) ? options.roles : [];
    const unique = new Map<string, RegisteredRole>();
    for (const role of roles) {
      if (!role || typeof role.roleCode !== "string" || !role.roleCode) continue;
      unique.set(role.roleCode, {
        roleCode: role.roleCode,
        roleName: typeof role.roleName === "string" && role.roleName ? role.roleName : role.roleCode,
      });
    }
    return [...unique.values()];
  }

  private async provision(sessionId: string, user: MockUser): Promise<void> {
    const process = this.requiredProcess(sessionId);
    const requirement = JSON.parse(process.requirement_snapshot_json) as BusinessRequirement;
    try {
      let definitionId = process.platform_definition_id;
      if (!definitionId) {
        const definition = await this.platform.createDefinition(requirement, user, process.create_operation_id);
        definitionId = definition.id || definition.definitionId;
        if (!definitionId) throw new Error("platform create response did not include definition id");
        this.updateProcess(process.id, {
          platform_definition_id: definitionId,
          definition_version: definition.version || null,
          saga_step: "DRAFT_CREATED",
        });
      }
      const templates = await this.platform.resolveAttachmentTemplates(requirement, user);
      this.updateProcess(process.id, { saga_step: "ATTACHMENTS_READY" });
      await this.platform.saveGraph(definitionId, requirement, templates, user, process.save_operation_id);
      this.updateProcess(process.id, { saga_step: "GRAPH_SAVED" });
      const validation = await this.platform.validate(definitionId, user);
      const snapshot = await this.platform.getDefinition(definitionId, user);
      const now = new Date().toISOString();
      this.database.transaction(() => {
        this.database.db.prepare(`
          UPDATE agent_process_definition SET status = 'VALIDATED', saga_step = 'VALIDATED',
            definition_version = ?, validation_json = ?, platform_snapshot_json = ?,
            last_error_code = NULL, last_error_message = NULL, updated_at = ? WHERE id = ?
        `).run(snapshot.version || null, JSON.stringify(validation), JSON.stringify(snapshot), now, process.id);
        this.database.db.prepare(`
          UPDATE agent_session SET state = 'PROCESS_REVIEW', row_version = row_version + 1,
            last_error_code = NULL, last_error_message = NULL, updated_at = ? WHERE id = ?
        `).run(now, sessionId);
      });
      this.events.publish(sessionId, { type: "process.validation_completed", data: validation });
      this.events.publish(sessionId, { type: "workflow.state_changed", data: { state: "PROCESS_REVIEW" } });
    } catch (error) {
      this.failProcess(sessionId, process.id, "PROCESS_PROVISION_FAILED", error);
    }
  }

  private async activate(sessionId: string, user: MockUser): Promise<void> {
    const process = this.requiredProcess(sessionId);
    const definitionId = process.platform_definition_id;
    if (!definitionId) {
      this.failProcess(sessionId, process.id, "PROCESS_ACTIVATION_FAILED", new Error("platform definition id is missing"));
      return;
    }
    try {
      const validation = await this.platform.validate(definitionId, user);
      if (!validation.valid) {
        const snapshot = await this.platform.getDefinition(definitionId, user);
        const now = new Date().toISOString();
        this.database.transaction(() => {
          this.database.db.prepare(`
            UPDATE agent_process_definition SET status = 'VALIDATED', saga_step = 'VALIDATED',
              validation_json = ?, platform_snapshot_json = ?, updated_at = ? WHERE id = ?
          `).run(JSON.stringify(validation), JSON.stringify(snapshot), now, process.id);
          this.database.db.prepare(`
            UPDATE agent_session SET state = 'PROCESS_REVIEW', row_version = row_version + 1,
              last_error_code = 'FLOW_VALIDATION_FAILED',
              last_error_message = 'Publish validation failed; review the blocking issues.', updated_at = ? WHERE id = ?
          `).run(now, sessionId);
        });
        this.events.publish(sessionId, { type: "process.validation_completed", data: validation });
        this.events.publish(sessionId, { type: "workflow.state_changed", data: { state: "PROCESS_REVIEW" } });
        return;
      }
      if (process.status !== "PUBLISHED") {
        await this.platform.publish(definitionId, user, process.publish_operation_id);
        this.updateProcess(process.id, { status: "PUBLISHED", saga_step: "PUBLISHED" });
      }
      await this.platform.activate(definitionId, user, process.activate_operation_id);
      const snapshot = await this.platform.getDefinition(definitionId, user);
      const now = new Date().toISOString();
      this.database.transaction(() => {
        this.database.db.prepare(`
          UPDATE agent_process_definition SET status = 'SUPERSEDED', saga_step = 'SUPERSEDED', updated_at = ?
          WHERE process_code = ? AND id <> ? AND status = 'ACTIVE'
        `).run(now, process.process_code, process.id);
        this.database.db.prepare(`
          UPDATE agent_process_definition SET status = 'ACTIVE', saga_step = 'ACTIVE',
            platform_snapshot_json = ?, validation_json = ?, definition_version = ?,
            activated_at = ?, last_error_code = NULL, last_error_message = NULL, updated_at = ? WHERE id = ?
        `).run(JSON.stringify(snapshot), JSON.stringify(validation), snapshot.version || null, now, now, process.id);
        this.database.db.prepare(`
          UPDATE agent_session SET state = 'PROCESS_ACTIVE', row_version = row_version + 1,
            last_error_code = NULL, last_error_message = NULL, updated_at = ? WHERE id = ?
        `).run(now, sessionId);
      });
      this.events.publish(sessionId, { type: "workflow.state_changed", data: { state: "PROCESS_ACTIVE" } });
    } catch (error) {
      this.failProcess(sessionId, process.id, "PROCESS_ACTIVATION_FAILED", error);
    }
  }

  private failProcess(sessionId: string, processId: string, state: WorkflowState, error: unknown): void {
    const code = error instanceof AgentError
      ? ((error.getResponse() as any).code || "FLOW_PLATFORM_ERROR")
      : "FLOW_PLATFORM_ERROR";
    const message = error instanceof Error ? error.message : String(error);
    const now = new Date().toISOString();
    this.database.transaction(() => {
      this.database.db.prepare(`
        UPDATE agent_process_definition SET last_error_code = ?, last_error_message = ?, updated_at = ? WHERE id = ?
      `).run(code, message, now, processId);
      this.database.db.prepare(`
        UPDATE agent_session SET state = ?, row_version = row_version + 1,
          last_error_code = ?, last_error_message = ?, updated_at = ? WHERE id = ?
      `).run(state, code, message, now, sessionId);
    });
    this.events.publish(sessionId, { type: "error", data: { code, message } });
    this.events.publish(sessionId, { type: "workflow.state_changed", data: { state } });
  }

  private updateProcess(id: string, values: Record<string, unknown>): void {
    const entries = Object.entries(values);
    const sql = entries.map(([key]) => `${key} = ?`).join(", ");
    this.database.db.prepare(`UPDATE agent_process_definition SET ${sql}, updated_at = ? WHERE id = ?`)
      .run(...entries.map(([, value]) => value), new Date().toISOString(), id);
  }

  private callbacks(sessionId: string): PiCallbacks {
    return {
      onEvent: (type, data) => this.events.publish(sessionId, { type, data }),
      onError: (code, message) => {
        this.setSessionError(sessionId, code, message);
        this.events.publish(sessionId, { type: "error", data: { code, message } });
      },
      listRegisteredRoles: () => this.listRegisteredRoles(sessionId),
      onRequirement: (requirement, missingItems, ambiguities) =>
        this.saveAgentRequirement(sessionId, requirement, missingItems, ambiguities),
    };
  }

  private ownedSession(sessionId: string, user: MockUser): SessionRow {
    const session = this.database.getSession(sessionId);
    if (!session) throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_SESSION_NOT_FOUND", "session not found", sessionId);
    if (session.owner_user_id !== user.userId) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_SESSION_FORBIDDEN", "session belongs to another user", sessionId);
    }
    return session;
  }

  private requiredProcess(sessionId: string): ProcessRow {
    const process = this.database.getProcessBySession(sessionId);
    if (!process) throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_PROCESS_NOT_FOUND", "process record not found", sessionId);
    return process;
  }

  private expectVersion(session: SessionRow, expected: number): void {
    if (!Number.isInteger(expected) || session.row_version !== expected) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_ROW_VERSION_CONFLICT", "row version is stale", session.id, {
        expected: session.row_version,
      });
    }
  }

  private expectState(session: SessionRow, allowed: WorkflowState[]): void {
    if (!allowed.includes(session.state)) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_STATE_CONFLICT", "current state does not allow this operation", session.id, {
        currentState: session.state,
        allowedStates: allowed,
      });
    }
  }

  private setSessionError(sessionId: string, code: string, message: string): void {
    this.database.db.prepare(`
      UPDATE agent_session SET last_error_code = ?, last_error_message = ?, updated_at = ? WHERE id = ?
    `).run(code, message, new Date().toISOString(), sessionId);
  }

  private clearSessionError(sessionId: string): void {
    this.database.db.prepare(`
      UPDATE agent_session SET last_error_code = NULL, last_error_message = NULL, updated_at = ? WHERE id = ?
    `).run(new Date().toISOString(), sessionId);
  }

  private async publishSnapshot(sessionId: string, user: MockUser): Promise<void> {
    this.events.publish(sessionId, { type: "workflow.snapshot", data: await this.getSnapshot(sessionId, user) });
  }
}

function formatSchemaErrors(errors: Array<{ instancePath?: string; message?: string; params?: Record<string, unknown> }>): string {
  return errors.slice(0, 12).map((error) => {
    const missingProperty = typeof error.params?.missingProperty === "string"
      ? `/${error.params.missingProperty}`
      : "";
    const path = `${error.instancePath || "requirement"}${missingProperty}`;
    return `${path}: ${error.message || "schema validation failed"}`;
  }).join("; ") || "schema validation failed";
}

function validateRequirementRoles(
  requirement: BusinessRequirement,
  availableRoles: RegisteredRole[],
): string[] {
  const participantCodes = new Set(requirement.participants.map(({ roleCode }) => roleCode));
  const registeredCodes = new Set(availableRoles.map(({ roleCode }) => roleCode));
  const approverRoleCodes = new Set<string>();
  const issues: string[] = [];
  for (const node of requirement.nodes) {
    if (node.approverRule?.type !== "ROLE" && node.approverRule?.type !== "ROLE_IN_DEPARTMENT") continue;
    const roleCode = node.approverRule.config?.roleCode;
    if (typeof roleCode !== "string" || !roleCode) continue;
    approverRoleCodes.add(roleCode);
    if (!participantCodes.has(roleCode)) {
      issues.push(`审批角色 ${roleCode} 未在参与角色列表中`);
    }
  }
  const unregistered = [...approverRoleCodes].filter((roleCode) => !registeredCodes.has(roleCode));
  if (unregistered.length) {
    issues.push(`以下角色编码需在角色中心预先注册后方可提交：${unregistered.join("、")}`);
  }
  return issues;
}

function toErrorDescriptor(error: unknown): { code: string; message: string } {
  if (error instanceof AgentError) {
    const response = error.getResponse();
    if (response && typeof response === "object") {
      const body = response as { code?: unknown; message?: unknown };
      return {
        code: typeof body.code === "string" ? body.code : "FLOW_PLATFORM_ERROR",
        message: typeof body.message === "string" ? body.message : error.message,
      };
    }
  }
  return {
    code: "FLOW_PLATFORM_ERROR",
    message: error instanceof Error ? error.message : String(error),
  };
}

function normalizeOptionalTarget(targetRoot?: string): string | undefined {
  if (!targetRoot?.trim()) return undefined;
  const value = targetRoot.trim();
  if (!isAbsolute(value)) {
    throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_ROOT_INVALID", "targetRoot must be an absolute path");
  }
  return normalize(resolve(value));
}

function toRequirementRevision(session: SessionRow): RequirementRevision | undefined {
  if (!session.requirement_json) return undefined;
  return {
    sessionId: session.id,
    revision: session.requirement_revision,
    requirement: JSON.parse(session.requirement_json),
    missingItems: parseJson(session.requirement_missing_items_json, []),
    ambiguities: parseJson(session.requirement_ambiguities_json, []),
    readyForReview: Boolean(session.requirement_ready_for_review),
    source: session.requirement_source as "AGENT" | "USER_EDIT",
    confirmedBy: session.requirement_confirmed_at ? session.owner_user_id : undefined,
    confirmedAt: session.requirement_confirmed_at || undefined,
    createdAt: session.updated_at,
  };
}

/**
 * Applies product defaults at both submission boundaries.  The prompt asks the
 * model to emit these values, while this guard also covers older sessions and
 * manual edits that omit optional node policies.
 */
function applyRequirementDefaults(input: unknown): unknown {
  if (!input || typeof input !== "object" || Array.isArray(input)) return input;
  const requirement = { ...(input as Record<string, unknown>) };
  if (typeof requirement.systemCode !== "string" || !requirement.systemCode.trim()) {
    requirement.systemCode = DEFAULT_SYSTEM_CODE;
  } else {
    requirement.systemCode = requirement.systemCode.trim();
  }
  if (Array.isArray(requirement.nodes)) {
    const userTaskNodeCodes = requirement.nodes
      .filter((value) => value && typeof value === "object" && !Array.isArray(value)
        && (value as Record<string, unknown>).nodeType === "USER_TASK")
      .map((value) => String((value as Record<string, unknown>).nodeCode || ""))
      .filter(Boolean);
    const firstUserTaskNodeCode = userTaskNodeCodes[0];
    requirement.nodes = requirement.nodes.map((value) => {
      if (!value || typeof value !== "object" || Array.isArray(value)) return value;
      const node = { ...(value as Record<string, unknown>) };
      if (node.nodeType !== "USER_TASK") return node;
      const nodeCode = String(node.nodeCode || "");
      const defaults = createDefaultUserTaskConfigs({
        rejectEnabled: Boolean(nodeCode && nodeCode !== firstUserTaskNodeCode),
        rejectTargetNodeCodes: userTaskNodeCodes,
      });
      if (!("listenerConfig" in node)) node.listenerConfig = defaults.listenerConfig;
      if (!("timeoutConfig" in node)) node.timeoutConfig = defaults.timeoutConfig;
      if (!("reminderConfig" in node)) node.reminderConfig = defaults.reminderConfig;
      return node;
    });
  }
  return requirement;
}

function toProcessPreview(process: ProcessRow): ProcessPreview | undefined {
  if (!process.platform_definition_id || !process.platform_snapshot_json) return undefined;
  const snapshot = parseJson<Record<string, any>>(process.platform_snapshot_json, {});
  const validation = parseJson(process.validation_json, { valid: false, issues: [] });
  return {
    platformDefinitionId: process.platform_definition_id,
    processCode: process.process_code,
    processName: process.process_name,
    definitionVersion: process.definition_version || undefined,
    definitionStatus: snapshot.definitionStatus,
    activationStatus: snapshot.activationStatus,
    nodes: snapshot.nodes || [],
    edges: snapshot.edges || [],
    formFields: snapshot.formFields || [],
    attachmentTemplates: snapshot.attachmentTemplates || [],
    validation,
  };
}

const resettableStates: WorkflowState[] = [
  "COLLECTING",
  "REQUIREMENT_REVIEW",
  "PROCESS_PROVISION_FAILED",
  "PROCESS_REVIEW",
  "PROCESS_ACTIVATION_FAILED",
  "PROCESS_ACTIVE",
  "CODE_REVIEW",
  "CODE_PIPELINE_FAILED",
  "COMPLETED",
];

function hasProcessPreview(state: WorkflowState): boolean {
  return [
    "PROCESS_REVIEW", "PROCESS_ACTIVATING", "PROCESS_ACTIVATION_FAILED", "PROCESS_ACTIVE",
    "CODE_GENERATING", "CODE_VERIFYING", "CODE_REVIEWING", "CODE_REPAIRING",
    "CODE_REVIEW", "CODE_PIPELINE_FAILED", "WRITING_ARTIFACTS", "ARTIFACT_WRITE_FAILED",
    "BUSINESS_ENTRY_CONFIGURING", "BUSINESS_ENTRY_CONFIG_FAILED", "COMPLETED",
  ].includes(state);
}

function hasGenerationPreview(state: WorkflowState): boolean {
  return [
    "CODE_GENERATING", "CODE_VERIFYING", "CODE_REVIEWING", "CODE_REPAIRING",
    "CODE_REVIEW", "CODE_PIPELINE_FAILED", "WRITING_ARTIFACTS", "ARTIFACT_WRITE_FAILED",
    "BUSINESS_ENTRY_CONFIGURING", "BUSINESS_ENTRY_CONFIG_FAILED", "COMPLETED",
  ].includes(state);
}

function allowedActions(state: WorkflowState, validationPassed: boolean): string[] {
  const mapping: Record<WorkflowState, string[]> = {
    COLLECTING: ["SEND_MESSAGE"],
    REQUIREMENT_REVIEW: ["EDIT_REQUIREMENT", "CONFIRM_REQUIREMENT", "REOPEN_REQUIREMENT"],
    PROCESS_PROVISIONING: [],
    PROCESS_PROVISION_FAILED: ["RETRY_PROCESS"],
    PROCESS_REVIEW: validationPassed ? ["CONFIRM_PROCESS"] : [],
    PROCESS_ACTIVATING: [],
    PROCESS_ACTIVATION_FAILED: ["RETRY_PROCESS"],
    PROCESS_ACTIVE: [],
    CODE_GENERATING: ["CANCEL_GENERATION"],
    CODE_VERIFYING: ["STOP_QUALITY"],
    CODE_REVIEWING: ["STOP_QUALITY"],
    CODE_REPAIRING: ["STOP_QUALITY"],
    CODE_REVIEW: ["EDIT_GENERATED_FILE", "REGENERATE", "REVERIFY", "OVERRIDE_QUALITY", "CONFIRM_WRITE"],
    CODE_PIPELINE_FAILED: ["EDIT_GENERATED_FILE", "REGENERATE", "REVERIFY"],
    WRITING_ARTIFACTS: [],
    ARTIFACT_WRITE_FAILED: ["CONFIRM_WRITE"],
    BUSINESS_ENTRY_CONFIGURING: [],
    BUSINESS_ENTRY_CONFIG_FAILED: ["CONFIRM_WRITE"],
    COMPLETED: [],
  };
  if (state === "PROCESS_ACTIVE") mapping.PROCESS_ACTIVE = ["START_GENERATION"];
  return resettableStates.includes(state) ? [...mapping[state], "RESET_SESSION"] : mapping[state];
}

function parseJson<T>(value: string | null, fallback: T): T {
  if (!value) return fallback;
  try { return JSON.parse(value) as T; } catch { return fallback; }
}

function sessionBusinessName(requirementJson: string | null): string {
  const requirement = parseJson<Record<string, unknown> | undefined>(requirementJson, undefined);
  return typeof requirement?.businessName === "string" && requirement.businessName.trim()
    ? requirement.businessName.trim()
    : "未命名需求";
}

function hash(value: unknown): string {
  return createHash("sha256").update(JSON.stringify(sortValue(value))).digest("hex");
}

function sortValue(value: any): any {
  if (Array.isArray(value)) return value.map(sortValue);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.keys(value).sort().map((key) => [key, sortValue(value[key])]));
  }
  return value;
}

function requireIdempotencyKey(key: string, sessionId: string): void {
  if (!key?.trim()) throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required", sessionId);
}

function checkReplay<T>(
  savedKey: string | null,
  savedHash: string | null,
  savedResult: string | null,
  key: string,
  requestHash: string,
  sessionId: string,
): T | undefined {
  if (!savedKey || savedKey !== key) return undefined;
  if (savedHash !== requestHash) {
    throw new AgentError(HttpStatus.CONFLICT, "AGENT_IDEMPOTENCY_CONFLICT", "idempotency key was reused with different content", sessionId);
  }
  return savedResult ? JSON.parse(savedResult) as T : undefined;
}
