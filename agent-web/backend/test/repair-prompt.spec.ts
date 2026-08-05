import { describe, expect, it } from "vitest";
import type { CodeReviewReport, QualityStageResult } from "@flowmind/agent-contracts";
import * as repairModule from "../src/repair/repair-coordinator.service.js";

describe("repair prompt", () => {
  it("includes independent reviewer findings in the resumed Generator context", () => {
    const stages: QualityStageResult[] = [{
      stage: "FRONTEND_BUILD",
      status: "PASSED",
      hardGate: true,
      summary: "passed",
      diagnostics: [],
    }];
    const review: CodeReviewReport = {
      reviewId: "review-1",
      status: "FAILED",
      verdict: "CHANGES_REQUESTED",
      summary: "Boundary issue found.",
      issues: [{
        code: "REVIEW_BOUNDARY",
        title: "Unexpected action",
        message: "Remove the unrelated platform action.",
        severity: "BLOCKING",
        relativePath: "frontend/src/modules/generated/example.ts",
      }],
      createdAt: "2026-08-05T00:00:00.000Z",
      completedAt: "2026-08-05T00:00:01.000Z",
    };
    const buildRepairPrompt = (repairModule as Record<string, unknown>).buildRepairPrompt as undefined | ((round: number, stages: QualityStageResult[], review: CodeReviewReport) => string);
    expect(buildRepairPrompt?.(1, stages, review)).toContain("REVIEW_BOUNDARY");
  });
});
