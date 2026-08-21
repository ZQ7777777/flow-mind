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
    expect(prompt).not.toContain("Keep the frontend-only boundary");
    expect(prompt).toContain("No generated business API is required");
    expect(prompt).not.toContain("ProcessRuntimeService");
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

  it("summarizes resolved, persisting, and new diagnostics for later repair rounds", () => {
    const stages: QualityStageResult[] = [{
      stage: "BACKEND_COMPILE",
      status: "FAILED",
      hardGate: true,
      summary: "compile failed",
      command: "mvn -q -DskipTests compile",
      exitCode: 1,
      diagnostics: [{
        diagnosticId: "diag-current",
        stage: "BACKEND_COMPILE",
        code: "JAVA_COMPILER_ERROR",
        message: "cannot find symbol",
        severity: "ERROR",
        hardGate: true,
        relativePath: "backend/src/main/java/com/flowmind/business/generated/EntryApplicationService.java",
        line: 87,
        column: 31,
        evidence: "symbol: method getApplicationNo()\nlocation: variable payload of type EntryApplicationRequest",
        repairability: "CODE_ACTIONABLE",
      }, {
        diagnosticId: "diag-new",
        stage: "BACKEND_COMPILE",
        code: "JAVA_COMPILER_ERROR",
        message: "incompatible types",
        severity: "ERROR",
        hardGate: true,
        relativePath: "backend/src/main/java/com/flowmind/business/generated/EntryApplicationService.java",
        line: 91,
        evidence: "required: String\nfound: Long",
        repairability: "CODE_ACTIONABLE",
      }],
    }];
    const buildRepairPrompt = (repairModule as Record<string, unknown>).buildRepairPrompt as any;
    const prompt = buildRepairPrompt(2, stages, undefined, {
      platformRuntime: "ProcessRuntimeService#startAndSubmit(StartProcessRequest)",
      trustedUserContext: "CurrentBusinessUserProvider.BusinessUser",
    }, {
      generationId: "generation-1",
      generationRevision: 3,
      verificationRunId: "verification-2",
    }, {
      changedFiles: ["backend/src/main/java/com/flowmind/business/generated/EntryApplicationService.java"],
      resolvedDiagnosticIds: ["diag-old"],
      unresolvedDiagnosticIds: ["diag-current"],
    });

    const brief = JSON.parse(prompt.split("Current Repair Brief:\n")[1]);
    expect(brief.failedStages).toContainEqual(expect.objectContaining({
      stage: "BACKEND_COMPILE",
      command: "mvn -q -DskipTests compile",
      exitCode: 1,
    }));
    expect(brief.diagnosticDelta).toEqual(expect.objectContaining({
      resolvedDiagnosticIds: ["diag-old"],
      persistingDiagnosticIds: ["diag-current"],
      newDiagnosticIds: ["diag-new"],
    }));
    expect(brief.ineffectiveRepairSignals).toEqual(expect.objectContaining({
      requiresRootCauseRecheck: true,
    }));
    expect(prompt).toContain("re-check the actual API, DTO, imports, dependencies, and language constraints before editing");
  });

  it("keeps external scoped diagnostics out of repair actionability", () => {
    const stages: QualityStageResult[] = [{
      stage: "FRONTEND_TYPECHECK",
      status: "FAILED",
      hardGate: true,
      summary: "failed",
      diagnostics: [{
        diagnosticId: "diag-current",
        stage: "FRONTEND_TYPECHECK",
        code: "TS2345",
        message: "Generated payload is invalid.",
        severity: "ERROR",
        hardGate: true,
        relativePath: "frontend/src/modules/generated/example.ts",
        scope: "CURRENT_GENERATION",
        repairability: "CODE_ACTIONABLE",
      } as any, {
        diagnosticId: "diag-pre-existing",
        stage: "FRONTEND_TYPECHECK",
        code: "TS2322",
        message: "Legacy view has an old unrelated type error.",
        severity: "ERROR",
        hardGate: true,
        relativePath: "frontend/src/views/LegacyView.vue",
        scope: "PRE_EXISTING",
        repairability: "CODE_ACTIONABLE",
      } as any, {
        diagnosticId: "diag-integration-impact",
        stage: "FRONTEND_TYPECHECK",
        code: "TS2741",
        message: "Legacy view directly depends on the generated payload shape.",
        severity: "ERROR",
        hardGate: true,
        relativePath: "frontend/src/views/GeneratedCaller.vue",
        scope: "INTEGRATION_IMPACT",
        repairability: "CODE_ACTIONABLE",
      } as any],
    }];
    const buildRepairPrompt = (repairModule as Record<string, unknown>).buildRepairPrompt as any;
    const prompt = buildRepairPrompt(1, stages);
    const brief = JSON.parse(prompt.split("Current Repair Brief:\n")[1]);

    expect(brief.actionableDiagnostics.map(({ diagnosticId }: { diagnosticId: string }) => diagnosticId))
      .toEqual(["diag-current"]);
    expect(brief.blockedDiagnostics.map(({ diagnosticId }: { diagnosticId: string }) => diagnosticId))
      .toEqual(["diag-pre-existing", "diag-integration-impact"]);
  });

  it("keeps the repair prompt bounded and carries a compact source-of-truth context", () => {
    const buildRepairPrompt = (repairModule as Record<string, unknown>).buildRepairPrompt as any;
    const prompt = buildRepairPrompt(
      1,
      [{
        stage: "FRONTEND_TYPECHECK",
        status: "FAILED",
        hardGate: true,
        summary: "failed",
        diagnostics: [{
          diagnosticId: "diag-current",
          stage: "FRONTEND_TYPECHECK",
          code: "TS2345",
          message: "Argument is invalid.",
          severity: "ERROR",
          hardGate: true,
          relativePath: "frontend/src/modules/generated/example.ts",
          repairability: "CODE_ACTIONABLE",
        }],
      }],
      undefined,
      { businessReferencePath: "frontend/src/api/generated/reference.ts" },
      {
        generationId: "generation-1",
        generationRevision: 2,
        verificationRunId: "verification-1",
      },
      undefined,
      [{ relativePath: "frontend/src/modules/generated/example.ts", content: "x".repeat(9_000) }],
      {
        requirement: {
          businessCode: "entry_application",
          businessName: "Entry application",
          formFields: [{ fieldCode: "amount", fieldType: "number" }],
          nodes: [{ nodeCode: "apply", nodeType: "USER_TASK" }],
        },
        contract: { contractVersion: "2.1", projectId: "fixture", frontend: { rootDir: "frontend", framework: "vue3" } },
        manifestPaths: ["frontend/src/modules/generated/example.ts"],
      },
    );
    const brief = JSON.parse(prompt.split("Current Repair Brief:\n")[1]);

    expect(prompt).toContain("entry_application");
    expect(prompt).toContain("read_generation_contract_file");
    expect(brief.relatedSourceFiles[0].content.length).toBeLessThanOrEqual(2_000);
    expect(prompt.length).toBeLessThan(20_000);
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
