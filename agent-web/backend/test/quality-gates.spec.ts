import { describe, expect, it } from "vitest";
import type { CodeReviewReport, QualityStageResult } from "@flowmind/agent-contracts";
import { evaluateQualityGates, nextRepairDecision } from "../src/verification/quality-gates.js";

describe("quality gates and repair budget", () => {
  it("allows writing only when all hard and soft gates pass", () => {
    expect(evaluateQualityGates(stages(), review("APPROVE"), [])).toEqual({
      hardGatePassed: true,
      softFailures: [],
      overrideRequired: false,
      canWrite: true,
    });
  });

  it("requires current-revision overrides for JUnit or Vitest failures", () => {
    const failed = stages().map((stage) => stage.stage === "BACKEND_TESTS"
      ? { ...stage, status: "FAILED" as const }
      : stage);
    expect(evaluateQualityGates(failed, review("CHANGES_REQUESTED"), [])).toEqual({
      hardGatePassed: true,
      softFailures: ["BACKEND_TESTS"],
      overrideRequired: true,
      canWrite: false,
    });
    expect(evaluateQualityGates(failed, review("CHANGES_REQUESTED"), ["BACKEND_TESTS"]).canWrite)
      .toBe(true);
  });

  it("does not block writing for non-blocking reviewer findings when automatic gates pass", () => {
    expect(evaluateQualityGates(stages(), review("CHANGES_REQUESTED"), [])).toEqual({
      hardGatePassed: true,
      softFailures: [],
      overrideRequired: false,
      canWrite: true,
    });
  });

  it("blocks writing for BLOCKING reviewer findings until an audited override", () => {
    const blocked = review("CHANGES_REQUESTED");
    blocked.issues.push({
      code: "REVIEW_BOUNDARY",
      title: "越界行为",
      message: "生成代码包含未声明的变更操作。",
      severity: "BLOCKING",
    });
    expect(evaluateQualityGates(stages(), blocked, [])).toEqual({
      hardGatePassed: true,
      softFailures: ["REVIEWER"],
      overrideRequired: true,
      canWrite: false,
    });
    expect(evaluateQualityGates(stages(), blocked, ["REVIEWER"]).canWrite).toBe(true);
  });

  it("never permits an override for a hard failure", () => {
    const failed = stages().map((stage) => stage.stage === "FRONTEND_BUILD"
      ? { ...stage, status: "FAILED" as const }
      : stage);
    expect(evaluateQualityGates(failed, review("APPROVE"), ["BACKEND_TESTS", "FRONTEND_TESTS", "REVIEWER"]))
      .toEqual(expect.objectContaining({ hardGatePassed: false, canWrite: false, overrideRequired: false }));
  });

  it("caps repair at three and does not spend budget on infrastructure failures", () => {
    expect(nextRepairDecision(0, false, true)).toEqual({ repair: false, nextRound: 0 });
    expect(nextRepairDecision(2, true, false)).toEqual({ repair: true, nextRound: 3 });
    expect(nextRepairDecision(3, true, false)).toEqual({ repair: false, nextRound: 3 });
  });

  it("keeps the automatic repair budget capped at three rounds", () => {
    expect(nextRepairDecision(3, true, false, true)).toEqual({ repair: false, nextRound: 3 });
    expect(nextRepairDecision(4, true, false, true)).toEqual({ repair: false, nextRound: 4 });
    expect(nextRepairDecision(3, true, false, false)).toEqual({ repair: false, nextRound: 3 });
  });
});

function stages(): QualityStageResult[] {
  return [
    stage("STATIC_VALIDATION", true),
    stage("BACKEND_COMPILE", true),
    stage("BACKEND_TESTS", false),
    stage("FRONTEND_TYPECHECK", true),
    stage("FRONTEND_TESTS", false),
    stage("FRONTEND_BUILD", true),
  ];
}

function stage(name: QualityStageResult["stage"], hardGate: boolean): QualityStageResult {
  return { stage: name, status: "PASSED", hardGate, summary: "ok", diagnostics: [] };
}

function review(verdict: CodeReviewReport["verdict"]): CodeReviewReport {
  return {
    reviewId: "review-1",
    status: verdict === "APPROVE" ? "PASSED" : "FAILED",
    verdict,
    summary: "reviewed",
    issues: [],
    createdAt: new Date().toISOString(),
  };
}
