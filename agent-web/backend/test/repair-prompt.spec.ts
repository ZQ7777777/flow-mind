import { describe, expect, it } from "vitest";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
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
    }, {
      stage: "FRONTEND_TYPECHECK",
      status: "FAILED",
      hardGate: true,
      summary: "failed",
      diagnostics: [{
        diagnosticId: "diagnostic-current",
        stage: "FRONTEND_TYPECHECK",
        code: "TS2345",
        message: "Argument is invalid.",
        severity: "ERROR",
        hardGate: true,
        relativePath: "frontend/src/modules/generated/example.ts",
        actual: "The payload exposes a string argument.",
        expected: "The payload must expose a numeric argument.",
        evidence: "example.ts(7,9): error TS2345",
        repairHint: "Correct the argument type.",
        acceptedForms: ["Pass a number directly", "Convert the validated string to a number"],
        repairability: "CODE_ACTIONABLE",
      }],
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
    const buildRepairPrompt = (repairModule as Record<string, unknown>).buildRepairPrompt as undefined | ((round: number, stages: QualityStageResult[], review: CodeReviewReport, references?: { platformRuntime: string; trustedUserContext: string }, context?: { generationId: string; generationRevision: number; verificationRunId: string }, previousAttempt?: { changedFiles: string[]; resolvedDiagnosticIds: string[]; unresolvedDiagnosticIds: string[] }) => string);
    const prompt = buildRepairPrompt?.(1, stages, review, {
      platformRuntime: "com.flowmind.platform.api.service.ProcessRuntimeService setVariables setAttachments getCreatedTasks",
      trustedUserContext: "CurrentBusinessUserProvider.BusinessUser",
    }, {
      generationId: "generation-1",
      generationRevision: 2,
      verificationRunId: "verification-current",
    }, {
      changedFiles: ["frontend/src/modules/generated/example.ts"],
      resolvedDiagnosticIds: [],
      unresolvedDiagnosticIds: ["diagnostic-current"],
    });
    expect(prompt).toContain("REVIEW_BOUNDARY");
    expect(prompt).toContain("com.flowmind.platform.api.service.ProcessRuntimeService");
    expect(prompt).toContain("CurrentBusinessUserProvider.BusinessUser");
    expect(prompt).toContain("all failed hard and soft quality stages");
    expect(prompt).toContain("BACKEND_TESTS and FRONTEND_TESTS");
    expect(prompt).toContain("diagnostic-current");
    expect(prompt).toContain("verification-current");
    expect(prompt).toContain("The payload exposes a string argument.");
    expect(prompt).toContain("The payload must expose a numeric argument.");
    expect(prompt).toContain("Pass a number directly");
    expect(prompt).toContain('"repeatedDiagnostics":[{');
    expect(prompt).toContain('"changedFiles":["frontend/src/modules/generated/example.ts"]');
    expect(prompt).toContain("explicitly remaining subchecks");
    expect(prompt).not.toContain('"summary":"passed"');
  });

  it("reads only bounded logs inside the current generation", () => {
    const root = mkdtempSync(join(tmpdir(), "flowmind-repair-log-"));
    try {
      const logDir = join(root, "verification-logs", "generation-1", "run-1");
      mkdirSync(logDir, { recursive: true });
      const logPath = join(logDir, "backend_tests.log");
      writeFileSync(logPath, "[stdout]\nfailed\n[stderr]\nAuthorization: Bearer secret-value\nexpected 42", "utf8");
      expect(repairModule.readVerificationLogExcerpt(root, "generation-1", logPath)).toEqual({
        stdoutExcerpt: "failed\n",
        stderrExcerpt: "Authorization: Bearer [REDACTED]\nexpected 42",
      });
      const outside = join(root, "verification-logs", "other-generation", "run-1", "test.log");
      mkdirSync(join(root, "verification-logs", "other-generation", "run-1"), { recursive: true });
      writeFileSync(outside, "secret", "utf8");
      expect(() => repairModule.readVerificationLogExcerpt(root, "generation-1", outside))
        .toThrow("outside the current generation");
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});
