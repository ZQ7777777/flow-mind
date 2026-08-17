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

  it("passes for a complete artifact set with valid Java, TypeScript, and Vue syntax", () => {
    const result = validator.validate(validInput());

    expect(result.status).toBe("PASSED");
    expect(result.diagnostics).toEqual([]);
  });

  it("fails when Java source has a clear syntax error and reports file position", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(path, input.files.get(path)!.replace("public class", "public class {"));

    const result = validator.validate(input);

    expect(result.status).toBe("FAILED");
    expect(result.diagnostics).toContainEqual(expect.objectContaining({
      code: "JAVA_SYNTAX_ERROR",
      relativePath: path,
      line: expect.any(Number),
      message: expect.any(String),
    }));
  });

  it("passes for valid TypeScript syntax", () => {
    const input = validInput();
    const path = input.spec.paths.api;
    input.files.set(path, `${input.files.get(path)!}\nexport const syntaxOnly = { ok: true };\n`);

    const result = validator.validate(input);

    expect(result.status).toBe("PASSED");
    expect(result.diagnostics).toEqual([]);
  });

  it("fails when TypeScript source has a clear syntax error", () => {
    const input = validInput();
    const path = input.spec.paths.api;
    input.files.set(path, `${input.files.get(path)!}\nexport const broken = ;\n`);

    const result = validator.validate(input);

    expect(result.status).toBe("FAILED");
    expect(result.diagnostics).toContainEqual(expect.objectContaining({
      code: "TYPESCRIPT_SYNTAX_ERROR",
      relativePath: path,
      line: expect.any(Number),
      column: expect.any(Number),
      message: expect.any(String),
    }));
  });

  it("passes for valid Vue SFC syntax", () => {
    const input = validInput();
    const path = input.spec.paths.view;
    input.files.set(path, `<script setup lang="ts">
const message: string = "ok";
</script>
<template><section>{{ message }}</section></template>
`);

    const result = validator.validate(input);

    expect(result.status).toBe("PASSED");
    expect(result.diagnostics).toEqual([]);
  });

  it("fails when Vue script TypeScript has a clear syntax error", () => {
    const input = validInput();
    const path = input.spec.paths.view;
    input.files.set(path, `<script setup lang="ts">
const message = ;
</script>
<template><section>{{ message }}</section></template>
`);

    const result = validator.validate(input);

    expect(result.status).toBe("FAILED");
    expect(result.diagnostics).toContainEqual(expect.objectContaining({
      code: "TYPESCRIPT_SYNTAX_ERROR",
      relativePath: path,
      line: expect.any(Number),
      column: expect.any(Number),
      message: expect.any(String),
    }));
  });

  it("fails when Vue SFC structure has a parser error", () => {
    const input = validInput();
    const path = input.spec.paths.view;
    input.files.set(path, "<script setup lang=\"ts\">const ok = true;</script><template><div></template>");

    const result = validator.validate(input);

    expect(result.status).toBe("FAILED");
    expect(result.diagnostics).toContainEqual(expect.objectContaining({
      code: "VUE_SYNTAX_ERROR",
      relativePath: path,
      message: expect.any(String),
    }));
  });

  it("fails when manifest, spec, and staging file sets differ", () => {
    const input = validInput();
    input.files.delete(input.spec.paths.api);

    const result = validator.validate(input);

    expect(result.status).toBe("FAILED");
    expect(result.diagnostics).toContainEqual(expect.objectContaining({
      code: "GENERATED_FILE_SET_MISMATCH",
    }));
  });

  it("fails when a required generated source file is empty", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(path, "   \n");

    const result = validator.validate(input);

    expect(result.status).toBe("FAILED");
    expect(result.diagnostics).toContainEqual(expect.objectContaining({
      code: "EMPTY_GENERATED_FILE",
      relativePath: path,
    }));
  });

  it("passes syntax-valid Java that uses a different business implementation style", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace(/\s*Map<String, Object> variables = new LinkedHashMap<String, Object>\(\);\n(?:\s*variables\.put\([^\n]+\n)+/, "\n")
        .replace("request.setVariables(variables);", "request.setVariables(input.toProcessVariables());")
        .replace("file.getSize() > 10485760L", "file.getSize() > 10 * 1024 * 1024L"),
    );

    const result = validator.validate(input);

    expect(result.status).toBe("PASSED");
    expect(result.diagnostics).toEqual([]);
  });

  it("does not block when syntax-valid code omits a business field mapping", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(path, input.files.get(path)!.replace(/\s*variables\.put\("amount"[^\n]+\n/, "\n"));

    const result = validator.validate(input);

    expect(result.status).toBe("PASSED");
    expect(result.diagnostics).toEqual([]);
  });

  it("does not block when syntax-valid code omits attachment business validation", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace(/bankReceiptCount < 1/g, "bankReceiptCount < -1")
        .replace(/bankReceiptCount > 5/g, "bankReceiptCount > 999")
        .replace(/file\.getSize\(\) > 10485760L/g, "false")
        .replace(/lowerName\.endsWith\("\.(?:pdf|jpg|png)"\)/g, "true"),
    );

    const result = validator.validate(input);

    expect(result.status).toBe("PASSED");
    expect(result.diagnostics).toEqual([]);
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
