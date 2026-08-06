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
              relativePath: "backend/EntryApplicationService.java",
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
    const repairRun = vi.spyOn(pi, "runRepair").mockImplementation(async (
      generationId,
      _stagingDir,
      sessionFile,
      prompt,
      callbacks,
    ) => {
      const brief = JSON.parse(prompt.split("Current Repair Brief:\n")[1]) as {
        actionableDiagnostics: Array<{ diagnosticId: string }>;
      };
      const changedPath = callbacks.listStaged().find((path) => path.endsWith("Service.java"))!;
      callbacks.writeStaged(changedPath, `${callbacks.readStaged(changedPath)}\n// repaired from current diagnostic\n`);
      callbacks.reportComplete(callbacks.listStaged(), brief.actionableDiagnostics.map(({ diagnosticId }) => ({
        diagnosticId,
        status: "RESOLVED",
        changedFiles: [changedPath],
        explanation: "Updated the generated service for the current static diagnostic.",
      })));
      return { piSessionId: `pi_fake_repair_${generationId}`, sessionFile };
    });
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

  it("rejects a repair that reports completion without changing staged files", async () => {
    const staticValidator = {
      validate: vi.fn(() => ({
        stage: "STATIC_VALIDATION" as const,
        status: "FAILED" as const,
        hardGate: true,
        summary: "repair required",
        diagnostics: [{
          code: "STATIC_REPAIR_REQUIRED",
          message: "Repair must change a staged file.",
          severity: "ERROR" as const,
          hardGate: true,
        }],
      })),
    } as unknown as StaticValidatorService;
    const repair = new RepairCoordinatorService(database, targets, staging, pi, events);
    quality = new QualityPipelineService(
      database, staging, targets, staticValidator, new VerificationWorkerService(),
      new ReviewerService(database, pi, staging), events, repair,
    );
    generation = new GenerationService(database, targets, staging, pi, events, quality);
    const target = createGenerationTarget(root);
    seedActiveWorkflow(database, "session-no-effect", target);
    const started = generation.start("session-no-effect", user, 0, "start-no-effect", target);
    await waitForGeneratedReview("session-no-effect", started.generationId);
    generation.startQuality("session-no-effect", started.generationId, user, database.getSession("session-no-effect")!.row_version, 1, false, "quality-no-effect");

    const summary = await waitForStatus("session-no-effect", started.generationId, "FAILED");
    expect(summary.lastError?.code).toBe("REPAIR_NO_EFFECT");
    expect(summary.quality).toEqual(expect.objectContaining({
      repairRound: 1,
      repairAttempts: [expect.objectContaining({
        outcome: "NO_EFFECT",
        failureCode: "REPAIR_NO_EFFECT",
        changedFiles: [],
      })],
    }));
    expect(database.db.prepare("SELECT COUNT(*) AS count FROM agent_verification_run WHERE generation_id = ?")
      .get(started.generationId)).toEqual({ count: 1 });
  });

  it("keeps a protocol-invalid repair visible when only a soft gate failed", async () => {
    const worker = {
      run: vi.fn(async () => ({
        runId: "worker-soft-failure",
        workspaceRoot: join(root, "worker"),
        logDir: join(root, "logs"),
        infrastructureFailed: false,
        stages: [
          ["BACKEND_COMPILE", true, "PASSED"],
          ["BACKEND_TESTS", false, "PASSED"],
          ["FRONTEND_TYPECHECK", true, "PASSED"],
          ["FRONTEND_TESTS", false, "FAILED"],
          ["FRONTEND_BUILD", true, "PASSED"],
        ].map(([stage, hardGate, status]) => ({
          stage,
          hardGate,
          status,
          summary: status === "FAILED" ? "Vitest failed." : "Passed.",
          diagnostics: status === "FAILED" ? [{
            code: "VITEST_TEST_FAILURE",
            message: "The generated form test failed.",
            severity: "ERROR" as const,
            hardGate: false,
            relativePath: "EntryApplicationApply.test.ts",
          }] : [],
        })),
      })),
    } as unknown as VerificationWorkerService;
    const repair = new RepairCoordinatorService(database, targets, staging, pi, events);
    quality = new QualityPipelineService(
      database, staging, targets, new StaticValidatorService(), worker,
      new ReviewerService(database, pi, staging), events, repair,
    );
    generation = new GenerationService(database, targets, staging, pi, events, quality);
    vi.spyOn(pi, "runRepair").mockImplementation(async (generationId, _stagingDir, sessionFile, _prompt, callbacks) => {
      const changedPath = callbacks.listStaged().find((path) => path.endsWith("Apply.spec.ts"))!;
      callbacks.writeStaged(changedPath, `${callbacks.readStaged(changedPath)}\n// valid change with invalid report\n`);
      callbacks.reportComplete(callbacks.listStaged(), []);
      return { piSessionId: `pi_protocol_invalid_${generationId}`, sessionFile };
    });
    const target = createGenerationTarget(root);
    seedActiveWorkflow(database, "session-protocol-invalid", target);
    const started = generation.start("session-protocol-invalid", user, 0, "start-protocol-invalid", target);
    await waitForGeneratedReview("session-protocol-invalid", started.generationId);
    generation.startQuality(
      "session-protocol-invalid",
      started.generationId,
      user,
      database.getSession("session-protocol-invalid")!.row_version,
      1,
      true,
      "quality-protocol-invalid",
    );

    const deadline = Date.now() + 7000;
    while (Date.now() < deadline) {
      const summary = generation.get("session-protocol-invalid", started.generationId, user);
      if (summary.status === "REVIEW" && summary.quality) {
        expect(summary.lastError?.code).toBe("REPAIR_PROTOCOL_INVALID");
        expect(summary.quality).toEqual(expect.objectContaining({
          hardGatePassed: true,
          canWrite: false,
          repairAttempts: [expect.objectContaining({
            outcome: "NO_EFFECT",
            failureCode: "REPAIR_PROTOCOL_INVALID",
            changedFiles: [expect.stringMatching(/EntryApplicationApply\.spec\.ts$/)],
          })],
        }));
        expect(staging.read(database.getGeneration(started.generationId)!, summary.quality.repairAttempts![0].changedFiles[0]).content)
          .not.toContain("valid change with invalid report");
        return;
      }
      if (summary.status === "FAILED") throw new Error(summary.lastError?.message);
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    throw new Error("protocol-invalid quality pipeline timed out");
  });

  it("feeds each repair round only the latest diagnostics and records all attempts", async () => {
    let validationCalls = 0;
    const staticValidator = {
      validate: vi.fn(() => {
        validationCalls += 1;
        const code = validationCalls === 1 ? "ROUND_A" : validationCalls === 2 ? "ROUND_B" : "ROUND_C";
        return {
          stage: "STATIC_VALIDATION" as const,
          status: "FAILED" as const,
          hardGate: true,
          summary: `${code} failed`,
          diagnostics: [{ code, message: `${code} must be repaired.`, severity: "ERROR" as const, hardGate: true }],
        };
      }),
    } as unknown as StaticValidatorService;
    const repair = new RepairCoordinatorService(database, targets, staging, pi, events);
    quality = new QualityPipelineService(
      database, staging, targets, staticValidator, new VerificationWorkerService(),
      new ReviewerService(database, pi, staging), events, repair,
    );
    generation = new GenerationService(database, targets, staging, pi, events, quality);
    const briefs: Array<{ verificationRunId: string; actionableDiagnostics: Array<{ diagnosticId: string; code: string }> }> = [];
    vi.spyOn(pi, "runRepair").mockImplementation(async (generationId, _stagingDir, sessionFile, prompt, callbacks) => {
      const brief = JSON.parse(prompt.split("Current Repair Brief:\n")[1]) as typeof briefs[number];
      const priorDiagnosticId = briefs.at(-1)?.actionableDiagnostics[0].diagnosticId;
      briefs.push(brief);
      expect(callbacks.readVerificationDiagnostic!(brief.actionableDiagnostics[0].diagnosticId).diagnostic)
        .toEqual(expect.objectContaining({ verificationRunId: brief.verificationRunId }));
      if (priorDiagnosticId) {
        expect(() => callbacks.readVerificationDiagnostic!(priorDiagnosticId))
          .toThrow("not part of the current verification run");
      }
      const changedPath = callbacks.listStaged().find((path) => path.endsWith("Service.java"))!;
      callbacks.writeStaged(changedPath, `${callbacks.readStaged(changedPath)}\n// repair round ${briefs.length}\n`);
      callbacks.reportComplete(callbacks.listStaged(), brief.actionableDiagnostics.map(({ diagnosticId }) => ({
        diagnosticId,
        status: "RESOLVED",
        changedFiles: [changedPath],
        explanation: `Changed the service for repair round ${briefs.length}.`,
      })));
      return { piSessionId: `pi_fake_repair_${generationId}`, sessionFile };
    });
    const target = createGenerationTarget(root);
    seedActiveWorkflow(database, "session-three-rounds", target);
    const started = generation.start("session-three-rounds", user, 0, "start-three-rounds", target);
    await waitForGeneratedReview("session-three-rounds", started.generationId);
    generation.startQuality("session-three-rounds", started.generationId, user, database.getSession("session-three-rounds")!.row_version, 1, false, "quality-three-rounds");

    const summary = await waitForStatus("session-three-rounds", started.generationId, "FAILED");
    expect(briefs.map(({ actionableDiagnostics }) => actionableDiagnostics.map(({ code }) => code)))
      .toEqual([["ROUND_A"], ["ROUND_B"], ["ROUND_C"]]);
    expect(new Set(briefs.map(({ verificationRunId }) => verificationRunId)).size).toBe(3);
    expect(summary.generationRevision).toBe(4);
    expect(summary.quality).toEqual(expect.objectContaining({
      repairRound: 3,
      repairAttempts: [
        expect.objectContaining({ round: 1, outcome: "CHANGED" }),
        expect.objectContaining({ round: 2, outcome: "CHANGED" }),
        expect.objectContaining({ round: 3, outcome: "CHANGED" }),
      ],
    }));
    expect(database.db.prepare("SELECT COUNT(*) AS count FROM agent_verification_run WHERE generation_id = ?")
      .get(started.generationId)).toEqual({ count: 4 });
  });

  async function waitForStatus(sessionId: string, generationId: string, status: "FAILED") {
    const deadline = Date.now() + 7000;
    while (Date.now() < deadline) {
      const summary = generation.get(sessionId, generationId, user);
      if (summary.status === status) return summary;
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    throw new Error(`generation did not reach ${status}`);
  }

  async function waitForGeneratedReview(sessionId: string, generationId: string): Promise<void> {
    const deadline = Date.now() + 3000;
    while (Date.now() < deadline) {
      if (generation.get(sessionId, generationId, user).status === "REVIEW") return;
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    throw new Error("generation did not reach the quality-gate selection state");
  }
});
