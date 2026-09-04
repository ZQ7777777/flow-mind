import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdtempSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { ENTRY_APPLICATION_REQUIREMENT, WAREHOUSE_PLEDGE_REQUIREMENT } from "@flowmind/agent-contracts";
import { DatabaseService } from "../src/persistence/database.service.js";
import { EventBusService } from "../src/workflow/event-bus.service.js";
import { PiAdapterService } from "../src/pi/pi-adapter.service.js";
import { TargetContractService } from "../src/generation/target-contract.service.js";
import { StagingService } from "../src/generation/staging.service.js";
import { GenerationService } from "../src/generation/generation.service.js";
import { deriveGenerationSpec } from "../src/generation/generation-spec.js";
import { createGenerationTarget, seedActiveWorkflow } from "./generation-fixture.js";

describe("deterministic IR generation strategy", () => {
  let parent: string;
  let database: DatabaseService;
  let generation: GenerationService;
  const user = { userId: "user_sales", userName: "Sales User", departmentId: "sales_dept" };

  beforeEach(() => {
    parent = mkdtempSync(join(tmpdir(), "flowmind-deterministic-"));
    process.env.AGENT_DATA_DIR = join(parent, "data");
    process.env.AGENT_DB_PATH = join(parent, "agent.db");
    process.env.AGENT_ALLOWED_TARGET_ROOTS = parent;
    process.env.AGENT_FAKE_PI = "true";
    process.env.AGENT_GENERATION_STRATEGY = "DETERMINISTIC_IR_V1";
    database = new DatabaseService();
    const targets = new TargetContractService();
    generation = new GenerationService(
      database,
      targets,
      new StagingService(database),
      new PiAdapterService(database),
      new EventBusService(),
    );
  });

  afterEach(() => {
    database.onModuleDestroy();
    rmSync(parent, { recursive: true, force: true });
    delete process.env.AGENT_DATA_DIR;
    delete process.env.AGENT_DB_PATH;
    delete process.env.AGENT_ALLOWED_TARGET_ROOTS;
    delete process.env.AGENT_FAKE_PI;
    delete process.env.AGENT_GENERATION_STRATEGY;
  });

  it("creates the standard module without opening a Pi generation session", async () => {
    const target = createGenerationTarget(parent);
    const requirement = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    seedActiveWorkflow(database, "session-deterministic", null, "user_sales", requirement);

    const started = generation.start("session-deterministic", user, 0, "start-deterministic", target);
    const row = await waitForReview(started.generationId);

    expect(row.generation_strategy).toBe("DETERMINISTIC_IR_V1");
    expect(row.pi_session_id).toBeNull();
    const summary = generation.get("session-deterministic", started.generationId, user);
    expect(summary.generationStrategy).toBe("DETERMINISTIC_IR_V1");
    expect(summary.manifest?.files).toHaveLength(5);
  });

  it("renders dynamic data, calculations and checks from the IR", async () => {
    const target = createGenerationTarget(parent);
    const requirement = structuredClone(WAREHOUSE_PLEDGE_REQUIREMENT);
    seedActiveWorkflow(database, "session-composite", null, "user_sales", requirement);

    const started = generation.start("session-composite", user, 0, "start-composite", target);
    const row = await waitForReview(started.generationId);
    const contract = JSON.parse(row.target_contract_json);
    const spec = deriveGenerationSpec(requirement, contract);
    const form = generation.readFile("session-composite", started.generationId, spec.paths.businessForm, user).content;
    const api = generation.readFile("session-composite", started.generationId, spec.paths.api, user).content;

    expect(form).toContain("calculateAmount");
    expect(form).toContain("loadFunds");
    expect(api).toContain("loadAccountFunds");
    expect(row.pi_session_id).toBeNull();
  });

  async function waitForReview(generationId: string) {
    for (let attempt = 0; attempt < 100; attempt += 1) {
      const row = database.getGeneration(generationId)!;
      if (row.status === "REVIEW") return row;
      if (row.status === "FAILED") throw new Error(`${row.last_error_code}: ${row.last_error_message}`);
      await new Promise((resolve) => setTimeout(resolve, 5));
    }
    throw new Error("deterministic generation did not finish");
  }
});
