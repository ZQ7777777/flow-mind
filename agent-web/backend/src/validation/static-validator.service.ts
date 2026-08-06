import { Injectable } from "@nestjs/common";
import { parse as parseVueSfc } from "@vue/compiler-sfc";
import { lexAndParse, type IToken } from "java-parser";
import ts from "typescript";
import type {
  ArtifactManifest,
  BusinessRequirement,
  GenerationTargetContract,
  QualityDiagnostic,
  QualityStageResult,
} from "@flowmind/agent-contracts";
import type { GenerationSpec } from "../generation/generation-spec.js";
import { qualityDiagnostic, type DiagnosticDetails } from "../verification/quality-diagnostic.js";

export interface StaticValidationInput {
  generationId: string;
  revision: number;
  requirement: BusinessRequirement;
  contract: GenerationTargetContract;
  spec: GenerationSpec;
  manifest: ArtifactManifest;
  files: Map<string, string>;
}

@Injectable()
export class StaticValidatorService {
  validate(input: StaticValidationInput): QualityStageResult {
    const startedAt = new Date().toISOString();
    const started = Date.now();
    const diagnostics: QualityDiagnostic[] = [];
    this.validateFileSet(input, diagnostics);

    const javaTokens = new Map<string, IToken[]>();
    const sourceFiles = new Map<string, ts.SourceFile>();
    for (const [relativePath, content] of input.files) {
      if (relativePath.endsWith(".java")) {
        try {
          javaTokens.set(relativePath, lexAndParse(content).tokens);
        } catch (error) {
          diagnostics.push(diagnostic(
            "JAVA_SYNTAX_ERROR",
            error instanceof Error ? error.message : "Java syntax is invalid",
            relativePath,
            lineFromMessage(error),
          ));
        }
      } else if (relativePath.endsWith(".vue")) {
        this.parseVue(relativePath, content, sourceFiles, diagnostics);
      } else if (relativePath.endsWith(".ts")) {
        this.parseTypeScript(relativePath, content, sourceFiles, diagnostics);
      }
    }

    if (!diagnostics.some(({ code }) => code.endsWith("_SYNTAX_ERROR"))) {
      this.validateJavaBoundary(input, javaTokens, diagnostics);
      this.validateTypeScriptBoundary(input, sourceFiles, diagnostics);
      this.validateMappings(input, diagnostics);
      this.validateGeneratedTests(input, diagnostics);
    }

    const completedAt = new Date().toISOString();
    return {
      stage: "STATIC_VALIDATION",
      status: diagnostics.length ? "FAILED" : "PASSED",
      hardGate: true,
      summary: diagnostics.length
        ? `Static validation found ${diagnostics.length} blocking issue(s).`
        : "Static validation passed.",
      diagnostics,
      startedAt,
      completedAt,
      durationMs: Date.now() - started,
    };
  }

  private validateFileSet(input: StaticValidationInput, diagnostics: QualityDiagnostic[]): void {
    const expected = [...input.spec.files].sort();
    const manifest = input.manifest.files.map(({ relativePath }) => relativePath).sort();
    const actual = [...input.files.keys()].sort();
    if (!sameFiles(expected, manifest) || !sameFiles(expected, actual)) {
      diagnostics.push(diagnostic(
        "GENERATED_FILE_SET_MISMATCH",
        "Manifest, staged files, and the derived generation specification must contain the same exact files.",
      ));
    }
  }

  private parseVue(
    relativePath: string,
    content: string,
    sourceFiles: Map<string, ts.SourceFile>,
    diagnostics: QualityDiagnostic[],
  ): void {
    const parsed = parseVueSfc(content, { filename: relativePath });
    for (const error of parsed.errors) {
      const message = typeof error === "string" ? error : error.message;
      const line = typeof error === "string" || !("loc" in error)
        ? undefined : error.loc?.start.line;
      diagnostics.push(diagnostic("VUE_SYNTAX_ERROR", message, relativePath, line));
    }
    const script = parsed.descriptor.scriptSetup || parsed.descriptor.script;
    if (script) this.parseTypeScript(relativePath, script.content, sourceFiles, diagnostics);
  }

  private parseTypeScript(
    relativePath: string,
    content: string,
    sourceFiles: Map<string, ts.SourceFile>,
    diagnostics: QualityDiagnostic[],
  ): void {
    const source = ts.createSourceFile(relativePath, content, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
    sourceFiles.set(relativePath, source);
    const parseDiagnostics = (source as ts.SourceFile & { parseDiagnostics?: readonly ts.Diagnostic[] }).parseDiagnostics || [];
    for (const item of parseDiagnostics) {
      const position = item.start === undefined ? undefined : source.getLineAndCharacterOfPosition(item.start);
      diagnostics.push(diagnostic(
        "TYPESCRIPT_SYNTAX_ERROR",
        ts.flattenDiagnosticMessageText(item.messageText, "\n"),
        relativePath,
        position === undefined ? undefined : position.line + 1,
        position === undefined ? undefined : position.character + 1,
      ));
    }
  }

  private validateJavaBoundary(
    input: StaticValidationInput,
    tokensByPath: Map<string, IToken[]>,
    diagnostics: QualityDiagnostic[],
  ): void {
    const productionEntries = [...tokensByPath.entries()].filter(([path]) =>
      path.startsWith(`${input.contract.backend.rootDir}/${input.contract.backend.generatedSourceDir}/`),
    );
    const startCalls = productionEntries.flatMap(([path, tokens]) =>
      tokens.filter(({ image }) => image === "startAndSubmit").map((token) => ({ path, token })),
    );
    if (startCalls.length !== 1) {
      diagnostics.push(diagnostic(
        "ALLOWED_API_CALL_COUNT",
        `Production code must call startAndSubmit exactly once; found ${startCalls.length}.`,
        startCalls[0]?.path,
        startCalls[0]?.token.startLine,
      ));
    }

    const servicePath = input.spec.paths.service;
    const service = input.files.get(servicePath) || "";
    const productionSource = productionEntries.map(([path]) => input.files.get(path) || "").join("\n");
    const accessorType = input.contract.backend.trustedUserContext.accessorType.split(".").pop()!;
    const requiredApiSymbols = [
      "import com.flowmind.platform.api.service.ProcessRuntimeService;",
      "import com.flowmind.platform.api.request.StartProcessRequest;",
      "import com.flowmind.platform.api.request.AttachmentUploadItem;",
      "import com.flowmind.platform.api.dto.ProcessInstanceDTO;",
      `${accessorType}.BusinessUser`,
      ".setVariables(",
      ".setAttachments(",
      ".getCreatedTasks(",
    ];
    const missingApiSymbols = requiredApiSymbols.filter((symbol) =>
      symbol === ".getCreatedTasks(" ? !productionSource.includes(symbol) : !service.includes(symbol),
    );
    if (missingApiSymbols.length || productionSource.includes("com.flowmind.platform.runtime")) {
      diagnostics.push(diagnostic(
        "PLATFORM_API_CONTRACT_MISMATCH",
        `Generated backend must use the authoritative platform-starter API; missing or invalid symbols: ${missingApiSymbols.join(", ") || "com.flowmind.platform.runtime"}.`,
        servicePath,
      ));
    }

    const responsePath = input.spec.paths.responseDto;
    const response = input.files.get(responsePath) || "";
    const taskMappingSource = `${service}\n${response}`;
    const taskMappingSymbols = [
      "import com.flowmind.platform.api.dto.TaskDTO;",
      ".getTaskId()",
      ".getNodeCode()",
      ".getNodeName()",
    ];
    const missingTaskSymbols = taskMappingSymbols.filter((symbol) => !taskMappingSource.includes(symbol));
    const opaqueMapping = /\bList\s*<\s*\?\s*>\s+(?:rawTasks|createdTasks)\b/.test(taskMappingSource);
    if (missingTaskSymbols.length || opaqueMapping) {
      const mappingPath = response.includes("getCreatedTasks()") ? responsePath : servicePath;
      diagnostics.push(diagnostic(
        "PLATFORM_API_CONTRACT_MISMATCH",
        `Generated response must map TaskDTO summaries without dropping fields; missing or invalid symbols: ${missingTaskSymbols.join(", ") || "List<?> task mapping"}.`,
        mappingPath,
      ));
    }

    const forbiddenActions = new Set([
      "approve", "reject", "submitTask", "completeTask", "claimTask", "delegateTask",
      "publish", "activate", "saveDefinition", "createDefinition",
    ]);
    for (const [path, tokens] of productionEntries) {
      for (const token of tokens) {
        if (forbiddenActions.has(token.image)) {
          diagnostics.push(diagnostic(
            "PLATFORM_ACTION_FORBIDDEN",
            `Platform action ${token.image} is outside the generated business boundary.`,
            path,
            token.startLine,
            token.startColumn,
          ));
        }
      }
      const content = input.files.get(path) || "";
      this.forbidSource(content, path, diagnostics, "PLATFORM_HTTP_FORBIDDEN", [
        "RestTemplate", "WebClient", "HttpClient", "URLConnection", "OkHttpClient",
      ], "Generated backend code must not call platform HTTP APIs.");
      this.forbidSource(content, path, diagnostics, "PERSISTENCE_FORBIDDEN", [
        "Repository", "JdbcTemplate", "EntityManager", "createNativeQuery", "SELECT ", "INSERT ", "UPDATE ", "DELETE ",
      ], "Generated backend code must not access persistence or SQL.");
      this.forbidSource(content, path, diagnostics, "JAVA8_COMPATIBILITY", [
        "jakarta.", "List.of(", "Map.of(", "Set.of(", "record ", " sealed ", " permits ",
      ], "Generated backend code must remain Java 8 and javax compatible.");
    }
  }

  private validateTypeScriptBoundary(
    input: StaticValidationInput,
    sourceFiles: Map<string, ts.SourceFile>,
    diagnostics: QualityDiagnostic[],
  ): void {
    for (const [path, source] of sourceFiles) {
      const visit = (node: ts.Node): void => {
        if (ts.isCallExpression(node) && ts.isPropertyAccessExpression(node.expression)
          && ["skip", "only", "todo"].includes(node.expression.name.text)) {
          const owner = node.expression.expression.getText(source);
          if (["describe", "it", "test"].includes(owner)) {
            const location = source.getLineAndCharacterOfPosition(node.getStart(source));
            diagnostics.push(diagnostic(
              "TEST_WEAKENED",
              `Generated test uses ${owner}.${node.expression.name.text}().`,
              path,
              location.line + 1,
              location.character + 1,
            ));
          }
        }
        ts.forEachChild(node, visit);
      };
      visit(source);
    }

    const route = input.files.get(input.spec.paths.routeRegistry) || "";
    for (const value of [input.spec.routeName, input.spec.routePath, input.spec.classPrefix + "Apply.vue"]) {
      if (!route.includes(value)) {
        diagnostics.push(diagnostic(
          "ROUTE_REGISTRATION_MISSING",
          `Generated route registry is missing ${value}.`,
          input.spec.paths.routeRegistry,
        ));
      }
    }
    const api = input.files.get(input.spec.paths.api) || "";
    if (!api.includes(input.spec.apiPath)) {
      diagnostics.push(diagnostic("API_PATH_MISMATCH", "Generated frontend API path does not match the derived contract.", input.spec.paths.api));
    }
  }

  private validateMappings(input: StaticValidationInput, diagnostics: QualityDiagnostic[]): void {
    const service = input.files.get(input.spec.paths.service) || "";
    const request = input.files.get(input.spec.paths.requestDto) || "";
    const view = input.files.get(input.spec.paths.view) || "";
    const api = input.files.get(input.spec.paths.api) || "";
    for (const field of input.requirement.formFields) {
      if (!mapsFormField(service, request, field.fieldCode)) {
        diagnostics.push(diagnostic(
          "FORM_FIELD_MAPPING_MISSING",
          `Service does not map confirmed form field ${field.fieldCode}.`,
          input.spec.paths.service,
          undefined,
          undefined,
          {
            actual: `No supported process-variable mapping was found for ${field.fieldCode} in ${input.spec.paths.service} or ${input.spec.paths.requestDto}.`,
            expected: `Map the confirmed form field ${field.fieldCode} into StartProcessRequest variables.`,
            repairHint: `Add a mapping for ${field.fieldCode} using one of the accepted forms, keeping the field code exact.`,
            acceptedForms: [
              `variables.put("${field.fieldCode}", value)`,
              `variables.put(FIELD_CONSTANT, value) where FIELD_CONSTANT resolves to "${field.fieldCode}"`,
              `payload.toProcessVariables() with put("${field.fieldCode}", value) in the DTO mapper`,
            ],
            unsupportedForms: [
              `A differently spelled key or a constant whose value is not "${field.fieldCode}"`,
              "A DTO mapper that is not passed to request.setVariables(...)",
            ],
          },
        ));
      }
      if (!request.includes(field.fieldCode) || !view.includes(`form.${field.fieldCode}`) || !api.includes(field.fieldCode)) {
        diagnostics.push(diagnostic(
          "FORM_FIELD_CONTRACT_MISSING",
          `Generated DTO/view/API does not consistently expose ${field.fieldCode}.`,
        ));
      }
    }
    for (const attachment of input.spec.applyAttachments) {
      const attachmentCode = attachment.attachmentCode;
      if (!mapsAttachmentCode(service, attachmentCode)) {
        diagnostics.push(diagnostic(
          "ATTACHMENT_MAPPING_MISSING",
          `Generated backend service must map attachment ${attachmentCode} with setAttachmentCode().`,
          input.spec.paths.service,
        ));
      }
      const validationGaps = attachmentValidationGaps(service, attachment);
      if (validationGaps.length) {
        diagnostics.push(diagnostic(
          "ATTACHMENT_VALIDATION_MISSING",
          `Generated backend service does not enforce ${validationGaps.join(", ")} for attachment ${attachmentCode}.`,
          input.spec.paths.service,
          undefined,
          undefined,
          {
            expected: `Backend validation must enforce required/minCount=${attachment.minCount}, maxCount=${attachment.maxCount}, maxSizeBytes=${attachment.maxSizeBytes}, and allowed extensions before startAndSubmit.`,
            repairHint: `Validate ${attachmentCode} count, each file size, and each allowed extension before constructing AttachmentUploadItem or calling startAndSubmit.`,
          },
        ));
      }
      if (!mapsAttachmentView(view, attachmentCode)) {
        diagnostics.push(diagnostic(
          "ATTACHMENT_MAPPING_MISSING",
          `Generated Vue upload must expose attachment ${attachmentCode} through form.${attachmentCode} or an exact name="${attachmentCode}" part binding.`,
          input.spec.paths.view,
        ));
      }
      if (!mapsAttachmentApi(api, attachmentCode)) {
        diagnostics.push(diagnostic(
          "ATTACHMENT_MAPPING_MISSING",
          `Generated frontend API must append attachment ${attachmentCode} using the exact multipart part name.`,
          input.spec.paths.api,
        ));
      }
    }
  }

  private validateGeneratedTests(input: StaticValidationInput, diagnostics: QualityDiagnostic[]): void {
    const controllerPath = input.spec.paths.controllerTest;
    const controllerTest = input.files.get(controllerPath) || "";
    this.forbidSource(controllerTest, controllerPath, diagnostics, "GENERATED_TEST_CONTRACT_MISMATCH", [
      "@WebMvcTest",
    ], "Generated controller tests must use MockMvcBuilders.standaloneSetup because the target has no Spring Boot application class.");

    const servicePath = input.spec.paths.serviceTest;
    const serviceTest = input.files.get(servicePath) || "";
    this.forbidSource(serviceTest, servicePath, diagnostics, "GENERATED_TEST_CONTRACT_MISMATCH", [
      "mock(InstanceStatusEnum.class)",
      "anyList()",
    ], "Generated Java tests must use real enum values and nullable matchers for optional collection arguments.");
    const accessorMethod = escapeRegExp(input.contract.backend.trustedUserContext.accessorMethod);
    const beforeEachBody = /@BeforeEach\s*(?:\r?\n\s*)?(?:(?:public|protected|private)\s+)?void\s+\w+\s*\([^)]*\)\s*\{([^{}]*)\}/g;
    const globalTrustedUserStub = new RegExp(
      `\\b(?:when|doReturn)\\s*\\([\\s\\S]{0,200}\\.${accessorMethod}\\s*\\(\\s*\\)`,
    );
    const globalStubMatch = [...serviceTest.matchAll(beforeEachBody)].find((match) =>
      globalTrustedUserStub.test(match[1]),
    );
    if (globalStubMatch) {
      const location = lineAndColumn(serviceTest, globalStubMatch.index);
      diagnostics.push(diagnostic(
        "GENERATED_TEST_CONTRACT_MISMATCH",
        "Generated service tests must not install a trusted-user Mockito stub in @BeforeEach; validation tests do not consume it under strict stubbing.",
        servicePath,
        location.line,
        location.column,
      ));
    }

    const viewPath = input.spec.paths.viewTest;
    const viewTest = input.files.get(viewPath) || "";
    const frontendPatterns: Array<{ pattern: RegExp; label: string }> = [
      { pattern: /\.findAll\s*\(\s*(["'])option\1\s*\)/, label: "native option query" },
      { pattern: /\bwrapper\.vm\b/, label: "wrapper.vm private-state access" },
      { pattern: /\.find(?:All)?\s*\(\s*(["'])\.el-[^"']*\1\s*\)/, label: "Element Plus internal CSS query" },
      { pattern: /\b(?:payloadBlob|blob)\.text\s*\(/, label: "Blob.text() in jsdom" },
    ];
    const ignoredFileSize = /function\s+createMockFile\s*\([^)]*\bsize\b[^)]*\)\s*\{(?:(?!Uint8Array\s*\(\s*size\s*\))[\s\S])*?new\s+File\s*\(/m.exec(viewTest);
    if (ignoredFileSize) frontendPatterns.push({ pattern: /function\s+createMockFile/, label: "file factory ignores its size argument" });
    for (const { pattern, label } of frontendPatterns) {
      const match = pattern.exec(viewTest);
      if (!match) continue;
      const location = lineAndColumn(viewTest, match.index);
      diagnostics.push(diagnostic(
        "GENERATED_TEST_CONTRACT_MISMATCH",
        `Generated Element Plus/jsdom test uses unsupported or internal behavior: ${label}.`,
        viewPath,
        location.line,
        location.column,
      ));
    }
  }

  private forbidSource(
    content: string,
    path: string,
    diagnostics: QualityDiagnostic[],
    code: string,
    needles: string[],
    message: string,
  ): void {
    for (const needle of needles) {
      const offset = content.indexOf(needle);
      if (offset >= 0) {
        const location = lineAndColumn(content, offset);
        diagnostics.push(diagnostic(code, `${message} Found ${needle}.`, path, location.line, location.column));
      }
    }
  }
}

function mapsFormField(service: string, request: string, fieldCode: string): boolean {
  if (service.includes(`variables.put("${fieldCode}"`)) return true;
  if (mapsStringConstantArgument(service, "variables.put", fieldCode)) return true;
  // A DTO-owned mapper is equivalent when the service passes its complete map into the request.
  return service.includes("payload.toProcessVariables()")
    && (request.includes(`put("${fieldCode}"`) || mapsStringConstantArgument(request, "put", fieldCode));
}

function mapsAttachmentView(view: string, attachmentCode: string): boolean {
  if (view.includes(`form.${attachmentCode}`)) return true;

  const escapedCode = escapeRegExp(attachmentCode);
  const exactName = new RegExp(`\\bname\\s*=\\s*(["'])${escapedCode}\\1`, "i");
  const fileType = /\btype\s*=\s*(["'])file\1/i;
  for (const match of view.matchAll(/<(el-upload|input)\b[^>]*>/gi)) {
    const [, tagName] = match;
    const openingTag = match[0];
    if (exactName.test(openingTag) && (tagName.toLowerCase() === "el-upload" || fileType.test(openingTag))) {
      return true;
    }
  }
  return false;
}

function mapsAttachmentApi(api: string, attachmentCode: string): boolean {
  const escapedCode = escapeRegExp(attachmentCode);
  return new RegExp(`\\.append\\s*\\(\\s*(["'])${escapedCode}\\1\\s*,`).test(api);
}

function diagnostic(
  code: string,
  message: string,
  relativePath?: string,
  line?: number,
  column?: number,
  details: Partial<DiagnosticDetails> = {},
): QualityDiagnostic {
  return qualityDiagnostic("STATIC_VALIDATION", {
    code,
    message,
    hardGate: true,
    relativePath,
    line,
    column,
    actual: details.actual || message,
    expected: details.expected || `The generated artifacts must satisfy static rule ${code}.`,
    evidence: details.evidence || message,
    repairHint: details.repairHint || `Inspect the referenced generated artifact and correct static rule ${code} without changing the confirmed requirement.`,
    acceptedForms: details.acceptedForms,
    unsupportedForms: details.unsupportedForms,
    repairability: details.repairability || "CODE_ACTIONABLE",
  });
}

function sameFiles(left: string[], right: string[]): boolean {
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

function lineAndColumn(content: string, offset: number): { line: number; column: number } {
  const before = content.slice(0, offset).split("\n");
  return { line: before.length, column: (before.at(-1)?.length || 0) + 1 };
}

function lineFromMessage(error: unknown): number | undefined {
  const match = /line:\s*(\d+)/i.exec(error instanceof Error ? error.message : String(error));
  return match ? Number(match[1]) : undefined;
}

function mapsAttachmentCode(service: string, attachmentCode: string): boolean {
  const escapedCode = escapeRegExp(attachmentCode);
  if (new RegExp(`\\bsetAttachmentCode\\s*\\(\\s*"${escapedCode}"\\s*\\)`).test(service)) return true;

  const constantNames = new Set<string>();
  const declaration = /\b(?:public|protected|private)?\s*(?:static\s+final|final\s+static)\s+String\s+([A-Za-z_$][\w$]*)\s*=\s*"([A-Za-z][A-Za-z0-9]*)"\s*;/g;
  for (const match of service.matchAll(declaration)) {
    if (match[2] === attachmentCode) constantNames.add(match[1]);
  }

  return [...constantNames].some((name) =>
    new RegExp(`\\bsetAttachmentCode\\s*\\(\\s*${escapeRegExp(name)}\\s*\\)`).test(service),
  );
}

function attachmentValidationGaps(
  service: string,
  attachment: { attachmentCode: string; minCount: number; maxCount: number; maxSizeBytes: number; allowedExtensions: string[] },
): string[] {
  const escapedCode = escapeRegExp(attachment.attachmentCode);
  const gaps: string[] = [];
  const countName = `${escapedCode}Count`;
  if (!new RegExp(`${countName}\\s*<\\s*${attachment.minCount}`, "i").test(service)
    || !new RegExp(`${countName}\\s*>\\s*${attachment.maxCount}`, "i").test(service)) {
    gaps.push("required/count bounds");
  }
  if (!new RegExp(`\\.getSize\\(\\)\\s*>\\s*${attachment.maxSizeBytes}L?`).test(service)) {
    gaps.push("maximum file size");
  }
  const missingExtensions = attachment.allowedExtensions
    .map((extension) => extension.toLowerCase().replace(/^\./, ""))
    .filter((extension) => !service.includes(`endsWith(".${extension}")`));
  if (missingExtensions.length) gaps.push(`allowed extensions (${missingExtensions.join("/")})`);
  return gaps;
}

function mapsStringConstantArgument(source: string, call: string, value: string): boolean {
  const constantNames = new Set<string>();
  const declaration = /\b(?:public|protected|private)?\s*(?:static\s+final|final\s+static)\s+String\s+([A-Za-z_$][\w$]*)\s*=\s*"([^"]+)"\s*;/g;
  for (const match of source.matchAll(declaration)) {
    if (match[2] === value) constantNames.add(match[1]);
  }
  const escapedCall = escapeRegExp(call);
  return [...constantNames].some((name) =>
    new RegExp(`${escapedCall}\\s*\\(\\s*${escapeRegExp(name)}\\s*,`).test(source),
  );
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
