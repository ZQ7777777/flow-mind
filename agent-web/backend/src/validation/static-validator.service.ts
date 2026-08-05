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
      if (!service.includes(`variables.put("${field.fieldCode}"`)) {
        diagnostics.push(diagnostic(
          "FORM_FIELD_MAPPING_MISSING",
          `Service does not map confirmed form field ${field.fieldCode}.`,
          input.spec.paths.service,
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
      if (!mapsAttachmentCode(service, attachment.attachmentCode)
        || !view.includes(`form.${attachment.attachmentCode}`)
        || !api.includes(attachment.attachmentCode)) {
        diagnostics.push(diagnostic(
          "ATTACHMENT_MAPPING_MISSING",
          `Generated code does not consistently map attachment ${attachment.attachmentCode}.`,
        ));
      }
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

function diagnostic(
  code: string,
  message: string,
  relativePath?: string,
  line?: number,
  column?: number,
): QualityDiagnostic {
  return { code, message, severity: "ERROR", hardGate: true, relativePath, line, column };
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

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
