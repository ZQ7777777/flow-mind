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
    const buildRepairPrompt = (repairModule as Record<string, unknown>).buildRepairPrompt as undefined | ((round: number, stages: QualityStageResult[], review: CodeReviewReport, references?: { platformRuntime: string; trustedUserContext: string }) => string);
    const prompt = buildRepairPrompt?.(1, stages, review, {
      platformRuntime: "com.flowmind.platform.api.service.ProcessRuntimeService setVariables setAttachments getCreatedTasks",
      trustedUserContext: "CurrentBusinessUserProvider.BusinessUser",
    });
    expect(prompt).toContain("REVIEW_BOUNDARY");
    expect(prompt).toContain("com.flowmind.platform.api.service.ProcessRuntimeService");
    expect(prompt).toContain("CurrentBusinessUserProvider.BusinessUser");
    expect(prompt).toContain("all failed hard and soft quality stages");
    expect(prompt).toContain("BACKEND_TESTS and FRONTEND_TESTS");
  });
});
