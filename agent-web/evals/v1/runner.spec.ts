import { describe, expect, it } from "vitest";
import { evaluationCatalogV1 } from "./catalog.js";
import { scoreEvaluationRun, validateEvaluationCatalog, type EvaluationRun } from "./runner.js";

describe("evaluation catalog v1", () => {
  it("contains the frozen 20/5/5 split and passes semantic validation", () => {
    const result = validateEvaluationCatalog(evaluationCatalogV1);

    expect(result.schemaErrors).toEqual([]);
    expect(result.semanticErrors).toEqual([]);
    expect(result.counts).toEqual({
      total: 30,
      development: 20,
      hiddenRegression: 5,
      hiddenAdversarial: 5,
      basic: 10,
      composite: 10,
      extension: 5,
      adversarial: 5,
    });
    expect(result.valid).toBe(true);
  });

  it("scores missing assertions as failures instead of silently excluding them", () => {
    const task = evaluationCatalogV1.tasks[0];
    const run: EvaluationRun = {
      runVersion: "1.0",
      catalogVersion: "1.0",
      strategyVersion: "legacy-baseline",
      model: "fake-model",
      promptHash: "prompt-hash",
      contextHash: "context-hash",
      startedAt: "2026-09-04T00:00:00.000Z",
      tasks: [{
        taskId: task.taskId,
        attempt: 1,
        generationSucceeded: true,
        technicalGatesPassed: true,
        businessAssertions: [{ assertionId: task.assertions[0].assertionId, status: "PASSED" }],
        repairRounds: 1,
        durationMs: 1200,
        inputTokens: 100,
        outputTokens: 50,
      }],
    };

    const metrics = scoreEvaluationRun(evaluationCatalogV1, run);

    expect(metrics.taskAttempts).toBe(1);
    expect(metrics.technicalGatePassRate).toBe(1);
    expect(metrics.criticalAssertionPassRate).toBeLessThan(1);
    expect(metrics.averageRepairRounds).toBe(1);
    expect(metrics.totalInputTokens).toBe(100);
  });
});
