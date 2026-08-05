import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DatabaseService } from "../src/persistence/database.service.js";
import { EventBusService } from "../src/workflow/event-bus.service.js";
import { GenerationService } from "../src/generation/generation.service.js";
import { StagingService } from "../src/generation/staging.service.js";
import { TargetContractService } from "../src/generation/target-contract.service.js";
import { PiAdapterService } from "../src/pi/pi-adapter.service.js";
import { StaticValidatorService } from "../src/validation/static-validator.service.js";
import { VerificationWorkerService } from "../src/verification/verification-worker.service.js";
import { ReviewerService } from "../src/review/reviewer.service.js";
import { QualityPipelineService } from "../src/verification/quality-pipeline.service.js";
import { RepairCoordinatorService } from "../src/repair/repair-coordinator.service.js";
import { createGenerationTarget, seedActiveWorkflow } from "./generation-fixture.js";

describe("M4 quality pipeline integration", () => {
  let root: string;
  let database: DatabaseService;
  let generation: GenerationService;
  let quality: QualityPipelineService;
  let targets: TargetContractService;
  let staging: StagingService;
  let pi: PiAdapterService;
  let events: EventBusService;
  const user = { userId: "user_sales", userName: "Sales User" };

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), "flowmind-quality-pipeline-"));
    process.env.AGENT_DATA_DIR = join(root, "data");
    process.env.AGENT_DB_PATH = join(root, "agent.db");
    process.env.AGENT_ALLOWED_TARGET_ROOTS = root;
    process.env.AGENT_FAKE_PI = "true";
    database = new DatabaseService();
    targets = new TargetContractService();
    staging = new StagingService(database);
    pi = new PiAdapterService(database);
    events = new EventBusService();
    quality = new QualityPipelineService(
      database,
      staging,
      targets,
      new StaticValidatorService(),
      new VerificationWorkerService(),
      new ReviewerService(database, pi, staging),
      events,
    );
    generation = new GenerationService(
      database,
      targets,
      staging,
      pi,
      events,
      quality,
    );
  });

  afterEach(() => {
    database.onModuleDestroy();
    rmSync(root, { recursive: true, force: true });
    delete process.env.AGENT_DATA_DIR;
    delete process.env.AGENT_DB_PATH;
    delete process.env.AGENT_ALLOWED_TARGET_ROOTS;
    delete process.env.AGENT_FAKE_PI;
  });

  it("freezes revision one, verifies all stages, reviews, and opens the write gate", async () => {
    const target = createGenerationTarget(root);
    seedActiveWorkflow(database, "session-quality", target);
    const started = generation.start("session-quality", user, 0, "start-quality", target);
    await waitForGeneratedReview("session-quality", started.generationId);
    generation.startQuality("session-quality", started.generationId, user, database.getSession("session-quality")!.row_version, 1, false, "start-quality-gate");
    const deadline = Date.now() + 5000;
    while (Date.now() < deadline) {
      const summary = generation.get("session-quality", started.generationId, user);
      if (summary.status === "REVIEW" && summary.quality) {
        expect(summary.generationRevision).toBe(1);
        expect(summary.manifest?.files.every(({ validationStatus }) => validationStatus === "VALID")).toBe(true);
        expect(summary.quality.stages).toHaveLength(6);
        expect(summary.quality.review).toEqual(expect.objectContaining({ verdict: "APPROVE" }));
        expect(summary.quality).toEqual(expect.objectContaining({
          hardGatePassed: true,
          overrideRequired: false,
          canWrite: true,
        }));
        expect(database.getSession("session-quality")?.state).toBe("CODE_REVIEW");
        expect(database.db.prepare("SELECT pi_session_id FROM agent_code_review WHERE generation_id = ?")
          .get(started.generationId))
          .toEqual({ pi_session_id: expect.stringContaining("pi_fake_review_") });
        expect(database.db.prepare("SELECT COUNT(*) AS count FROM agent_verification_run WHERE generation_id = ?")
          .get(started.generationId)).toEqual({ count: 1 });
        return;
      }
      if (summary.status === "FAILED") throw new Error(summary.lastError?.message);
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    throw new Error("quality pipeline timed out");
  });

  it("records an explicit AI-review skip without creating a reviewer result", async () => {
    const target = createGenerationTarget(root);
    seedActiveWorkflow(database, "session-skip-review", target);
    const started = generation.start("session-skip-review", user, 0, "start-skip-review", target);
    await waitForGeneratedReview("session-skip-review", started.generationId);
    generation.startQuality("session-skip-review", started.generationId, user, database.getSession("session-skip-review")!.row_version, 1, true, "start-skip-review-gate");

    const deadline = Date.now() + 5000;
    while (Date.now() < deadline) {
      const summary = generation.get("session-skip-review", started.generationId, user);
      if (summary.status === "REVIEW" && summary.quality) {
        expect(summary.quality).toEqual(expect.objectContaining({ aiReviewSkipped: true, canWrite: true }));
        expect(summary.quality.review).toBeUndefined();
        expect(database.db.prepare("SELECT COUNT(*) AS count FROM agent_code_review WHERE generation_id = ?")
          .get(started.generationId)).toEqual({ count: 0 });
        return;
      }
      if (summary.status === "FAILED") throw new Error(summary.lastError?.message);
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    throw new Error("skipped-review quality pipeline timed out");
  });
  it("resumes the Generator Session and fully verifies a repaired revision", async () => {
    const realStatic = new StaticValidatorService();
    let validationCalls = 0;
    const staticValidator = {
      validate: vi.fn((input) => {
        validationCalls += 1;
        if (validationCalls === 1) {
          return {
            stage: "STATIC_VALIDATION" as const,
            status: "FAILED" as const,
            hardGate: true,
            summary: "repair required",
            diagnostics: [{
              code: "STATIC_REPAIR_REQUIRED",
              message: "Repair the generated boundary.",
              severity: "ERROR" as const,
              hardGate: true,
            }],
          };
        }
        return realStatic.validate(input);
      }),
    } as unknown as StaticValidatorService;
    const repair = new RepairCoordinatorService(database, targets, staging, pi, events);
    quality = new QualityPipelineService(
      database,
      staging,
      targets,
      staticValidator,
      new VerificationWorkerService(),
      new ReviewerService(database, pi, staging),
      events,
      repair,
    );
    generation = new GenerationService(database, targets, staging, pi, events, quality);
    const repairRun = vi.spyOn(pi, "runRepair");
    const target = createGenerationTarget(root);
    seedActiveWorkflow(database, "session-repair", target);
    const started = generation.start("session-repair", user, 0, "start-repair", target);
    await waitForGeneratedReview("session-repair", started.generationId);
    generation.startQuality("session-repair", started.generationId, user, database.getSession("session-repair")!.row_version, 1, false, "start-repair-gate");

    const deadline = Date.now() + 5000;
    while (Date.now() < deadline) {
      const summary = generation.get("session-repair", started.generationId, user);
      if (summary.status === "REVIEW" && summary.quality) {
        expect(summary.generationRevision).toBe(2);
        expect(summary.quality.repairRound).toBe(1);
        expect(repairRun).toHaveBeenCalledWith(
          started.generationId,
          expect.any(String),
          expect.stringContaining(started.generationId),
          expect.stringContaining("Repair round 1"),
          expect.any(Object),
        );
        expect(database.db.prepare("SELECT COUNT(*) AS count FROM agent_verification_run WHERE generation_id = ?")
          .get(started.generationId)).toEqual({ count: 2 });
        return;
      }
      if (summary.status === "FAILED") {
        const row = database.getGeneration(started.generationId)!;
        throw new Error(`repair failed: ${row.last_error_code} ${row.last_error_message} round=${row.repair_round} validations=${validationCalls}`);
      }
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    const row = database.getGeneration(started.generationId)!;
    throw new Error(`repaired quality pipeline timed out: status=${row.status} round=${row.repair_round} validations=${validationCalls}`);
  });

  async function waitForGeneratedReview(sessionId: string, generationId: string): Promise<void> {
    const deadline = Date.now() + 3000;
    while (Date.now() < deadline) {
      if (generation.get(sessionId, generationId, user).status === "REVIEW") return;
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    throw new Error("generation did not reach the quality-gate selection state");
  }
});
