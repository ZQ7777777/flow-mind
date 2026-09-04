import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { GenerationQualityReport, MockUser, QualityStageResult } from "@flowmind/agent-contracts";
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
import { createGenerationTarget, seedActiveWorkflow } from "./generation-fixture.js";

describe("M5 quality action idempotency", () => {
  let root: string;
  let database: DatabaseService;
  let generation: GenerationService;
  let quality: QualityPipelineService;
  let generationId: string;
  const user: MockUser = { userId: "user_sales", userName: "Sales User" };

  beforeEach(async () => {
    root = mkdtempSync(join(tmpdir(), "flowmind-quality-actions-"));
    process.env.AGENT_DATA_DIR = join(root, "data");
    process.env.AGENT_DB_PATH = join(root, "agent.db");
    process.env.AGENT_ALLOWED_TARGET_ROOTS = root;
    process.env.AGENT_FAKE_PI = "true";
    database = new DatabaseService();
    const targets = new TargetContractService();
    const staging = new StagingService(database);
    const pi = new PiAdapterService(database);
    const events = new EventBusService();
    const baseline = new GenerationService(database, targets, staging, pi, events);
    const target = createGenerationTarget(root);
    seedActiveWorkflow(database, "session-actions", target);
    generationId = baseline.start("session-actions", user, 0, "start-actions", target).generationId;
    await waitForReview();

    quality = new QualityPipelineService(
      database,
      staging,
      targets,
      new StaticValidatorService(),
      new VerificationWorkerService(),
      new ReviewerService(database, pi, staging),
      events,
    );
    vi.spyOn(quality, "start").mockImplementation(() => undefined);
    generation = new GenerationService(database, targets, staging, pi, events, quality);
    seedSoftFailure();
  });

  afterEach(() => {
    database.onModuleDestroy();
    rmSync(root, { recursive: true, force: true });
    delete process.env.AGENT_DATA_DIR;
    delete process.env.AGENT_DB_PATH;
    delete process.env.AGENT_ALLOWED_TARGET_ROOTS;
    delete process.env.AGENT_FAKE_PI;
  });

  it("replays reverify with the original row version and key", () => {
    const rowVersion = database.getSession("session-actions")!.row_version;
    const reverify = generation.reverify as unknown as (
      sessionId: string,
      generationId: string,
      user: MockUser,
      rowVersion: number,
      revision: number,
      idempotencyKey: string,
    ) => unknown;
    const first = reverify.call(generation, "session-actions", generationId, user, rowVersion, 1, "reverify-key");
    const replay = reverify.call(generation, "session-actions", generationId, user, rowVersion, 1, "reverify-key");
    expect(replay).toEqual(first);
    expect(database.db.prepare("SELECT COUNT(*) AS count FROM agent_generation_action WHERE generation_id = ? AND action = 'REVERIFY'").get(generationId))
      .toEqual({ count: 1 });
  });

  it("starts a manual reverify with a fresh repair cycle and clears repair history", () => {
    const rowVersion = database.getSession("session-actions")!.row_version;
    database.db.prepare(`
      INSERT INTO agent_verification_run (
        id, generation_id, revision, repair_round, trigger, status,
        stage_results_json, started_at, completed_at, created_at
      ) VALUES ('verification-before-reverify', ?, 1, 3, 'REPAIR', 'FAILED', '[]', ?, ?, ?)
    `).run(generationId, new Date().toISOString(), new Date().toISOString(), new Date().toISOString());
    database.db.prepare(`
      INSERT INTO agent_repair_attempt (
        id, generation_id, verification_run_id, round, diagnostic_ids_json,
        changed_files_json, resolutions_json, outcome, failure_code, created_at
      ) VALUES ('repair-manual-reverify', ?, 'verification-before-reverify', 3, '["diagnostic-before"]',
        '["backend/src/main/java/com/flowmind/business/generated/entryapplication/EntryApplicationService.java"]',
        '[]', 'CHANGED', NULL, ?)
    `).run(generationId, new Date().toISOString());
    database.db.prepare(`
      UPDATE agent_code_generation SET repair_round = 3, max_repair_rounds = 3 WHERE id = ?
    `).run(generationId);

    generation.reverify("session-actions", generationId, user, rowVersion, 1, "fresh-reverify-cycle");

    const reverified = database.getGeneration(generationId)!;
    expect(reverified.status).toBe("VERIFYING");
    expect(reverified.repair_round).toBe(0);
    expect(reverified.max_repair_rounds).toBe(3);
    expect(database.db.prepare("SELECT COUNT(*) AS count FROM agent_repair_attempt WHERE generation_id = ?").get(generationId))
      .toEqual({ count: 0 });
  });

  it("replays a quality override and advances the session row version once", () => {
    const rowVersion = database.getSession("session-actions")!.row_version;
    const override = generation.overrideQuality as unknown as (
      sessionId: string,
      generationId: string,
      user: MockUser,
      rowVersion: number,
      revision: number,
      scopes: Array<"BACKEND_TESTS">,
      reason: string,
      idempotencyKey: string,
    ) => GenerationQualityReport;
    const first = override.call(generation, "session-actions", generationId, user, rowVersion, 1, ["BACKEND_TESTS"], "JUnit failure was reviewed and accepted.", "override-key");
    const replay = override.call(generation, "session-actions", generationId, user, rowVersion, 1, ["BACKEND_TESTS"], "JUnit failure was reviewed and accepted.", "override-key");
    expect(replay).toEqual(first);
    expect(database.getSession("session-actions")!.row_version).toBe(rowVersion + 1);
    expect(database.db.prepare("SELECT COUNT(*) AS count FROM agent_generation_action WHERE generation_id = ? AND action = 'OVERRIDE_QUALITY'").get(generationId))
      .toEqual({ count: 1 });
  });

  it("persists who overrode a BLOCKING reviewer finding and why", () => {
    const report = JSON.parse(database.getGeneration(generationId)!.quality_report_json!) as GenerationQualityReport;
    report.stages = report.stages.map((stage) => stage.stage === "BACKEND_TESTS" ? { ...stage, status: "PASSED" } : stage);
    report.review = {
      reviewId: "review-blocking",
      status: "FAILED",
      verdict: "CHANGES_REQUESTED",
      summary: "存在生成边界问题",
      issues: [{ code: "REVIEW_BOUNDARY", title: "越界行为", message: "包含未声明操作", severity: "BLOCKING" }],
      createdAt: new Date().toISOString(),
      completedAt: new Date().toISOString(),
    };
    database.db.prepare("UPDATE agent_code_generation SET quality_report_json = ? WHERE id = ?")
      .run(JSON.stringify(report), generationId);
    const rowVersion = database.getSession("session-actions")!.row_version;

    const result = generation.overrideQuality(
      "session-actions", generationId, user, rowVersion, 1,
      ["REVIEWER"], "业务负责人已复核该阻断项并承担发布责任。", "reviewer-override-key",
    );

    expect(result.canWrite).toBe(true);
    expect(database.db.prepare("SELECT revision, scopes_json, reason, created_by FROM agent_quality_override WHERE generation_id = ? ORDER BY created_at DESC LIMIT 1").get(generationId))
      .toEqual({ revision: 1, scopes_json: '["REVIEWER"]', reason: "业务负责人已复核该阻断项并承担发布责任。", created_by: user.userId });
  });

  it("stops a running quality gate, resets repair rounds, and allows restart", () => {
    const rowVersion = database.getSession("session-actions")!.row_version;
    const runningReport = JSON.parse(database.getGeneration(generationId)!.quality_report_json!) as GenerationQualityReport;
    database.db.prepare(`
      INSERT INTO agent_verification_run (
        id, generation_id, revision, repair_round, trigger, status,
        stage_results_json, started_at, created_at
      ) VALUES ('verification-stop', ?, 1, 3, 'REPAIR', 'RUNNING', '[]', ?, ?)
    `).run(generationId, new Date().toISOString(), new Date().toISOString());
    database.db.prepare(`
      UPDATE agent_code_generation SET status = 'REPAIRING', repair_round = 3,
        max_repair_rounds = 3, can_write = 1, quality_report_json = ? WHERE id = ?
    `).run(JSON.stringify({ ...runningReport, repairRound: 3 }), generationId);
    database.db.prepare("UPDATE agent_session SET state = 'CODE_REPAIRING' WHERE id = 'session-actions'").run();

    const stopped = generation.stopQuality("session-actions", generationId, user, rowVersion);

    expect(stopped).toEqual({ cancelled: true, state: "CODE_REVIEW" });
    const stoppedGeneration = database.getGeneration(generationId)!;
    expect(stoppedGeneration.status).toBe("REVIEW");
    expect(stoppedGeneration.repair_round).toBe(0);
    expect(stoppedGeneration.max_repair_rounds).toBe(3);
    expect(stoppedGeneration.can_write).toBe(0);
    expect(JSON.parse(stoppedGeneration.quality_report_json!)).toEqual(expect.objectContaining({
      pipelineState: "CANCELLED",
      repairRound: 0,
      maxRepairRounds: 3,
      canWrite: false,
    }));
    expect(database.db.prepare("SELECT status FROM agent_verification_run WHERE id = 'verification-stop'").get())
      .toEqual({ status: "CANCELLED" });
    const afterStop = database.getSession("session-actions")!;
    expect(afterStop.state).toBe("CODE_REVIEW");
    expect(afterStop.row_version).toBe(rowVersion + 1);

    generation.startQuality("session-actions", generationId, user, afterStop.row_version, 1, false, "restart-quality");

    const restarted = database.getGeneration(generationId)!;
    expect(restarted.status).toBe("VERIFYING");
    expect(restarted.repair_round).toBe(0);
    expect(restarted.quality_report_json).toBeNull();
    expect(database.getSession("session-actions")!.state).toBe("CODE_VERIFYING");
  });

  async function waitForReview(): Promise<void> {
    const deadline = Date.now() + 3000;
    while (Date.now() < deadline) {
      if (database.getGeneration(generationId)?.status === "REVIEW") return;
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    throw new Error("generation did not reach review");
  }

  function seedSoftFailure(): void {
    const stages: QualityStageResult[] = [
      { stage: "STATIC_VALIDATION", status: "PASSED", hardGate: true, summary: "passed", diagnostics: [] },
      { stage: "BACKEND_COMPILE", status: "PASSED", hardGate: true, summary: "passed", diagnostics: [] },
      { stage: "BACKEND_TESTS", status: "FAILED", hardGate: false, summary: "failed", diagnostics: [] },
      { stage: "FRONTEND_TYPECHECK", status: "PASSED", hardGate: true, summary: "passed", diagnostics: [] },
      { stage: "FRONTEND_TESTS", status: "PASSED", hardGate: false, summary: "passed", diagnostics: [] },
      { stage: "FRONTEND_BUILD", status: "PASSED", hardGate: true, summary: "passed", diagnostics: [] },
    ];
    const report: GenerationQualityReport = {
      generationId,
      revision: 1,
      pipelineState: "PASSED",
      repairRound: 3,
      maxRepairRounds: 3,
      stages,
      review: {
        reviewId: "review-actions",
        status: "PASSED",
        verdict: "APPROVE",
        summary: "approved",
        issues: [],
        createdAt: new Date().toISOString(),
        completedAt: new Date().toISOString(),
      },
      hardGatePassed: true,
      overrideRequired: true,
      canWrite: false,
      updatedAt: new Date().toISOString(),
    };
    database.db.prepare(`
      UPDATE agent_code_generation SET status = 'REVIEW', quality_revision = 1,
        quality_report_json = ?, hard_gate_passed = 1, override_required = 1,
        can_write = 0 WHERE id = ?
    `).run(JSON.stringify(report), generationId);
  }
});
