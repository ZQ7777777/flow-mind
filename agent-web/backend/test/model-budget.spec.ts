import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdtempSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { DatabaseService } from "../src/persistence/database.service.js";
import { ModelBudgetService } from "../src/budget/model-budget.service.js";

describe("one-time CNY model budget", () => {
  let root: string;
  let database: DatabaseService;
  let budget: ModelBudgetService;
  const user = { userId: "user_sales", userName: "Sales User" };

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), "flowmind-budget-"));
    process.env.AGENT_DATA_DIR = root;
    process.env.AGENT_DB_PATH = join(root, "agent.db");
    process.env.PI_MODEL = "test/model";
    process.env.AGENT_MODEL_BUDGET_CNY = "10";
    process.env.AGENT_MODEL_INPUT_CNY_PER_MILLION = "2";
    process.env.AGENT_MODEL_OUTPUT_CNY_PER_MILLION = "4";
    process.env.AGENT_MODEL_PRICE_VERSION = "TEST_PRICE_V1";
    process.env.AGENT_MODEL_MAX_OUTPUT_TOKENS = "1000";
    database = new DatabaseService();
    budget = new ModelBudgetService(database);
  });

  afterEach(() => {
    database.onModuleDestroy();
    rmSync(root, { recursive: true, force: true });
    for (const name of [
      "AGENT_DATA_DIR", "AGENT_DB_PATH", "PI_MODEL", "AGENT_MODEL_BUDGET_CNY",
      "AGENT_MODEL_INPUT_CNY_PER_MILLION", "AGENT_MODEL_OUTPUT_CNY_PER_MILLION",
      "AGENT_MODEL_PRICE_VERSION", "AGENT_MODEL_MAX_OUTPUT_TOKENS",
    ]) delete process.env[name];
  });

  it("fails closed before a paid call is explicitly authorized", () => {
    expect(() => budget.reserve(user.userId, "REQUIREMENT", "session-1", "secret prompt"))
      .toThrow(/not been explicitly authorized/);
  });

  it("reserves worst-case CNY without persisting prompt content", () => {
    budget.authorize(user, {
      purpose: "REQUIREMENT",
      scopeId: "session-1",
      maxCny: 1,
    }, "authorize-session-1");
    const reservation = budget.reserve(user.userId, "REQUIREMENT", "session-1", "sensitive requirement text");
    budget.markBillingOutcomeUnknown(reservation.reservationId);

    expect(reservation.reservedCny).toBeGreaterThan(0);
    const row = database.db.prepare(`
      SELECT prompt_sha256, status FROM agent_model_budget_reservation WHERE id = ?
    `).get(reservation.reservationId) as { prompt_sha256: string; status: string };
    expect(row.prompt_sha256).toMatch(/^[a-f0-9]{64}$/);
    expect(row.status).toBe("UNKNOWN");
    expect(JSON.stringify(row)).not.toContain("sensitive requirement text");
    expect(budget.status(user).remainingCny).toBeLessThan(10);
  });

  it("replays an authorization idempotently and rejects changed parameters", () => {
    const first = budget.authorize(user, {
      purpose: "REVIEWER",
      scopeId: "generation-1",
      maxCny: 1,
    }, "same-key");
    const replay = budget.authorize(user, {
      purpose: "REVIEWER",
      scopeId: "generation-1",
      maxCny: 1,
    }, "same-key");
    expect(replay.authorizationId).toBe(first.authorizationId);
    expect(() => budget.authorize(user, {
      purpose: "REVIEWER",
      scopeId: "generation-1",
      maxCny: 2,
    }, "same-key")).toThrow(/reused with different budget parameters/);
  });
});
