import { describe, expect, it } from "vitest";
import {
  WAREHOUSE_PLEDGE_REQUIREMENT,
  type ArtifactManifest,
  type GenerationTargetContract,
} from "@flowmind/agent-contracts";
import { deriveGenerationSpec } from "../src/generation/generation-spec.js";
import { createFakeGenerationFiles } from "../src/pi/fake-generation-files.js";
import { StaticValidatorService } from "../src/validation/static-validator.service.js";

const contract = {
  contractVersion: "2.1",
  generationMode: "FRONTEND_ONLY",
  projectId: "fixture",
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

describe("frontend-only static generated-code validation", () => {
  const validator = new StaticValidatorService();

  it("passes the exact seven-file dynamic frontend artifact set", () => {
    const input = validInput();
    expect(input.spec.files).toHaveLength(7);
    expect(validator.validate(input)).toEqual(expect.objectContaining({ status: "PASSED", diagnostics: [] }));
  });

  it("fails when manifest, spec, and staging file sets differ", () => {
    const input = validInput();
    input.files.delete(input.spec.paths.api);
    expect(validator.validate(input).diagnostics).toContainEqual(expect.objectContaining({ code: "GENERATED_FILE_SET_MISMATCH" }));
  });

  it("blocks omitted confirmed fields and displayed checks", () => {
    const input = validInput();
    input.files.set(input.spec.paths.businessForm, input.files.get(input.spec.paths.businessForm)!
      .replaceAll("futuresAccount", "removedAccount")
      .replaceAll("满足质押要求", "未实现核查"));
    expect(validator.validate(input).diagnostics).toContainEqual(expect.objectContaining({
      code: "GENERATED_REQUIREMENT_MAPPING_MISSING", actual: expect.stringContaining("futuresAccount"),
    }));
  });

  it("fails empty and malformed frontend sources with locations", () => {
    const empty = validInput();
    empty.files.set(empty.spec.paths.businessForm, "  \n");
    expect(validator.validate(empty).diagnostics).toContainEqual(expect.objectContaining({ code: "EMPTY_GENERATED_FILE" }));

    const malformedTs = validInput();
    malformedTs.files.set(malformedTs.spec.paths.api, "export const broken = ;");
    expect(validator.validate(malformedTs).diagnostics).toContainEqual(expect.objectContaining({
      code: "TYPESCRIPT_SYNTAX_ERROR", line: expect.any(Number), column: expect.any(Number),
    }));

    const malformedVue = validInput();
    malformedVue.files.set(malformedVue.spec.paths.applyView, '<template><div></template>');
    expect(validator.validate(malformedVue).diagnostics).toContainEqual(expect.objectContaining({ code: "VUE_SYNTAX_ERROR" }));
  });

  it.each([
    ["direct fetch", "fetch('/api/reference-data/x')"],
    ["workflow submit", "start-submit"],
    ["attachment ownership", "new FormData()"],
    ["approval action", "approve()"],
  ])("blocks BusinessForm %s logic", (_label, forbidden) => {
    const input = validInput();
    input.files.set(input.spec.paths.businessForm, input.files.get(input.spec.paths.businessForm)!.replace("</script>", `${forbidden};\n</script>`));
    expect(validator.validate(input).diagnostics).toContainEqual(expect.objectContaining({ code: "GENERATED_FORM_BOUNDARY_VIOLATION" }));
  });

  it("blocks workflow request logic in Apply", () => {
    const input = validInput();
    input.files.set(input.spec.paths.applyView, input.files.get(input.spec.paths.applyView)!.replace("</script>", "fetch('/api/workflow/start');\n</script>"));
    expect(validator.validate(input).diagnostics).toContainEqual(expect.objectContaining({ code: "GENERATED_APPLY_BOUNDARY_VIOLATION" }));
  });

  it.each([
    ["platform endpoint", 'fetch("/api/platform/process")'],
    ["mutation method", 'fetch("/api/reference-data/x", { method: "POST" })'],
    ["task action", 'fetch("/api/workflow/tasks/1/approve")'],
  ])("blocks API %s", (_label, forbidden) => {
    const input = validInput();
    input.files.set(input.spec.paths.api, `${input.files.get(input.spec.paths.api)!}\n${forbidden};\n`);
    expect(validator.validate(input).diagnostics).toContainEqual(expect.objectContaining({ code: "GENERATED_API_MUTATION_FORBIDDEN" }));
  });

  it("blocks read-only endpoints that the requirement did not declare", () => {
    const input = validInput();
    input.files.set(input.spec.paths.api, `${input.files.get(input.spec.paths.api)!}\nfetch("/api/reference-data/internal-secrets");\n`);
    expect(validator.validate(input).diagnostics).toContainEqual(expect.objectContaining({ code: "GENERATED_API_ENDPOINT_UNDECLARED" }));
  });

  it("requires authenticated standalone routing and rejects public routing", () => {
    const missing = validInput();
    missing.files.set(missing.spec.paths.routeRegistry, missing.files.get(missing.spec.paths.routeRegistry)!.replace("standalone: true", "standalone: false"));
    expect(validator.validate(missing).diagnostics).toContainEqual(expect.objectContaining({ code: "GENERATED_ROUTE_BOUNDARY_VIOLATION" }));
    const publicRoute = validInput();
    publicRoute.files.set(publicRoute.spec.paths.routeRegistry, publicRoute.files.get(publicRoute.spec.paths.routeRegistry)!.replace("standalone: true", "standalone: true, public: true"));
    expect(validator.validate(publicRoute).diagnostics).toContainEqual(expect.objectContaining({ code: "GENERATED_ROUTE_BOUNDARY_VIOLATION" }));
  });
});

function validInput() {
  const requirement = structuredClone(WAREHOUSE_PLEDGE_REQUIREMENT);
  const spec = deriveGenerationSpec(requirement, contract);
  const files = new Map(Object.entries(createFakeGenerationFiles(
    requirement,
    spec,
    contract,
    'import type { RouteRecordRaw } from "vue-router";\nexport const generatedRoutes: RouteRecordRaw[] = [];\n',
  )));
  const manifest: ArtifactManifest = {
    generationId: "generation-static", targetRoot: "target", contractVersion: contract.contractVersion, revision: 1,
    files: spec.files.map((relativePath) => ({
      relativePath, changeType: relativePath === spec.paths.routeRegistry ? "MODIFY" : "ADD",
      stagedSha256: "b".repeat(64), sizeBytes: Buffer.byteLength(files.get(relativePath) || "", "utf8"),
      validationStatus: "PENDING", editedByUser: false,
    })),
  };
  return { generationId: manifest.generationId, revision: 1, requirement, contract, spec, manifest, files };
}
