import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DatabaseService } from "../src/persistence/database.service.js";
import { EventBusService } from "../src/workflow/event-bus.service.js";
import { GenerationService } from "../src/generation/generation.service.js";
import { StagingService } from "../src/generation/staging.service.js";
import { TargetContractService } from "../src/generation/target-contract.service.js";
import { PiAdapterService } from "../src/pi/pi-adapter.service.js";
import { createGenerationTarget } from "./generation-fixture.js";

describe("tester quality-gate fixture", () => {
  let root: string;
  let database: DatabaseService;
  let generation: GenerationService;

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), "flowmind-tester-fixture-"));
    process.env.AGENT_DATA_DIR = join(root, "data");
    process.env.AGENT_DB_PATH = join(root, "agent.db");
    process.env.AGENT_ALLOWED_TARGET_ROOTS = root;
    database = new DatabaseService();
    generation = new GenerationService(
      database,
      new TargetContractService(),
      new StagingService(database),
      new PiAdapterService(database),
      new EventBusService(),
    );
  });

  afterEach(() => {
    database.onModuleDestroy();
    rmSync(root, { recursive: true, force: true });
    delete process.env.AGENT_DATA_DIR; delete process.env.AGENT_DB_PATH; delete process.env.AGENT_ALLOWED_TARGET_ROOTS;
  });

  it("creates a frozen quality-gate baseline without any sales session", () => {
    const target = createGenerationTarget(root);
    const fixture = generation.createTesterQualityFixture(
      { userId: "user_tester", userName: "Quality Tester" },
      target,
    );

    const summary = generation.get(fixture.sessionId, fixture.generationId, { userId: "user_tester", userName: "Quality Tester" });
    const fixtureProcess = database.getProcessBySession(fixture.sessionId)!;
    expect(database.getSession(fixture.sessionId)?.state).toBe("CODE_REVIEW");
    expect(summary).toEqual(expect.objectContaining({ status: "REVIEW", generationRevision: 1 }));
    expect(summary.manifest?.files).toHaveLength(11);
    expect(JSON.parse(fixtureProcess.validation_json || "{}")).toEqual({ valid: true, issues: [] });
    expect(fixtureProcess.platform_snapshot_json).not.toBeNull();
    const fixtureCode = generation.readFile(
      fixture.sessionId,
      fixture.generationId,
      "backend/src/main/java/com/flowmind/business/generated/entryapplication/EntryApplicationService.java",
      { userId: "user_tester", userName: "Quality Tester" },
    ).content;
    expect(fixtureCode).toContain("request.setProcessVariables(payload.toProcessVariables())");
  });
});
