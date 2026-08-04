import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { existsSync, mkdtempSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { DatabaseService } from "../src/persistence/database.service.js";
import { EventBusService } from "../src/workflow/event-bus.service.js";
import { PiAdapterService } from "../src/pi/pi-adapter.service.js";
import { TargetContractService } from "../src/generation/target-contract.service.js";
import { StagingService } from "../src/generation/staging.service.js";
import { GenerationService } from "../src/generation/generation.service.js";
import { ENTRY_APPLICATION_FILES } from "../src/generation/generation.constants.js";
import { createGenerationTarget, seedActiveWorkflow } from "./generation-fixture.js";

describe("M3 entry application generation", () => {
  let parent: string;
  let database: DatabaseService;
  let generation: GenerationService;
  const user = { userId: "user_sales", userName: "Sales User", departmentId: "sales_dept" };

  beforeEach(() => {
    parent = mkdtempSync(join(tmpdir(), "flowmind-m3-"));
    process.env.AGENT_DATA_DIR = join(parent, "data");
    process.env.AGENT_DB_PATH = join(parent, "agent.db");
    process.env.AGENT_ALLOWED_TARGET_ROOTS = parent;
    process.env.AGENT_FAKE_PI = "true";
    database = new DatabaseService();
    const events = new EventBusService();
    const pi = new PiAdapterService(database);
    const targets = new TargetContractService();
    const staging = new StagingService(database);
    generation = new GenerationService(database, targets, staging, pi, events);
  });

  afterEach(() => {
    database.onModuleDestroy();
    rmSync(parent, { recursive: true, force: true });
    delete process.env.AGENT_DATA_DIR; delete process.env.AGENT_DB_PATH;
    delete process.env.AGENT_ALLOWED_TARGET_ROOTS; delete process.env.AGENT_FAKE_PI;
  });

  it("generates the exact code-and-test boundary, exposes diff, edits and supersedes", async () => {
    const target = createGenerationTarget(parent);
    seedActiveWorkflow(database, "session-m3", null);
    const process = database.getProcessBySession("session-m3")!;
    const requirement = JSON.parse(process.requirement_snapshot_json);
    requirement.businessCode = "DEPOSIT_APPLY_001";
    database.db.prepare("UPDATE agent_process_definition SET requirement_snapshot_json = ? WHERE id = ?")
      .run(JSON.stringify(requirement), process.id);
    const started = generation.start("session-m3", user, 0, "start-m3", target);
    const first = await waitForReview(started.generationId);
    expect(first.manifest?.files.map((file) => file.relativePath).sort()).toEqual([...ENTRY_APPLICATION_FILES].sort());
    expect(first.manifest?.files).toHaveLength(11);
    expect(first.generationRevision).toBe(1);
    expect(database.getSession("session-m3")?.target_root?.toLowerCase()).toContain("flowmind-m3-");
    const service = generation.readFile("session-m3", started.generationId, ENTRY_APPLICATION_FILES[1], user);
    expect(service.content.match(/startAndSubmit\(/g)).toHaveLength(1);
    expect(service.content).not.toMatch(/approve\(|submitTask\(|RestTemplate|Repository/);
    expect(() => generation.readFile("session-m3", started.generationId, "../pom.xml", user)).toThrow(/generation boundary/);
    const routeDiff = generation.readDiff("session-m3", started.generationId, "frontend/src/router/generated-routes.ts", user);
    expect(routeDiff.changeType).toBe("MODIFY");
    expect(routeDiff.unifiedDiff).toContain("entry-application-apply");

    const edited = generation.editFile("session-m3", started.generationId, ENTRY_APPLICATION_FILES[8], `${generation.readFile("session-m3", started.generationId, ENTRY_APPLICATION_FILES[8], user).content}\n// reviewed\n`, 1, user);
    expect(edited.revision).toBe(2);
    expect(edited.files.find((file) => file.relativePath === ENTRY_APPLICATION_FILES[8])?.editedByUser).toBe(true);

    const session = database.getSession("session-m3")!;
    const regenerated = generation.regenerate("session-m3", started.generationId, user, session.row_version, 2, "regen-m3");
    const second = await waitForReview(regenerated.generationId);
    expect(second.generationId).not.toBe(first.generationId);
    expect(database.getGeneration(started.generationId)?.status).toBe("SUPERSEDED");
    expect(existsSync(database.getGeneration(regenerated.generationId)!.staging_dir)).toBe(true);
  });

  async function waitForReview(id: string) {
    const deadline = Date.now() + 3000;
    while (Date.now() < deadline) {
      const result = generation.get("session-m3", id, user);
      if (result.status === "REVIEW") return result;
      if (result.status === "FAILED") throw new Error(result.lastError?.message);
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    throw new Error("timed out waiting for M3 generation");
  }
});
