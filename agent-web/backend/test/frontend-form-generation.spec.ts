import { describe, expect, it } from "vitest";
import {
  ENTRY_APPLICATION_REQUIREMENT,
  type ArtifactManifest,
  type GenerationTargetContract,
} from "@flowmind/agent-contracts";
import { deriveGenerationSpec } from "../src/generation/generation-spec.js";
import { createFakeGenerationFiles } from "../src/pi/fake-generation-files.js";
import { StaticValidatorService } from "../src/validation/static-validator.service.js";

const contract: GenerationTargetContract = {
  contractVersion: "2.0",
  generationMode: "FRONTEND_FORM_ONLY",
  projectId: "fixture",
  frontend: {
    rootDir: "frontend",
    framework: "vue3",
    generatedModuleDir: "src/modules/generated",
    routeRegistry: "src/router/generated-routes.ts",
    sharedStartShell: "src/components/workflow/WorkflowStartShell.vue",
    sharedWorkflowTypes: "src/types/workflow.ts",
    verificationProfile: "vue3-npm",
  },
  readableReferenceFiles: ["frontend/src/router/generated-routes.ts"],
  allowedOutputPatterns: ["frontend/src/modules/generated/**/*", "frontend/src/router/generated-routes.ts"],
  protectedFiles: [{ path: "frontend/package.json", sha256: "a".repeat(64) }],
};

describe("frontend-only generated form", () => {
  it("creates exactly five files and preserves existing route entries", () => {
    const spec = deriveGenerationSpec(ENTRY_APPLICATION_REQUIREMENT, contract);
    const files = createFakeGenerationFiles(
      ENTRY_APPLICATION_REQUIREMENT,
      spec,
      contract,
      `import type { RouteRecordRaw } from "vue-router";\nexport const generatedRoutes: RouteRecordRaw[] = [\n  { path: "/existing", name: "existing-route", component: () => import("../Existing.vue") },\n];\nexport const generatedBusinessFormRegistry = {\n  existing: () => import("../ExistingForm.vue"),\n};\n`,
    );
    expect(Object.keys(files).sort()).toEqual([...spec.files].sort());
    expect(files[spec.paths.routeRegistry]).toContain("existing-route");
    expect(files[spec.paths.routeRegistry]).toContain("existing: () =>");
    expect(files[spec.paths.routeRegistry]).toContain(ENTRY_APPLICATION_REQUIREMENT.businessCode);
    expect(files[spec.paths.businessForm]).not.toMatch(/<button|fetch\(|start-submit/);
    expect(files[spec.paths.applyView]).toContain("WorkflowStartShell");

    const result = new StaticValidatorService().validate({
      generationId: "generation-1",
      revision: 0,
      requirement: ENTRY_APPLICATION_REQUIREMENT,
      contract,
      spec,
      manifest: manifest(spec.files),
      files: new Map(Object.entries(files)),
    });
    expect(result.status).toBe("PASSED");
  });

  it("blocks submit logic inside BusinessForm.vue", () => {
    const spec = deriveGenerationSpec(ENTRY_APPLICATION_REQUIREMENT, contract);
    const files = createFakeGenerationFiles(ENTRY_APPLICATION_REQUIREMENT, spec, contract,
      `import type { RouteRecordRaw } from "vue-router";\nexport const generatedRoutes: RouteRecordRaw[] = [];\n`);
    files[spec.paths.businessForm] += `\n<template><button type="submit">提交</button></template>`;
    const result = new StaticValidatorService().validate({
      generationId: "generation-2", revision: 0, requirement: ENTRY_APPLICATION_REQUIREMENT,
      contract, spec, manifest: manifest(spec.files), files: new Map(Object.entries(files)),
    });
    expect(result.diagnostics).toContainEqual(expect.objectContaining({ code: "GENERATED_FORM_BOUNDARY_VIOLATION" }));
  });

  it("keeps two different business forms independent while preserving both route registrations", () => {
    const entrySpec = deriveGenerationSpec(ENTRY_APPLICATION_REQUIREMENT, contract);
    const entryFiles = createFakeGenerationFiles(
      ENTRY_APPLICATION_REQUIREMENT,
      entrySpec,
      contract,
      `import type { RouteRecordRaw } from "vue-router";\nexport const generatedRoutes: RouteRecordRaw[] = [];\nexport const generatedBusinessFormRegistry = {};\n`,
    );
    const travel = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    travel.businessCode = "travel_expense";
    travel.businessName = "差旅报销";
    travel.entryDisplayName = "差旅报销";
    travel.entryPageTitle = "发起差旅报销";
    travel.formFields = [
      { fieldCode: "tripDays", fieldName: "出差天数", fieldType: "number", controlType: "number", required: true, validation: { minimum: 1 }, sortOrder: 1 },
      { fieldCode: "purpose", fieldName: "出差事由", fieldType: "string", controlType: "textarea", required: true, validation: {}, sortOrder: 2 },
      { fieldCode: "departureDate", fieldName: "出发日期", fieldType: "date", controlType: "datePicker", required: true, validation: {}, sortOrder: 3 },
    ];
    travel.nodeFieldPermissions = travel.nodes.flatMap(({ nodeCode, nodeType }) => travel.formFields.map(({ fieldCode, required }) => ({
      nodeCode,
      fieldCode,
      visible: nodeType !== "START" && nodeType !== "END",
      editable: nodeCode === "apply",
      required: nodeCode === "apply" && required,
    })));
    const travelSpec = deriveGenerationSpec(travel, contract);
    const travelFiles = createFakeGenerationFiles(travel, travelSpec, contract, entryFiles[entrySpec.paths.routeRegistry]);

    expect(entryFiles[entrySpec.paths.businessForm]).toContain("applicantName");
    expect(entryFiles[entrySpec.paths.businessForm]).not.toContain("tripDays");
    expect(travelFiles[travelSpec.paths.businessForm]).toContain("tripDays");
    expect(travelFiles[travelSpec.paths.businessForm]).not.toContain("applicantName");
    expect(travelFiles[travelSpec.paths.routeRegistry]).toContain("entry_application");
    expect(travelFiles[travelSpec.paths.routeRegistry]).toContain("travel_expense");
    expect(new StaticValidatorService().validate({
      generationId: "generation-travel", revision: 0, requirement: travel,
      contract, spec: travelSpec, manifest: manifest(travelSpec.files), files: new Map(Object.entries(travelFiles)),
    }).status).toBe("PASSED");
  });
});

function manifest(files: string[]): ArtifactManifest {
  return {
    generationId: "generation",
    targetRoot: "target",
    contractVersion: "2.0",
    revision: 0,
    files: files.map((relativePath) => ({
      relativePath, changeType: "ADD", stagedSha256: "a".repeat(64), sizeBytes: 1,
      validationStatus: "PENDING", editedByUser: false,
    })),
  };
}
