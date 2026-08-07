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

  it("accepts the user_sales formData model and extracted-extension validation style", () => {
    const input = validInput();
    input.files.set(
      input.spec.paths.view,
      input.files.get(input.spec.paths.view)!
        .replace(/\bform\./g, "formData.")
        .replace("const form = reactive", "const formData = reactive"),
    );
    input.files.set(
      input.spec.paths.service,
      useDirectAttachmentValidation(input.files.get(input.spec.paths.service)!, "bankReceiptFiles"),
    );

    expect(validator.validate(input).diagnostics).toEqual([]);
  });

  it("accepts the user_manager direct collection checks and equality extension checks", () => {
    const input = validInput();
    input.files.set(
      input.spec.paths.service,
      useDirectAttachmentValidation(input.files.get(input.spec.paths.service)!, "bankReceipt"),
    );

    expect(validator.validate(input).diagnostics).toEqual([]);
  });

  it("accepts a separately extracted extension checked through an allowed collection", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace(
          "String lowerName = file.getOriginalFilename() == null ? \"\" : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);",
          "String originalFilename = file.getOriginalFilename();\n                String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);",
        )
        .replace(
          /if \(!\(lowerName\.endsWith\("\.pdf"\) \|\| lowerName\.endsWith\("\.jpg"\) \|\| lowerName\.endsWith\("\.png"\)\)\)/,
          'if (!java.util.Arrays.asList("pdf", "jpg", "png").contains(extension))',
        ),
    );

    expect(validator.validate(input).diagnostics).toEqual([]);
  });

  it("tracks filename normalization across multiple string variables", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace(
          "String lowerName = file.getOriginalFilename() == null ? \"\" : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);",
          "String originalFilename = file.getOriginalFilename();\n                String normalizedFilename = originalFilename.toLowerCase(java.util.Locale.ROOT);\n                String extension = normalizedFilename.substring(normalizedFilename.lastIndexOf('.') + 1);",
        )
        .replace(
          /if \(!\(lowerName\.endsWith\("\.pdf"\) \|\| lowerName\.endsWith\("\.jpg"\) \|\| lowerName\.endsWith\("\.png"\)\)\)/,
          'if (!"pdf".equals(extension) && !"jpg".equals(extension) && !"png".equals(extension))',
        ),
    );

    expect(validator.validate(input).diagnostics).toEqual([]);
  });

  it("does not accept an allowed collection check on an unrelated string", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace(
          "String lowerName = file.getOriginalFilename() == null ? \"\" : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);",
          "String originalFilename = file.getOriginalFilename();\n                String extension = suppliedExtension;",
        )
        .replace(
          /if \(!\(lowerName\.endsWith\("\.pdf"\) \|\| lowerName\.endsWith\("\.jpg"\) \|\| lowerName\.endsWith\("\.png"\)\)\)/,
          'if (!java.util.Arrays.asList("pdf", "jpg", "png").contains(extension))',
        ),
    );

    expect(validator.validate(input).diagnostics.find(({ code }) => code === "ATTACHMENT_VALIDATION_MISSING"))
      .toEqual(expect.objectContaining({ message: expect.stringContaining("allowedExtensions=pdf/jpg/png") }));
  });

  it("does not treat an arbitrary filename substring as an extracted extension", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace(
          "String lowerName = file.getOriginalFilename() == null ? \"\" : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);",
          "String originalFilename = file.getOriginalFilename();\n                String extension = originalFilename.substring(0, 1);",
        )
        .replace(
          /if \(!\(lowerName\.endsWith\("\.pdf"\) \|\| lowerName\.endsWith\("\.jpg"\) \|\| lowerName\.endsWith\("\.png"\)\)\)/,
          'if (!java.util.Arrays.asList("pdf", "jpg", "png").contains(extension))',
        ),
    );

    expect(validator.validate(input).diagnostics.find(({ code }) => code === "ATTACHMENT_VALIDATION_MISSING"))
      .toEqual(expect.objectContaining({ message: expect.stringContaining("allowedExtensions=pdf/jpg/png") }));
  });

  it("does not accept extension validation placed after startAndSubmit", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace(
          /\s*String lowerName = file\.getOriginalFilename\(\) == null \? "" : file\.getOriginalFilename\(\)\.toLowerCase\(java\.util\.Locale\.ROOT\);\s*if \(!\(lowerName\.endsWith\("\.pdf"\) \|\| lowerName\.endsWith\("\.jpg"\) \|\| lowerName\.endsWith\("\.png"\)\)\) throw new IllegalArgumentException\([^\n]+/,
          "",
        )
        .replace(
          "ProcessInstanceDTO result = runtimeService.startAndSubmit(request);",
          "ProcessInstanceDTO result = runtimeService.startAndSubmit(request);\n        String lowerName = \"receipt.pdf\";\n        if (!lowerName.endsWith(\".pdf\") || !lowerName.endsWith(\".jpg\") || !lowerName.endsWith(\".png\")) throw new IllegalArgumentException(\"late\");",
        ),
    );

    expect(validator.validate(input).diagnostics.find(({ code }) => code === "ATTACHMENT_VALIDATION_MISSING"))
      .toEqual(expect.objectContaining({ message: expect.stringContaining("allowedExtensions=pdf/jpg/png") }));
  });

  it("accepts numeric constants, digit separators, and a validation helper called before startAndSubmit", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace(
          /(public class \w+Service \{)/,
          "$1\n    private static final int MIN_FILES = 1;\n    private static final int MAX_FILES = 5;\n    private static final long MAX_FILE_SIZE = 10_485_760L;",
        )
        .replace("bankReceiptCount < 1", "bankReceiptCount < MIN_FILES")
        .replace("bankReceiptCount > 5", "bankReceiptCount > MAX_FILES")
        .replace("file.getSize() > 10485760L", "file.getSize() > MAX_FILE_SIZE")
        .replace(
          "String lowerName = file.getOriginalFilename() == null ? \"\" : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);",
          "validateBankReceiptExtension(file);",
        )
        .replace(
          /\s*if \(!\(lowerName\.endsWith\("\.pdf"\) \|\| lowerName\.endsWith\("\.jpg"\) \|\| lowerName\.endsWith\("\.png"\)\)\) throw new IllegalArgumentException\([^\n]+/,
          "",
        )
        .replace(
          /\n}\s*$/,
          `\n    private void validateBankReceiptExtension(MultipartFile file) {\n        String extension = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.') + 1);\n        if (!\"pdf\".equals(extension) && !\"jpg\".equals(extension) && !\"png\".equals(extension)) throw new IllegalArgumentException(\"invalid extension\");\n    }\n}\n`,
        ),
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

  it("accepts attachment validation delegated through renamed collection and file parameters", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace(
          /\s*int bankReceiptCount = [^;]+;\s*if \(bankReceiptCount < 1 \|\| bankReceiptCount > 5\) \{[^}]+\}/,
          "\n        validateBankReceipts(bankReceiptFiles);",
        )
        .replace(
          /\s*if \(bankReceiptFiles != null\) \{\s*for \(MultipartFile file : bankReceiptFiles\) \{\s*if \(file\.getSize\(\) > 10485760L\) throw new IllegalArgumentException\([^\n]+\);\s*String lowerName = [^;]+;\s*if \(!\(lowerName\.endsWith\("\.pdf"\) \|\| lowerName\.endsWith\("\.jpg"\) \|\| lowerName\.endsWith\("\.png"\)\)\) throw new IllegalArgumentException\([^\n]+\);/,
          "\n        if (bankReceiptFiles != null) {\n            for (MultipartFile file : bankReceiptFiles) {",
        )
        .replace(
          /\n}\s*$/,
          `\n    private void validateBankReceipts(java.util.List<MultipartFile> files) {\n        if (files == null || files.isEmpty()) throw new IllegalArgumentException("required");\n        if (files.size() > 5) throw new IllegalArgumentException("too many");\n        for (MultipartFile upload : files) validateBankReceiptFile(upload);\n    }\n\n    private void validateBankReceiptFile(MultipartFile upload) {\n        if (upload.getSize() > 10485760L) throw new IllegalArgumentException("too large");\n        String lowerName = upload.getOriginalFilename() == null ? "" : upload.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);\n        if (!(lowerName.endsWith(".pdf") || lowerName.endsWith(".jpg") || lowerName.endsWith(".png"))) throw new IllegalArgumentException("invalid extension");\n    }\n}\n`,
        ),
    );

    expect(validator.validate(input).diagnostics).toEqual([]);
  });

  it("does not borrow attachment checks from an unrelated collection", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace("bankReceiptCount > 5", "bankReceiptCount > 999")
        .replace(/\n}\s*$/, "\n    private void validateOther(java.util.List<MultipartFile> otherFiles) { if (otherFiles.size() > 5) throw new IllegalArgumentException(\"too many\"); }\n}\n"),
    );

    expect(validator.validate(input).diagnostics.find(({ code }) => code === "ATTACHMENT_VALIDATION_MISSING"))
      .toEqual(expect.objectContaining({ message: expect.stringContaining("maxCount=5") }));
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

  it("rejects attachment mapping that omits backend count, size, and extension enforcement", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace("bankReceiptCount < 1", "bankReceiptCount < 0")
        .replace("bankReceiptCount > 5", "bankReceiptCount > 999")
        .replace("file.getSize() > 10485760L", "file.getSize() > Long.MAX_VALUE")
        .replace(/lowerName\.endsWith\("\.(?:pdf|jpg|png)"\)/g, "false"),
    );

    const result = validator.validate(input);
    expect(result.diagnostics).toContainEqual(expect.objectContaining({
      code: "ATTACHMENT_VALIDATION_MISSING",
      relativePath: path,
      message: expect.stringContaining("bankReceipt"),
      actual: expect.stringContaining("Missing:"),
      expected: expect.stringContaining("minCount=1"),
      evidence: expect.stringContaining("Attachment collection candidates"),
      repairHint: expect.stringContaining("missing checks"),
      acceptedForms: expect.arrayContaining([expect.stringContaining("isEmpty")]),
    }));
  });

  it.each([
    ["required/minCount=1", (source: string) => source.replace("bankReceiptCount < 1", "bankReceiptCount < 0")],
    ["maxCount=5", (source: string) => source.replace("bankReceiptCount > 5", "bankReceiptCount > 999")],
    ["maxSizeBytes=10485760", (source: string) => source.replace("file.getSize() > 10485760L", "file.getSize() > Long.MAX_VALUE")],
    ["allowedExtensions=pdf", (source: string) => source.replace('lowerName.endsWith(".pdf")', "false")],
  ])("reports the specific missing attachment subrule %s", (missing, mutate) => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(path, mutate(input.files.get(path)!));

    const diagnostic = validator.validate(input).diagnostics.find(({ code }) => code === "ATTACHMENT_VALIDATION_MISSING");
    expect(diagnostic).toEqual(expect.objectContaining({
      relativePath: path,
      message: expect.stringContaining(missing),
      actual: expect.stringContaining(missing),
    }));
  });

  it("does not accept attachment validation patterns that appear only in comments", () => {
    const input = validInput();
    const path = input.spec.paths.service;
    input.files.set(
      path,
      input.files.get(path)!
        .replace("bankReceiptCount < 1", "bankReceiptCount < 0")
        .replace("bankReceiptCount > 5", "bankReceiptCount > 999")
        .replace("file.getSize() > 10485760L", "file.getSize() > Long.MAX_VALUE")
        .replace(/lowerName\.endsWith\("\.(?:pdf|jpg|png)"\)/g, "false")
        .replace(/\n}\s*$/, '\n// bankReceiptCount < 1; bankReceiptCount > 5; file.getSize() > 10485760L; lowerName.endsWith(".pdf"); lowerName.endsWith(".jpg"); lowerName.endsWith(".png");\n}\n'),
    );

    const diagnostic = validator.validate(input).diagnostics.find(({ code }) => code === "ATTACHMENT_VALIDATION_MISSING");
    expect(diagnostic?.message).toEqual(expect.stringContaining("required/minCount=1"));
    expect(diagnostic?.message).toEqual(expect.stringContaining("maxCount=5"));
    expect(diagnostic?.message).toEqual(expect.stringContaining("maxSizeBytes=10485760"));
    expect(diagnostic?.message).toEqual(expect.stringContaining("allowedExtensions=pdf/jpg/png"));
  });

  it("reports separate path-bound diagnostics for each missing Vue field", () => {
    const input = validInput();
    const path = input.spec.paths.view;
    input.files.set(path, input.files.get(path)!.replace(/\bform\.(applicantName|amount|accountNo)\b/g, "form.removedField"));

    const diagnostics = validator.validate(input).diagnostics.filter(({ code, relativePath }) =>
      code === "FORM_FIELD_CONTRACT_MISSING" && relativePath === path,
    );
    expect(diagnostics).toHaveLength(input.requirement.formFields.length);
    expect(new Set(diagnostics.map(({ diagnosticId }) => diagnosticId)).size).toBe(input.requirement.formFields.length);
    expect(diagnostics.every(({ actual, expected, repairHint }) => Boolean(actual && expected && repairHint))).toBe(true);
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
    expect(result.diagnostics.find(({ message }) => message.includes("wrapper.vm"))).toEqual(expect.objectContaining({
      repairHint: expect.stringContaining("update:modelValue/update:fileList"),
      acceptedForms: expect.arrayContaining([expect.stringContaining("ElUpload")]),
    }));
  });

  it("accepts Element Plus public update events without root wrapper state access", () => {
    const input = validInput();
    input.files.set(
      input.spec.paths.viewTest,
      input.files.get(input.spec.paths.viewTest)! + `
const applicationNo = wrapper.findComponent({ name: "ElInput" });
applicationNo.vm.$emit("update:modelValue", "APP-001");
const amount = wrapper.findComponent({ name: "ElInputNumber" });
amount.vm.$emit("update:modelValue", 10);
const currency = wrapper.findComponent({ name: "ElSelect" });
currency.vm.$emit("update:modelValue", "CNY");
const upload = wrapper.findComponent({ name: "ElUpload" });
upload.vm.$emit("update:fileList", []);
`,
    );

    expect(validator.validate(input).diagnostics.filter(({ code }) => code === "GENERATED_TEST_CONTRACT_MISMATCH"))
      .toEqual([]);
  });

  it("rejects enum mocks, non-nullable list matchers, and fake file sizes", () => {
    const input = validInput();
    input.files.set(
      input.spec.paths.serviceTest,
      input.files.get(input.spec.paths.serviceTest)! + "\n// mock(InstanceStatusEnum.class)\n// verify(service).submit(any(), anyList(), anyString());\n",
    );
    input.files.set(
      input.spec.paths.viewTest,
      input.files.get(input.spec.paths.viewTest)! + '\nfunction createMockFile(name: string, size: number) { return new File(["tiny"], name); }\n',
    );

    const result = validator.validate(input);
    expect(result.diagnostics.filter(({ code }) => code === "GENERATED_TEST_CONTRACT_MISMATCH")).toHaveLength(3);
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

function useDirectAttachmentValidation(source: string, collectionName: string): string {
  return source
    .replace(/bankReceiptFiles/g, collectionName)
    .replace(
      /\s*int bankReceiptCount = [^;]+;\s*if \(bankReceiptCount < 1 \|\| bankReceiptCount > 5\) \{[^}]+\}/,
      `\n        if (${collectionName} == null || ${collectionName}.isEmpty()) throw new IllegalArgumentException("required");\n        if (${collectionName}.size() > 5) throw new IllegalArgumentException("too many");`,
    )
    .replace(
      "String lowerName = file.getOriginalFilename() == null ? \"\" : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);",
      "String extension = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);",
    )
    .replace(
      /if \(!\(lowerName\.endsWith\("\.pdf"\) \|\| lowerName\.endsWith\("\.jpg"\) \|\| lowerName\.endsWith\("\.png"\)\)\)/,
      'if (!"pdf".equals(extension) && !"jpg".equals(extension) && !"png".equals(extension))',
    );
}
