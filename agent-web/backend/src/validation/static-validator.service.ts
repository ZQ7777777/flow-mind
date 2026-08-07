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
      this.validateMappings(input, sourceFiles, diagnostics);
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

  private validateMappings(
    input: StaticValidationInput,
    sourceFiles: Map<string, ts.SourceFile>,
    diagnostics: QualityDiagnostic[],
  ): void {
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
      if (!javaDtoExposesField(request, field.fieldCode)) {
        diagnostics.push(diagnostic(
          "FORM_FIELD_CONTRACT_MISSING",
          `Generated request DTO does not expose confirmed form field ${field.fieldCode}.`,
          input.spec.paths.requestDto,
          undefined,
          undefined,
          {
            actual: `No Java property or accessor named ${field.fieldCode} was found in the request DTO.`,
            expected: `The request DTO must expose a Java property named ${field.fieldCode}.`,
            evidence: `Inspected ${input.spec.paths.requestDto} for an exact ${field.fieldCode} property or accessor.`,
            repairHint: `Add the ${field.fieldCode} property and its JavaBean accessor methods to the generated request DTO.`,
            acceptedForms: [
              `private Type ${field.fieldCode}; with get${upperFirst(field.fieldCode)}()/set${upperFirst(field.fieldCode)}(...)`,
              `A public JavaBean accessor for ${field.fieldCode}`,
            ],
          },
        ));
      }
      if (!vueViewExposesField(view, field.fieldCode)) {
        diagnostics.push(diagnostic(
          "FORM_FIELD_CONTRACT_MISSING",
          `Generated Vue form does not expose confirmed form field ${field.fieldCode}.`,
          input.spec.paths.view,
          undefined,
          undefined,
          {
            actual: `No binding for ${field.fieldCode} was found on the model declared by <el-form :model> or through an exact name attribute.`,
            expected: `The generated Vue form must bind a control to ${field.fieldCode}.`,
            evidence: `Inspected ${input.spec.paths.view} for ${field.fieldCode}. Recognized Vue form models: ${vueFormModels(view).join(", ") || "none"}.`,
            repairHint: `Bind the field through the actual form model, for example v-model="formData.${field.fieldCode}", or use name="${field.fieldCode}".`,
            acceptedForms: [
              `<el-form :model="formData"> with v-model="formData.${field.fieldCode}"`,
              `A form control with name="${field.fieldCode}"`,
            ],
            unsupportedForms: [`A field mentioned only in comments or display text`],
          },
        ));
      }
      if (!typeScriptExposesProperty(sourceFiles.get(input.spec.paths.api), field.fieldCode)) {
        diagnostics.push(diagnostic(
          "FORM_FIELD_CONTRACT_MISSING",
          `Generated frontend API does not expose confirmed form field ${field.fieldCode}.`,
          input.spec.paths.api,
          undefined,
          undefined,
          {
            actual: `No TypeScript property named ${field.fieldCode} was found in the generated API contract.`,
            expected: `The frontend API payload contract must expose ${field.fieldCode}.`,
            evidence: `Inspected the TypeScript syntax tree for ${field.fieldCode} in ${input.spec.paths.api}.`,
            repairHint: `Add ${field.fieldCode} to the payload interface/type used by the generated submit API.`,
            acceptedForms: [
              `${field.fieldCode}: Type in an interface or type literal`,
              `An object property or shorthand property named ${field.fieldCode}`,
            ],
          },
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
      const validation = inspectAttachmentValidation(service, attachment);
      if (validation.missing.length) {
        diagnostics.push(diagnostic(
          "ATTACHMENT_VALIDATION_MISSING",
          `Generated backend service does not enforce ${validation.missing.join(", ")} for attachment ${attachmentCode}.`,
          input.spec.paths.service,
          undefined,
          undefined,
          {
            actual: `Recognized before startAndSubmit: ${validation.recognized.join("; ") || "no attachment validation"}. Missing: ${validation.missing.join(", ")}.`,
            expected: `Backend validation before startAndSubmit must enforce required/minCount=${attachment.minCount}, maxCount=${attachment.maxCount}, maxSizeBytes=${attachment.maxSizeBytes}, and allowed extensions ${attachment.allowedExtensions.join("/")}.`,
            evidence: `Attachment collection candidates: ${validation.collectionNames.join(", ") || "none"}. ${validation.recognized.join("; ") || "No supported validation evidence was recognized."}`,
            repairHint: `Add only the missing checks (${validation.missing.join(", ")}) before startAndSubmit; equivalent direct expressions, local variables, and resolved constants are accepted.`,
            acceptedForms: [
              `required/minCount: null/isEmpty or collection size compared with ${attachment.minCount}`,
              `maxCount: collection size or a derived count variable compared with ${attachment.maxCount}`,
              `maxSizeBytes: file.getSize() compared with ${attachment.maxSizeBytes}, directly or through a numeric constant`,
              `allowedExtensions: endsWith, extracted-extension equals, or an allowed collection contains check for ${attachment.allowedExtensions.join("/")}`,
            ],
            unsupportedForms: [
              "Validation mentioned only in comments, messages, or tests",
              "Validation that occurs only after startAndSubmit",
            ],
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

function javaDtoExposesField(source: string, fieldCode: string): boolean {
  const escaped = escapeRegExp(fieldCode);
  const property = new RegExp(`\\b(?:private|protected|public)\\s+[A-Za-z_$][\\w$<>?,. \\t]*\\s+${escaped}\\s*;`);
  const accessor = new RegExp(`\\b(?:get|is|set)${escapeRegExp(upperFirst(fieldCode))}\\s*\\(`);
  return property.test(source) || accessor.test(source);
}

function vueFormModels(source: string): string[] {
  const models = new Set<string>();
  for (const match of source.matchAll(/<el-form\b[^>]*\b(?::model|v-bind:model)\s*=\s*(["'])([A-Za-z_$][\w$]*)\1/gi)) {
    models.add(match[2]);
  }
  for (const match of source.matchAll(/\bv-model(?::[\w-]+)?\s*=\s*(["'])([A-Za-z_$][\w$]*)\s*\./gi)) {
    models.add(match[2]);
  }
  return [...models];
}

function vueViewExposesField(source: string, fieldCode: string): boolean {
  const escaped = escapeRegExp(fieldCode);
  if (new RegExp(`\\bname\\s*=\\s*(["'])${escaped}\\1`, "i").test(source)) return true;
  if (new RegExp(`\\bv-model(?::[\\w-]+)?\\s*=\\s*(["'])[A-Za-z_$][\\w$]*\\s*\\.\\s*${escaped}\\b`, "i").test(source)) {
    return true;
  }
  return vueFormModels(source).some((model) =>
    new RegExp(`\\b${escapeRegExp(model)}\\s*\\.\\s*${escaped}\\b`).test(source),
  );
}

function typeScriptExposesProperty(source: ts.SourceFile | undefined, fieldCode: string): boolean {
  if (!source) return false;
  let found = false;
  const visit = (node: ts.Node): void => {
    if (found) return;
    if ((ts.isPropertySignature(node) || ts.isPropertyDeclaration(node) || ts.isPropertyAssignment(node)
      || ts.isShorthandPropertyAssignment(node) || ts.isMethodSignature(node))
      && propertyNameText(node.name) === fieldCode) {
      found = true;
      return;
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return found;
}

function propertyNameText(name: ts.PropertyName): string | undefined {
  if (ts.isIdentifier(name) || ts.isStringLiteral(name) || ts.isNumericLiteral(name)) return name.text;
  return undefined;
}

function mapsAttachmentView(view: string, attachmentCode: string): boolean {
  if (view.includes(`form.${attachmentCode}`)) return true;
  if (vueFormModels(view).some((model) =>
    new RegExp(`\\b${escapeRegExp(model)}\\s*\\.\\s*${escapeRegExp(attachmentCode)}\\b`).test(view),
  )) return true;

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

interface AttachmentValidationInspection {
  missing: string[];
  recognized: string[];
  collectionNames: string[];
}

function inspectAttachmentValidation(
  service: string,
  attachment: { attachmentCode: string; minCount: number; maxCount: number; maxSizeBytes: number; allowedExtensions: string[] },
): AttachmentValidationInspection {
  const validationSource = normalizeJavaNumbers(javaValidationSourceBeforeRuntimeCall(stripJavaComments(service)));
  const constants = javaNumericConstants(validationSource);
  const collectionNames = attachmentCollectionNames(validationSource, attachment.attachmentCode);
  const countSubjects = attachmentCountSubjects(validationSource, collectionNames);
  const fileNames = attachmentFileNames(validationSource, collectionNames);
  const recognized: string[] = [];
  const missing: string[] = [];

  const nullHandled = collectionNames.some((name) =>
    new RegExp(`\\b${escapeRegExp(name)}\\s*==\\s*null\\b`).test(validationSource),
  );
  const emptyHandled = collectionNames.some((name) =>
    new RegExp(`\\b${escapeRegExp(name)}\\s*\\.\\s*isEmpty\\s*\\(\\s*\\)`).test(validationSource),
  );
  const minHandled = attachment.minCount <= 0 || emptyHandled
    || hasBoundComparison(validationSource, countSubjects, "minimum", attachment.minCount, constants);
  const requiredAndMinimumHandled = minHandled && (attachment.minCount <= 0 || nullHandled);
  if (requiredAndMinimumHandled) {
    recognized.push(`required/minCount=${attachment.minCount}`);
  } else {
    missing.push(`required/minCount=${attachment.minCount}`);
  }

  const maxHandled = hasBoundComparison(validationSource, countSubjects, "maximum", attachment.maxCount, constants);
  if (maxHandled) recognized.push(`maxCount=${attachment.maxCount}`);
  else missing.push(`maxCount=${attachment.maxCount}`);

  const sizeHandled = hasNumericComparison(
    validationSource,
    fileNames.map((name) => `${escapeRegExp(name)}\\s*\\.\\s*getSize\\s*\\(\\s*\\)`),
    "maximum",
    attachment.maxSizeBytes,
    constants,
  );
  if (sizeHandled) recognized.push(`maxSizeBytes=${attachment.maxSizeBytes}`);
  else missing.push(`maxSizeBytes=${attachment.maxSizeBytes}`);

  const normalizedExtensions = attachment.allowedExtensions.map((item) => item.toLowerCase().replace(/^\./, ""));
  const missingExtensions = normalizedExtensions.filter((extension) =>
    !hasDirectExtensionCheck(validationSource, extension, fileNames)
      && !hasAllowedExtensionCollection(validationSource, normalizedExtensions, fileNames),
  );
  if (!missingExtensions.length) recognized.push(`allowedExtensions=${normalizedExtensions.join("/")}`);
  else missing.push(`allowedExtensions=${missingExtensions.join("/")}`);

  return { missing, recognized, collectionNames };
}

function stripJavaComments(source: string): string {
  let result = "";
  let state: "CODE" | "STRING" | "CHAR" | "LINE_COMMENT" | "BLOCK_COMMENT" = "CODE";
  let escaped = false;
  for (let index = 0; index < source.length; index += 1) {
    const char = source[index];
    const next = source[index + 1];
    if (state === "LINE_COMMENT") {
      if (char === "\n") {
        state = "CODE";
        result += char;
      } else {
        result += " ";
      }
      continue;
    }
    if (state === "BLOCK_COMMENT") {
      if (char === "*" && next === "/") {
        result += "  ";
        index += 1;
        state = "CODE";
      } else {
        result += char === "\n" ? "\n" : " ";
      }
      continue;
    }
    if (state === "STRING" || state === "CHAR") {
      result += char;
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if ((state === "STRING" && char === '"') || (state === "CHAR" && char === "'")) state = "CODE";
      continue;
    }
    if (char === "/" && next === "/") {
      result += "  ";
      index += 1;
      state = "LINE_COMMENT";
    } else if (char === "/" && next === "*") {
      result += "  ";
      index += 1;
      state = "BLOCK_COMMENT";
    } else {
      result += char;
      if (char === '"') state = "STRING";
      else if (char === "'") state = "CHAR";
    }
  }
  return result;
}

function javaValidationSourceBeforeRuntimeCall(source: string): string {
  const runtimeCall = /\.\s*startAndSubmit\s*\(/.exec(source);
  const prefix = runtimeCall ? source.slice(0, runtimeCall.index) : source;
  const blocks = javaMethodBlocks(source);
  const included = new Set<string>();
  let context = prefix;
  let changed = true;
  while (changed) {
    changed = false;
    for (const [name, block] of blocks) {
      if (included.has(name) || !new RegExp(`\\b${escapeRegExp(name)}\\s*\\(`).test(context)) continue;
      if (/\.\s*startAndSubmit\s*\(/.test(block)) continue;
      included.add(name);
      context += `\n${block}`;
      changed = true;
    }
  }
  return context;
}

function javaMethodBlocks(source: string): Map<string, string> {
  const blocks = new Map<string, string>();
  const declaration = /\b(?:public|protected|private)\s+(?:static\s+)?[A-Za-z_$][\w$<>,.?\[\] \t]*\s+([A-Za-z_$][\w$]*)\s*\([^;{}]*\)\s*(?:throws\s+[^{]+)?\{/g;
  for (const match of source.matchAll(declaration)) {
    const openingBrace = (match.index || 0) + match[0].lastIndexOf("{");
    const closingBrace = matchingBrace(source, openingBrace);
    if (closingBrace > openingBrace) blocks.set(match[1], source.slice(match.index, closingBrace + 1));
  }
  return blocks;
}

function matchingBrace(source: string, openingBrace: number): number {
  let depth = 0;
  let quote = "";
  let escaped = false;
  for (let index = openingBrace; index < source.length; index += 1) {
    const char = source[index];
    if (quote) {
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === quote) quote = "";
      continue;
    }
    if (char === '"' || char === "'") {
      quote = char;
      continue;
    }
    if (char === "{") depth += 1;
    if (char === "}" && --depth === 0) return index;
  }
  return -1;
}

function attachmentCollectionNames(source: string, attachmentCode: string): string[] {
  const names = new Set<string>();
  const escapedCode = escapeRegExp(attachmentCode);
  for (const match of source.matchAll(/\b(?:java\.util\.)?(?:List|Collection)\s*<\s*MultipartFile\s*>\s+([A-Za-z_$][\w$]*)/g)) {
    names.add(match[1]);
  }
  for (const match of source.matchAll(new RegExp(`\\b([A-Za-z_$][\\w$]*)\\s*=\\s*[A-Za-z_$][\\w$]*\\.get\\s*\\(\\s*"${escapedCode}"\\s*\\)`, "g"))) {
    names.add(match[1]);
  }
  const related = [...names].filter((name) => name.toLowerCase().includes(attachmentCode.toLowerCase()));
  const roots = related.length ? related : [...names];
  return propagateJavaAliases(source, roots, names, "(?:java\\.util\\.)?(?:List|Collection)\\s*<\\s*MultipartFile\\s*>");
}

function attachmentFileNames(source: string, collectionNames: string[]): string[] {
  const allFiles = new Set<string>();
  const roots = new Set<string>();
  for (const match of source.matchAll(/\bMultipartFile\s+([A-Za-z_$][\w$]*)/g)) allFiles.add(match[1]);
  for (const collectionName of collectionNames) {
    const loop = new RegExp(
      `\\bMultipartFile\\s+([A-Za-z_$][\\w$]*)\\s*:\\s*${escapeRegExp(collectionName)}\\b`,
      "g",
    );
    for (const match of source.matchAll(loop)) roots.add(match[1]);
  }
  return propagateJavaAliases(source, [...roots], allFiles, "MultipartFile");
}

function propagateJavaAliases(
  source: string,
  roots: string[],
  candidates: Set<string>,
  typePattern: string,
): string[] {
  const edges = new Map<string, Set<string>>();
  const connect = (left: string, right: string): void => {
    if (!candidates.has(left) || !candidates.has(right)) return;
    if (!edges.has(left)) edges.set(left, new Set());
    if (!edges.has(right)) edges.set(right, new Set());
    edges.get(left)!.add(right);
    edges.get(right)!.add(left);
  };
  const assignment = new RegExp(`\\b${typePattern}\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*([A-Za-z_$][\\w$]*)\\b`, "g");
  for (const match of source.matchAll(assignment)) connect(match[1], match[2]);

  const declaration = /\b(?:public|protected|private)\s+(?:static\s+)?[A-Za-z_$][\w$<>,.?\[\]]*\s+([A-Za-z_$][\w$]*)\s*\(([^;{}]*)\)\s*(?:throws\s+[^\{]+)?\{/g;
  for (const method of source.matchAll(declaration)) {
    const parameters = splitJavaArguments(method[2]);
    const calls = new RegExp(`\\b${escapeRegExp(method[1])}\\s*\\(([^;{}()]*)\\)`, "g");
    for (const call of source.matchAll(calls)) {
      const argumentsList = splitJavaArguments(call[1]);
      parameters.forEach((parameter, index) => {
        if (!new RegExp(typePattern).test(parameter)) return;
        const parameterName = parameter.match(/([A-Za-z_$][\w$]*)\s*$/)?.[1];
        const argumentName = argumentsList[index]?.trim().match(/^([A-Za-z_$][\w$]*)$/)?.[1];
        if (parameterName && argumentName) connect(parameterName, argumentName);
      });
    }
  }

  const discovered = new Set(roots);
  const queue = [...roots];
  while (queue.length) {
    const current = queue.shift()!;
    for (const alias of edges.get(current) || []) {
      if (discovered.has(alias)) continue;
      discovered.add(alias);
      queue.push(alias);
    }
  }
  return [...discovered];
}

function splitJavaArguments(value: string): string[] {
  return value.split(",").map((item) => item.trim());
}

function attachmentCountSubjects(source: string, collectionNames: string[]): string[] {
  const subjects = collectionNames.map((name) => `${escapeRegExp(name)}\\s*\\.\\s*size\\s*\\(\\s*\\)`);
  for (const name of collectionNames) {
    const escaped = escapeRegExp(name);
    const declarations = new RegExp(
      `\\b(?:int|long|Integer|Long)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*(?:${escaped}\\s*==\\s*null\\s*\\?\\s*0\\s*:\\s*)?${escaped}\\s*\\.\\s*size\\s*\\(\\s*\\)`,
      "g",
    );
    for (const match of source.matchAll(declarations)) subjects.push(`\\b${escapeRegExp(match[1])}\\b`);
  }
  return subjects;
}

function javaNumericConstants(source: string): Map<string, number> {
  const constants = new Map<string, number>();
  const declaration = /\b(?:static\s+final|final\s+static)\s+(?:int|long|Integer|Long)\s+([A-Za-z_$][\w$]*)\s*=\s*(\d+)L?\s*;/g;
  for (const match of source.matchAll(declaration)) constants.set(match[1], Number(match[2]));
  return constants;
}

function hasBoundComparison(
  source: string,
  subjects: string[],
  kind: "minimum" | "maximum",
  boundary: number,
  constants: Map<string, number>,
): boolean {
  return hasNumericComparison(source, subjects, kind, boundary, constants);
}

function hasNumericComparison(
  source: string,
  subjects: string[],
  kind: "minimum" | "maximum",
  boundary: number,
  constants: Map<string, number>,
): boolean {
  for (const subject of subjects) {
    const direct = new RegExp(`(?:${subject})\\s*(<=|>=|<|>|==)\\s*([A-Za-z_$][\\w$]*|\\d+)L?`, "g");
    for (const match of source.matchAll(direct)) {
      const value = /^\d+$/.test(match[2]) ? Number(match[2]) : constants.get(match[2]);
      if (value !== undefined && comparisonRejectsBoundary(match[1], value, kind, boundary)) return true;
    }
    const reversed = new RegExp(`([A-Za-z_$][\\w$]*|\\d+)L?\\s*(<=|>=|<|>|==)\\s*(?:${subject})`, "g");
    for (const match of source.matchAll(reversed)) {
      const value = /^\d+$/.test(match[1]) ? Number(match[1]) : constants.get(match[1]);
      if (value !== undefined && comparisonRejectsBoundary(reverseOperator(match[2]), value, kind, boundary)) return true;
    }
  }
  return false;
}

function comparisonRejectsBoundary(
  operator: string,
  value: number,
  kind: "minimum" | "maximum",
  boundary: number,
): boolean {
  if (kind === "minimum") {
    return (operator === "<" && value === boundary)
      || (operator === "<=" && value === boundary - 1)
      || (operator === "==" && boundary === 1 && value === 0);
  }
  return (operator === ">" && value === boundary)
    || (operator === ">=" && value === boundary + 1);
}

function reverseOperator(operator: string): string {
  return ({ "<": ">", "<=": ">=", ">": "<", ">=": "<=", "==": "==" } as Record<string, string>)[operator];
}

function hasDirectExtensionCheck(source: string, extension: string, fileNames: string[]): boolean {
  const derivedNames = attachmentExtensionValueNames(source, fileNames);
  if (!derivedNames.length) return false;
  const escaped = escapeRegExp(extension);
  return derivedNames.some((name) => {
    const subject = escapeRegExp(name);
    return new RegExp(`\\b${subject}\\s*\\.\\s*endsWith\\s*\\(\\s*"\\.${escaped}"\\s*\\)`, "i").test(source)
      || new RegExp(`"${escaped}"\\s*\\.\\s*equals\\s*\\(\\s*${subject}\\s*\\)`, "i").test(source)
      || new RegExp(`\\b${subject}\\s*\\.\\s*equals\\s*\\(\\s*"${escaped}"\\s*\\)`, "i").test(source);
  });
}

function hasAllowedExtensionCollection(source: string, extensions: string[], fileNames: string[]): boolean {
  const derivedNames = attachmentExtensionValueNames(source, fileNames);
  if (!derivedNames.some((name) => new RegExp(`\\.\\s*contains\\s*\\(\\s*${escapeRegExp(name)}\\s*\\)`).test(source))) return false;
  return extensions.every((extension) => new RegExp(`"${escapeRegExp(extension)}"`, "i").test(source));
}

function attachmentExtensionValueNames(source: string, fileNames: string[]): string[] {
  const names = new Set<string>();
  for (const fileName of fileNames) {
    const declaration = new RegExp(
      `\\bString\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*[^;]*\\b${escapeRegExp(fileName)}\\s*\\.\\s*getOriginalFilename\\s*\\(\\s*\\)[^;]*;`,
      "g",
    );
    for (const match of source.matchAll(declaration)) names.add(match[1]);
  }
  return [...names];
}

function normalizeJavaNumbers(source: string): string {
  return source.replace(/(?<=\d)_(?=\d)/g, "");
}

function upperFirst(value: string): string {
  return value ? value[0].toUpperCase() + value.slice(1) : value;
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
