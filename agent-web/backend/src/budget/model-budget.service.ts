import { HttpStatus, Inject, Injectable } from "@nestjs/common";
import { createHash, randomUUID } from "node:crypto";
import type {
  MockUser,
  ModelBudgetAuthorizationRequest,
  ModelBudgetPurpose,
  ModelBudgetStatus,
} from "@flowmind/agent-contracts";
import { AgentError } from "../common/agent-error.js";
import { loadConfig } from "../config.js";
import { DatabaseService } from "../persistence/database.service.js";

interface AuthorizationRow {
  id: string;
  owner_user_id: string;
  purpose: ModelBudgetPurpose;
  scope_id: string;
  max_micros_cny: number;
  reserved_micros_cny: number;
  spent_micros_cny: number;
  status: "ACTIVE" | "EXHAUSTED" | "REVOKED";
  expires_at: string;
}

const MICROS_PER_CNY = 1_000_000;

@Injectable()
export class ModelBudgetService {
  private readonly config = loadConfig();

  constructor(@Inject(DatabaseService) private readonly database: DatabaseService) {}

  authorize(user: MockUser, request: ModelBudgetAuthorizationRequest, idempotencyKey: string) {
    if (!idempotencyKey?.trim()) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_IDEMPOTENCY_KEY_REQUIRED", "idempotency-key is required");
    }
    const allowedPurposes = new Set<ModelBudgetPurpose>(["REQUIREMENT", "GENERATOR", "REVIEWER", "REPAIR", "COMPACTION"]);
    if (!request?.scopeId?.trim() || !allowedPurposes.has(request?.purpose)
      || !Number.isFinite(request.maxCny) || request.maxCny <= 0) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_MODEL_BUDGET_AUTH_INVALID", "purpose, scopeId and a positive maxCny are required");
    }
    const maxMicros = Math.ceil(request.maxCny * MICROS_PER_CNY);
    const totalMicros = Math.floor(this.config.modelBudgetTotalCny * MICROS_PER_CNY);
    if (maxMicros > totalMicros) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_MODEL_BUDGET_LIMIT", "authorization exceeds the one-time model budget");
    }
    const requestHash = sha256(JSON.stringify({
      purpose: request.purpose,
      scopeId: request.scopeId.trim(),
      maxMicros,
      expiresInMinutes: request.expiresInMinutes || 30,
    }));
    const replay = this.database.db.prepare(`
      SELECT id, request_hash FROM agent_model_budget_authorization
      WHERE owner_user_id = ? AND idempotency_key = ?
    `).get(user.userId, idempotencyKey.trim()) as { id: string; request_hash: string } | undefined;
    if (replay) {
      if (replay.request_hash !== requestHash) {
        throw new AgentError(HttpStatus.CONFLICT, "AGENT_IDEMPOTENCY_CONFLICT", "idempotency key was reused with different budget parameters");
      }
      return { authorizationId: replay.id, ...this.status(user) };
    }
    const id = `mba_${randomUUID()}`;
    const now = new Date();
    const expiresAt = new Date(now.getTime() + Math.min(Math.max(request.expiresInMinutes || 30, 1), 1440) * 60_000);
    this.database.db.prepare(`
      INSERT INTO agent_model_budget_authorization (
        id, owner_user_id, purpose, scope_id, max_micros_cny, status,
        idempotency_key, request_hash, created_at, expires_at
      ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
    `).run(
      id, user.userId, request.purpose, request.scopeId.trim(), maxMicros,
      idempotencyKey.trim(), requestHash, now.toISOString(), expiresAt.toISOString(),
    );
    return { authorizationId: id, ...this.status(user) };
  }

  reserve(
    ownerUserId: string,
    purpose: ModelBudgetPurpose,
    scopeId: string,
    prompt: string,
  ): { reservationId: string; reservedCny: number } {
    if (!this.config.piModel || this.config.modelPriceVersion === "UNCONFIGURED"
      || this.config.modelPriceInputCnyPerMillion <= 0 || this.config.modelPriceOutputCnyPerMillion <= 0) {
      throw new AgentError(HttpStatus.PAYMENT_REQUIRED, "AGENT_MODEL_BUDGET_UNCONFIGURED", "model usage or versioned CNY pricing is unavailable");
    }
    const estimatedInputTokens = Math.max(1, Math.ceil(prompt.length / 4));
    const reservedMicros = Math.ceil(
      estimatedInputTokens * this.config.modelPriceInputCnyPerMillion
      + this.config.modelMaxOutputTokens * this.config.modelPriceOutputCnyPerMillion,
    );
    const reservationId = `mbr_${randomUUID()}`;
    this.database.transaction(() => {
      const now = new Date().toISOString();
      const authorization = this.database.db.prepare(`
        SELECT * FROM agent_model_budget_authorization
        WHERE owner_user_id = ? AND purpose = ? AND scope_id = ? AND status = 'ACTIVE' AND expires_at > ?
        ORDER BY created_at DESC LIMIT 1
      `).get(ownerUserId, purpose, scopeId, now) as AuthorizationRow | undefined;
      if (!authorization) {
        throw new AgentError(HttpStatus.PAYMENT_REQUIRED, "AGENT_MODEL_BUDGET_LOCKED", "this paid model call has not been explicitly authorized");
      }
      const global = this.database.db.prepare(`
        SELECT COALESCE(SUM(reserved_micros_cny), 0) AS reserved,
          COALESCE(SUM(spent_micros_cny), 0) AS spent
        FROM agent_model_budget_authorization
      `).get() as { reserved: number; spent: number };
      const totalMicros = Math.floor(this.config.modelBudgetTotalCny * MICROS_PER_CNY);
      if (global.reserved + global.spent + reservedMicros > totalMicros
        || authorization.reserved_micros_cny + authorization.spent_micros_cny + reservedMicros > authorization.max_micros_cny) {
        throw new AgentError(HttpStatus.PAYMENT_REQUIRED, "AGENT_MODEL_BUDGET_EXHAUSTED", "the one-time model budget is exhausted");
      }
      this.database.db.prepare(`
        INSERT INTO agent_model_budget_reservation (
          id, authorization_id, owner_user_id, purpose, scope_id, provider_model,
          price_version, prompt_sha256, estimated_input_tokens, max_output_tokens,
          reserved_micros_cny, status, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RESERVED', ?)
      `).run(
        reservationId, authorization.id, ownerUserId, purpose, scopeId, this.config.piModel,
        this.config.modelPriceVersion, sha256(prompt), estimatedInputTokens,
        this.config.modelMaxOutputTokens, reservedMicros, now,
      );
      this.database.db.prepare(`
        UPDATE agent_model_budget_authorization
        SET reserved_micros_cny = reserved_micros_cny + ?
        WHERE id = ?
      `).run(reservedMicros, authorization.id);
    });
    return { reservationId, reservedCny: reservedMicros / MICROS_PER_CNY };
  }

  markBillingOutcomeUnknown(reservationId: string): void {
    this.database.db.prepare(`
      UPDATE agent_model_budget_reservation SET status = 'UNKNOWN', settled_at = ?
      WHERE id = ? AND status = 'RESERVED'
    `).run(new Date().toISOString(), reservationId);
  }

  hasActiveAuthorization(ownerUserId: string, purpose: ModelBudgetPurpose, scopeId: string): boolean {
    const row = this.database.db.prepare(`
      SELECT id FROM agent_model_budget_authorization
      WHERE owner_user_id = ? AND purpose = ? AND scope_id = ?
        AND status = 'ACTIVE' AND expires_at > ?
      LIMIT 1
    `).get(ownerUserId, purpose, scopeId, new Date().toISOString());
    return Boolean(row);
  }

  status(user: MockUser): ModelBudgetStatus {
    const rows = this.database.db.prepare(`
      SELECT * FROM agent_model_budget_authorization WHERE owner_user_id = ? ORDER BY created_at DESC
    `).all(user.userId) as AuthorizationRow[];
    const global = this.database.db.prepare(`
      SELECT COALESCE(SUM(reserved_micros_cny), 0) AS reserved,
        COALESCE(SUM(spent_micros_cny), 0) AS spent
      FROM agent_model_budget_authorization
    `).get() as { reserved: number; spent: number };
    const total = this.config.modelBudgetTotalCny;
    return {
      totalLimitCny: total,
      reservedCny: global.reserved / MICROS_PER_CNY,
      spentCny: global.spent / MICROS_PER_CNY,
      remainingCny: Math.max(0, total - (global.reserved + global.spent) / MICROS_PER_CNY),
      locked: this.config.modelBudgetLocked,
      authorizations: rows.map((row) => ({
        id: row.id,
        purpose: row.purpose,
        scopeId: row.scope_id,
        maxCny: row.max_micros_cny / MICROS_PER_CNY,
        reservedCny: row.reserved_micros_cny / MICROS_PER_CNY,
        spentCny: row.spent_micros_cny / MICROS_PER_CNY,
        status: row.status,
        expiresAt: row.expires_at,
      })),
    };
  }
}

function sha256(value: string): string {
  return createHash("sha256").update(value).digest("hex");
}
