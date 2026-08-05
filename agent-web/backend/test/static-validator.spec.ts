import { describe, expect, it } from "vitest";
import {
  ENTRY_APPLICATION_REQUIREMENT,
  type ArtifactManifest,
  type GenerationTargetContract,
} from "@flowmind/agent-contracts";
import { deriveGenerationSpec } from "../src/generation/generation-spec.js";
import { StaticValidatorService } from "../src/validation/static-validator.service.js";
import { createFakeGenerationFiles } from "../src/pi/fake-generation-files.js";

const contract: GenerationTargetContract = {
  contractVersion: "1.0",
  projectId: "fixture",
  backend: {
    rootDir: "backend",
    javaVersion: "8",
    springBootVersion: "2.7.18",
    basePackage: "com.flowmind.business",
    generatedSourceDir: "src/main/java/com/flowmind/business/generated",
    generatedTestDir: "src/test/java/com/flowmind/business/generated",
    starter: {
      groupId: "com.flowmind",
      artifactId: "platform-starter",
      version: "0.1.0-SNAPSHOT",
      allowedApi: "ProcessRuntimeService#startAndSubmit(StartProcessRequest)",
    },
    trustedUserContext: {
      accessorType: "com.flowmind.business.security.CurrentBusinessUserProvider",
      accessorMethod: "currentUser",
      userIdProperty: "userId",
      departmentIdProperty: "departmentId",
    },
    verificationProfile: "maven-java8",
  },
  frontend: {
    rootDir: "frontend",
    framework: "vue3",
    generatedViewDir: "src/modules/generated",
    generatedApiDir: "src/api/generated",
    generatedTestDir: "src/modules/generated/__tests__",
    routeRegistry: "src/router/generated-routes.ts",
    verificationProfile: "vue3-npm",
  },
  readableReferenceFiles: ["backend/pom.xml"],
  allowedOutputPatterns: [
    "backend/src/main/java/com/flowmind/business/generated/**/*.java",
    "backend/src/test/java/com/flowmind/business/generated/**/*.java",
    "frontend/src/modules/generated/**/*",
    "frontend/src/api/generated/**/*",
    "frontend/src/router/generated-routes.ts",
  ],
  protectedFiles: [{ path: "backend/pom.xml", sha256: "a".repeat(64) }],
};

describe("static generated-code validation", () => {
  const validator = new StaticValidatorService();

  it("accepts a complete M3 generated artifact set", () => {
    const input = validInput();
    const result = validator.validate(input);
    expect(result.status).toBe("PASSED");
    expect(result.diagnostics).toEqual([]);
  });

  it("accepts an attachment mapping that uses a static final string constant", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace(/(public class \w+Service \{)/, "$1\n    private static final String ATTACHMENT_BANK_RECEIPT = \"bankReceipt\";")
        .replace('item.setAttachmentCode("bankReceipt")', "item.setAttachmentCode(ATTACHMENT_BANK_RECEIPT)"),
    );

    const result = validator.validate(input);
    expect(result.status).toBe("PASSED");
    expect(result.diagnostics).toEqual([]);
  });

  it("accepts a DTO-owned process-variable mapper and an independent attachment file list", () => {
    const input = validInput();
    input.files.set(
      input.spec.paths.service,
      input.files.get(input.spec.paths.service)!
        .replace(/\s*variables\.put\([^\n]+/g, "")
        .replace("request.setVariables(variables);", "request.setProcessVariables(payload.toProcessVariables());"),
    );
    const dto = input.files.get(input.spec.paths.requestDto)!;
    const puts = input.requirement.formFields.map((field) => `vars.put(\"${field.fieldCode}\", null);`).join(" ");
    input.files.set(
      input.spec.paths.requestDto,
      dto.replace(/\n}\s*$/, `\n    public Map<String, Object> toProcessVariables() { Map<String, Object> vars = new HashMap<>(); ${puts} return vars; }\n}`),
    );
    input.files.set(
      input.spec.paths.view,
      input.files.get(input.spec.paths.view)!
        .replace(/form\.bankReceipt/g, "bankReceiptFileList")
        .replace('type="file"', 'name="bankReceipt" type="file"'),
    );

    expect(validator.validate(input).diagnostics).toEqual([]);
  });

  it("rejects an attachment constant whose resolved value differs from the contract", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace(/(public class \w+Service \{)/, "$1\n    private static final String ATTACHMENT_OTHER = \"otherAttachment\";")
        .replace('item.setAttachmentCode("bankReceipt")', "item.setAttachmentCode(ATTACHMENT_OTHER)"),
    );

    const result = validator.validate(input);
    expect(result.diagnostics.map(({ code }) => code)).toContain("ATTACHMENT_MAPPING_MISSING");
  });

  it("rejects platform actions outside the single allowed start call", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!.replace("runtimeService.startAndSubmit(request)", "runtimeService.approve(request)"),
    );
    const result = validator.validate(input);
    expect(result.status).toBe("FAILED");
    expect(result.diagnostics.map(({ code }) => code)).toEqual(
      expect.arrayContaining(["ALLOWED_API_CALL_COUNT", "PLATFORM_ACTION_FORBIDDEN"]),
    );
    expect(result.diagnostics.find(({ code }) => code === "PLATFORM_ACTION_FORBIDDEN"))
      .toEqual(expect.objectContaining({ relativePath: path, line: expect.any(Number) }));
  });

  it("rejects missing field mapping and weakened tests", () => {
    const input = validInput();
    const servicePath = input.spec.paths.service;
    input.files.set(
      servicePath,
      input.files.get(servicePath)!.replace(/\s*variables\.put\("amount"[^\n]+/, ""),
    );
    const testPath = input.spec.paths.apiTest;
    input.files.set(testPath, input.files.get(testPath)!.replace("describe(", "describe.skip("));
    const result = validator.validate(input);
    expect(result.diagnostics.map(({ code }) => code)).toEqual(
      expect.arrayContaining(["FORM_FIELD_MAPPING_MISSING", "TEST_WEAKENED"]),
    );
  });
});

function validInput() {
  const requirement = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
  const spec = deriveGenerationSpec(requirement, contract);
  const files = new Map(Object.entries(createFakeGenerationFiles(
    requirement,
    spec,
    contract,
    'import type { RouteRecordRaw } from "vue-router";\nexport const generatedRoutes: RouteRecordRaw[] = [];\n',
  )));
  const manifest: ArtifactManifest = {
    generationId: "generation-static",
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
  return { generationId: manifest.generationId, revision: 1, requirement, contract, spec, manifest, files };
}
