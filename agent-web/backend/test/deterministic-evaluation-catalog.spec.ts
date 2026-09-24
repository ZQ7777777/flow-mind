import { describe, expect, it } from "vitest";
import type { ArtifactManifest, GenerationTargetContract } from "@flowmind/agent-contracts";
import { evaluationCatalogV1 } from "../../evals/v1/catalog.js";
import { createDeterministicGenerationFiles, materializeRequirementFromIr } from "../src/generation/deterministic-generation-files.js";
import { deriveGenerationSpec } from "../src/generation/generation-spec.js";
import { validateRequirementIr } from "../src/requirement/requirement-ir.js";
import { StaticValidatorService } from "../src/validation/static-validator.service.js";

const contract = {
  contractVersion: "2.1",
  generationMode: "FRONTEND_ONLY",
  projectId: "m2-evaluation",
  frontend: {
    rootDir: "frontend", framework: "vue3", generatedModuleDir: "src/modules/generated",
    generatedApiDir: "src/api/generated", routeRegistry: "src/router/generated-routes.ts",
    sharedStartShell: "src/components/workflow/WorkflowStartShell.vue", sharedWorkflowTypes: "src/types/workflow.ts",
    exampleReferenceFiles: [], verificationProfile: "vue3-npm",
  },
  readableReferenceFiles: ["frontend/package.json"],
  allowedOutputPatterns: ["frontend/src/modules/generated/**/*", "frontend/src/api/generated/**/*", "frontend/src/router/generated-routes.ts"],
  protectedFiles: [{ path: "frontend/package.json", sha256: "a".repeat(64) }],
} as GenerationTargetContract;

describe("M2 frozen evaluation catalog", () => {
  it("generates every ready task and blocks every adversarial task without a model", () => {
    const validator = new StaticValidatorService();
    const outcomes = evaluationCatalogV1.tasks.map((task) => {
      const irValidation = validateRequirementIr(task.requirementIr);
      if (!irValidation.generationReady) return { taskId: task.taskId, outcome: "BLOCKED_REQUIREMENT" };

      const requirement = materializeRequirementFromIr(task.requirementIr);
      let spec;
      try {
        spec = deriveGenerationSpec(requirement, contract);
      } catch (error) {
        const issues = error && typeof error === "object" && "issues" in error ? (error as { issues: string[] }).issues.join("; ") : String(error);
        throw new Error(`${task.taskId}: ${issues}`);
      }
      const generated = createDeterministicGenerationFiles(
        task.requirementIr,
        spec,
        contract,
        'import type { RouteRecordRaw } from "vue-router";\nexport const generatedRoutes: RouteRecordRaw[] = [];\n',
      );
      const files = new Map(Object.entries(generated));
      const manifest: ArtifactManifest = {
        generationId: `m2-${task.taskId}`,
        targetRoot: "target",
        contractVersion: contract.contractVersion,
        revision: 1,
        files: spec.files.map((relativePath) => ({
          relativePath,
          changeType: relativePath === spec.paths.routeRegistry ? "MODIFY" : "ADD",
          stagedSha256: "b".repeat(64),
          sizeBytes: Buffer.byteLength(files.get(relativePath) || "", "utf8"),
          validationStatus: "PENDING",
          editedByUser: false,
        })),
      };
      const result = validator.validate({ generationId: manifest.generationId, revision: 1, requirement, contract, spec, manifest, files });
      expect(result.diagnostics, task.taskId).toEqual([]);
      return { taskId: task.taskId, outcome: "GENERATION_READY" };
    });

    expect(outcomes).toHaveLength(30);
    for (const [index, outcome] of outcomes.entries()) {
      expect(outcome.outcome, outcome.taskId).toBe(evaluationCatalogV1.tasks[index].expectedOutcome);
    }
  });
});
