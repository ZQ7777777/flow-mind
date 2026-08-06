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

  it("accepts a DTO-owned process-variable mapper and an independently named attachment file list", () => {
    const input = validInput();
    input.files.set(
      input.spec.paths.service,
      input.files.get(input.spec.paths.service)!
        .replace(/\s*variables\.put\([^\n]+/g, "")
        .replace("request.setVariables(variables);", "request.setVariables(payload.toProcessVariables());"),
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
        .replace(/form\.bankReceipt/g, "fileList")
        .replace(/<input([^>]*?)type="file"([^>]*?)\/>/, '<el-upload$1name = \'bankReceipt\'$2 />'),
    );

    expect(validator.validate(input).diagnostics).toEqual([]);
  });

  it("accepts a process-variable key that uses a resolved static final string constant", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace(/(public class \w+Service \{)/, '$1\n    private static final String FIELD_AMOUNT = "amount";')
        .replace('variables.put("amount", payload.getAmount())', "variables.put(FIELD_AMOUNT, payload.getAmount())"),
    );

    expect(validator.validate(input).diagnostics.find(({ code }) => code === "FORM_FIELD_MAPPING_MISSING"))
      .toBeUndefined();
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
    expect(result.diagnostics).toContainEqual(expect.objectContaining({
      code: "ATTACHMENT_MAPPING_MISSING",
      relativePath: path,
    }));
  });

  it("rejects an attachment upload whose external part name differs from the contract", () => {
    const input = validInput();
    const path = input.spec.paths.view;
    input.files.set(
      path,
      input.files.get(path)!
        .replace(/form\.bankReceipt/g, "fileList")
        .replace(/<input([^>]*?)type="file"([^>]*?)\/>/, '<el-upload$1name="otherAttachment"$2 />'),
    );

    const result = validator.validate(input);
    expect(result.diagnostics).toContainEqual(expect.objectContaining({
      code: "ATTACHMENT_MAPPING_MISSING",
      relativePath: path,
      message: expect.stringContaining("Vue upload"),
    }));
  });

  it("rejects an attachment API whose multipart part name differs from the contract", () => {
    const input = validInput();
    const path = input.spec.paths.api;
    input.files.set(path, input.files.get(path)!.replace(/bankReceipt/g, "otherAttachment"));

    const result = validator.validate(input);
    expect(result.diagnostics).toContainEqual(expect.objectContaining({
      code: "ATTACHMENT_MAPPING_MISSING",
      relativePath: path,
      message: expect.stringContaining("frontend API"),
    }));
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

  it("rejects the captured user_sales platform API guesses before Maven compilation", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace("com.flowmind.platform.api.service.ProcessRuntimeService", "com.flowmind.platform.runtime.ProcessRuntimeService")
        .replace("com.flowmind.platform.api.request.StartProcessRequest", "com.flowmind.platform.runtime.dto.StartProcessRequest")
        .replace("com.flowmind.platform.api.request.AttachmentUploadItem", "com.flowmind.platform.runtime.dto.AttachmentUploadItem")
        .replace("com.flowmind.platform.api.dto.ProcessInstanceDTO", "com.flowmind.platform.runtime.dto.ProcessInstance")
        .replace("CurrentBusinessUserProvider.BusinessUser", "CurrentUser")
        .replace("request.setVariables(variables)", "request.setProcessVariables(variables)")
        .replace("request.setAttachments(attachments)", "request.setAttachmentItems(attachments)"),
    );
    const result = validator.validate(input);
    expect(result.status).toBe("FAILED");
    expect(result.diagnostics).toContainEqual(expect.objectContaining({
      code: "PLATFORM_API_CONTRACT_MISMATCH",
      relativePath: path,
    }));
  });

  it("rejects an opaque created-task mapping that drops task summary fields", () => {
    const input = validInput();
    const path = input.spec.paths.responseDto;
    input.files.set(
      path,
      input.files.get(path)!
        .replace("private List<CreatedTask> createdTasks;", "private List<?> createdTasks;"),
    );

    const result = validator.validate(input);
    expect(result.diagnostics).toContainEqual(expect.objectContaining({
      code: "PLATFORM_API_CONTRACT_MISMATCH",
      relativePath: path,
    }));
  });

  it("rejects captured backend test environment mismatches", () => {
    const input = validInput();
    input.files.set(
      input.spec.paths.controllerTest,
      input.files.get(input.spec.paths.controllerTest)! + "\n@WebMvcTest(EntryApplicationController.class) class SliceTest {}\n",
    );
    input.files.set(
      input.spec.paths.serviceTest,
      input.files.get(input.spec.paths.serviceTest)! + "\n@BeforeEach void setUp() { when(currentBusinessUserProvider.currentUser()).thenReturn(user); }\n",
    );

    const result = validator.validate(input);
    expect(result.diagnostics.filter(({ code }) => code === "GENERATED_TEST_CONTRACT_MISMATCH")).toHaveLength(2);
  });

  it("rejects captured Element Plus and jsdom test anti-patterns", () => {
    const input = validInput();
    const path = input.spec.paths.viewTest;
    input.files.set(
      path,
      input.files.get(path)! + `\nwrapper.findAll("option");\nwrapper.vm.form.amount = 1;\nwrapper.find(".el-form-item__error");\npayloadBlob.text();\n`,
    );

    const result = validator.validate(input);
    expect(result.diagnostics.filter(({ code }) => code === "GENERATED_TEST_CONTRACT_MISMATCH")).toHaveLength(4);
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
    expect(result.diagnostics.find(({ code }) => code === "FORM_FIELD_MAPPING_MISSING"))
      .toEqual(expect.objectContaining({
        diagnosticId: expect.any(String),
        stage: "STATIC_VALIDATION",
        actual: expect.stringContaining("amount"),
        expected: expect.stringContaining("amount"),
        repairHint: expect.stringContaining("amount"),
        acceptedForms: expect.arrayContaining([expect.stringContaining('variables.put("amount"')]),
        repairability: "CODE_ACTIONABLE",
      }));
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
