import { Injectable } from "@nestjs/common";
import { parse as parseVueSfc, type SFCScriptBlock } from "@vue/compiler-sfc";
import { lexAndParse } from "java-parser";
import ts from "typescript";
import type {
  ArtifactManifest,
  BusinessRequirement,
  GenerationTargetContract,
  QualityDiagnostic,
  QualityStageResult,
} from "@flowmind/agent-contracts";
import type { GenerationSpec } from "../generation/generation-spec.js";
import { qualityDiagnostic } from "../verification/quality-diagnostic.js";

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
    this.validateFileContents(input, diagnostics);

    const completedAt = new Date().toISOString();
    const blockingDiagnostics = diagnostics.filter(({ severity, hardGate }) => severity === "ERROR" && hardGate);
    return {
      stage: "STATIC_VALIDATION",
      status: blockingDiagnostics.length ? "FAILED" : "PASSED",
      hardGate: true,
      summary: blockingDiagnostics.length
        ? `Static validation found ${blockingDiagnostics.length} blocking issue(s).`
        : "Static validation passed.",
      diagnostics,
      startedAt,
      completedAt,
      durationMs: Date.now() - started,
    };
  }

  private validateFileSet(input: StaticValidationInput, diagnostics: QualityDiagnostic[]): void {
    const expected = sortPaths(input.spec.files);
    const manifest = sortPaths(input.manifest.files.map(({ relativePath }) => relativePath));
    const staged = sortPaths([...input.files.keys()]);

    if (sameFiles(expected, manifest) && sameFiles(expected, staged)) return;

    diagnostics.push(diagnostic({
      code: "GENERATED_FILE_SET_MISMATCH",
      message: "Manifest, staged files, and generation spec must contain the same generated files.",
      actual: `Manifest files: ${manifest.join(", ") || "none"}\nStaged files: ${staged.join(", ") || "none"}`,
      expected: `Generation spec files: ${expected.join(", ") || "none"}`,
      evidence: fileSetEvidence(expected, manifest, staged),
      repairHint: "Regenerate or restage the artifact set so manifest, spec, and staged files agree exactly.",
    }));
  }

  private validateFileContents(input: StaticValidationInput, diagnostics: QualityDiagnostic[]): void {
    for (const relativePath of sortPaths(input.spec.files)) {
      if (!input.files.has(relativePath)) continue;
      const content = input.files.get(relativePath)!;

      if (isRequiredSourceFile(relativePath) && content.trim().length === 0) {
        diagnostics.push(diagnostic({
          code: "EMPTY_GENERATED_FILE",
          message: "Required generated source file is empty.",
          relativePath,
          actual: "The staged file contains no non-whitespace content.",
          expected: "Required generated source files must contain parseable source code.",
          repairHint: "Regenerate the missing source content for this file.",
        }));
        continue;
      }

      if (relativePath.endsWith(".java")) {
        this.parseJava(relativePath, content, diagnostics);
      } else if (relativePath.endsWith(".vue")) {
        this.parseVue(relativePath, content, diagnostics);
      } else if (relativePath.endsWith(".ts")) {
        this.parseTypeScript(relativePath, content, diagnostics);
      }
      if (relativePath === input.spec.paths.businessForm && /fetch\(|start-submit|FormData|type=["']submit["']|\/api\/workflow|approve|reject|upload/i.test(content)) {
        diagnostics.push(diagnostic({
          code: "GENERATED_FORM_BOUNDARY_VIOLATION",
          message: "Generated BusinessForm.vue must not contain submit controls or direct workflow submission logic.",
          relativePath,
          actual: "BusinessForm.vue contains workflow mutation, direct fetch, upload, or submit-capable markup.",
          expected: "BusinessForm.vue renders fields and may call only generated typed read-only API helpers.",
          repairHint: "Move submission, attachment upload, and workflow API calls into the shared WorkflowStartShell.",
        }));
      }
      if (relativePath === input.spec.paths.businessForm) {
        const requiredTokens = [
          ...input.requirement.formFields.map(({ fieldCode }) => fieldCode),
          ...(input.requirement.frontendBehavior?.dataQueries.map(({ queryCode }) => queryCode) || []),
          ...(input.requirement.frontendBehavior?.calculations.map(({ targetFieldCode }) => targetFieldCode) || []),
          ...(input.requirement.frontendBehavior?.checks.map(({ checkName }) => checkName) || []),
        ];
        const missingTokens = [...new Set(requiredTokens)].filter((token) => !content.includes(token));
        if (missingTokens.length) {
          diagnostics.push(diagnostic({
            code: "GENERATED_REQUIREMENT_MAPPING_MISSING",
            message: "Generated BusinessForm.vue omits confirmed business fields or page behavior.",
            relativePath,
            actual: `Missing confirmed tokens: ${missingTokens.join(", ")}`,
            expected: "Every confirmed field, query, calculation target, and displayed check must be represented.",
            repairHint: "Implement the missing confirmed fields and page behaviors without adding undeclared business values.",
          }));
        }
      }
      if (relativePath === input.spec.paths.applyView && /fetch\(|start-submit|FormData|type=["']submit["']|Idempotency-Key|\/api\//i.test(content)) {
        diagnostics.push(diagnostic({
          code: "GENERATED_APPLY_BOUNDARY_VIOLATION",
          message: "Generated Apply.vue must delegate workflow behavior to WorkflowStartShell.",
          relativePath,
          actual: "Apply.vue contains request or submission implementation.",
          expected: "Apply.vue only composes the standalone page, BusinessForm, and WorkflowStartShell.",
          repairHint: "Remove request, attachment, idempotency, and submission logic from Apply.vue.",
        }));
      }
      if (relativePath === input.spec.paths.api && input.spec.hasBusinessApi
        && (/\/api\/platform\//.test(content) || /start-submit|\/tasks\/|approve|reject|upload/i.test(content)
          || /method\s*:\s*["'](?:POST|PUT|PATCH|DELETE)["']/i.test(content))) {
        diagnostics.push(diagnostic({
          code: "GENERATED_API_MUTATION_FORBIDDEN",
          message: "Generated business API modules are read-only.",
          relativePath,
          actual: "The API module contains a platform/workflow mutation or a non-GET method.",
          expected: "Only declared read-only business reference-data GET requests are allowed.",
          repairHint: "Remove workflow/platform mutations and keep only declared reference-data GET helpers.",
        }));
      }
      if (relativePath === input.spec.paths.api && input.spec.hasBusinessApi) {
        const allowedPrefixes = declaredApiPrefixes(input.requirement);
        const endpoints = [...content.matchAll(/["'`](\/api\/[^"'`]*)["'`]/g)].map((match) => match[1]);
        const undeclared = endpoints.filter((endpoint) => !allowedPrefixes.some((prefix) => endpoint.startsWith(prefix)));
        if (undeclared.length) {
          diagnostics.push(diagnostic({
            code: "GENERATED_API_ENDPOINT_UNDECLARED",
            message: "Generated business API modules may call only requirement-declared read-only resources.",
            relativePath,
            actual: `Undeclared endpoints: ${[...new Set(undeclared)].join(", ")}`,
            expected: `Allowed endpoint prefixes: ${allowedPrefixes.join(", ") || "none"}`,
            repairHint: "Remove undeclared endpoints or add the required reference-data/query declaration before regenerating.",
          }));
        }
      }
      if (relativePath === input.spec.paths.routeRegistry) {
        if (!content.includes(input.spec.routeName) || !/standalone\s*:\s*true/.test(content) || /public\s*:\s*true/.test(content)) {
          diagnostics.push(diagnostic({
            code: "GENERATED_ROUTE_BOUNDARY_VIOLATION",
            message: "Generated routes must preserve authenticated standalone layout semantics.",
            relativePath,
            actual: "The generated route is missing, not standalone, or marked public.",
            expected: `Route ${input.spec.routeName} uses meta.standalone: true and is not public.`,
            repairHint: "Register the generated route with standalone metadata while preserving existing entries.",
          }));
        }
      }
    }
  }

  private parseJava(relativePath: string, content: string, diagnostics: QualityDiagnostic[]): void {
    try {
      lexAndParse(content);
    } catch (error) {
      const location = javaErrorLocation(error);
      const parserMessage = rawErrorMessage(error);
      diagnostics.push(diagnostic({
        code: "JAVA_SYNTAX_ERROR",
        message: parserMessage,
        relativePath,
        line: location.line,
        column: location.column,
        actual: parserMessage,
        expected: "Java parser must parse the generated source successfully.",
        evidence: parserMessage,
        repairHint: "Fix the Java syntax error at the reported location.",
      }));
    }
  }

  private parseTypeScript(
    relativePath: string,
    content: string,
    diagnostics: QualityDiagnostic[],
    lineOffset = 0,
    firstLineColumnOffset = 0,
  ): void {
    const source = ts.createSourceFile(relativePath, content, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
    const parseDiagnostics = (source as ts.SourceFile & { parseDiagnostics?: readonly ts.Diagnostic[] }).parseDiagnostics || [];
    for (const item of parseDiagnostics) {
      const position = item.start === undefined ? undefined : source.getLineAndCharacterOfPosition(item.start);
      const parserMessage = ts.flattenDiagnosticMessageText(item.messageText, "\n");
      const line = position === undefined ? undefined : position.line + 1 + lineOffset;
      const column = position === undefined
        ? undefined
        : position.character + 1 + (position.line === 0 ? firstLineColumnOffset : 0);
      diagnostics.push(diagnostic({
        code: "TYPESCRIPT_SYNTAX_ERROR",
        message: parserMessage,
        relativePath,
        line,
        column,
        actual: parserMessage,
        expected: "TypeScript parser must parse the generated source successfully.",
        evidence: parserMessage,
        repairHint: "Fix the TypeScript syntax error at the reported location.",
      }));
    }
  }

  private parseVue(relativePath: string, content: string, diagnostics: QualityDiagnostic[]): void {
    try {
      const parsed = parseVueSfc(content, { filename: relativePath });
      for (const error of parsed.errors) {
        const parserMessage = rawErrorMessage(error);
        const location = vueErrorLocation(error);
        diagnostics.push(diagnostic({
          code: "VUE_SYNTAX_ERROR",
          message: parserMessage,
          relativePath,
          line: location.line,
          column: location.column,
          actual: parserMessage,
          expected: "Vue SFC parser must parse the generated component successfully.",
          evidence: parserMessage,
          repairHint: "Fix the Vue SFC syntax error at the reported location.",
        }));
      }

      for (const script of vueScriptBlocks(parsed.descriptor.script, parsed.descriptor.scriptSetup)) {
        const startLine = script.loc.start.line;
        const startColumn = script.loc.start.column;
        this.parseTypeScript(relativePath, script.content, diagnostics, startLine - 1, startColumn - 1);
      }
    } catch (error) {
      const parserMessage = rawErrorMessage(error);
      const location = vueErrorLocation(error);
      diagnostics.push(diagnostic({
        code: "VUE_SYNTAX_ERROR",
        message: parserMessage,
        relativePath,
        line: location.line,
        column: location.column,
        actual: parserMessage,
        expected: "Vue SFC parser must parse the generated component successfully.",
        evidence: parserMessage,
        repairHint: "Fix the Vue SFC syntax error at the reported location.",
      }));
    }
  }
}

function declaredApiPrefixes(requirement: BusinessRequirement): string[] {
  const resources = new Set([
    ...requirement.formFields.flatMap(({ referenceDataSource }) => referenceDataSource ? [referenceDataSource.resource] : []),
    ...(requirement.frontendBehavior?.dataQueries.map(({ resource }) => resource) || []),
  ]);
  const prefixes = new Set<string>();
  if (resources.has("FUTURES_ACCOUNTS")) prefixes.add("/api/reference-data/futures-accounts");
  if (resources.has("EXCHANGES")) prefixes.add("/api/reference-data/exchanges");
  if (resources.has("TRADING_CODES") || resources.has("ACCOUNT_FUNDS")) prefixes.add("/api/reference-data/futures-accounts/");
  if (resources.has("FUTURES_PRODUCTS")) prefixes.add("/api/reference-data/futures-products");
  return [...prefixes];
}

function diagnostic(details: {
  code: string;
  message: string;
  relativePath?: string;
  line?: number;
  column?: number;
  actual?: string;
  expected?: string;
  evidence?: string;
  repairHint?: string;
}): QualityDiagnostic {
  return qualityDiagnostic("STATIC_VALIDATION", {
    code: details.code,
    message: details.message,
    severity: "ERROR",
    hardGate: true,
    relativePath: details.relativePath,
    line: details.line,
    column: details.column,
    actual: details.actual || details.message,
    expected: details.expected || "Generated artifacts must satisfy deterministic static validation.",
    evidence: details.evidence || details.message,
    repairHint: details.repairHint || "Inspect the referenced generated artifact and fix the deterministic static validation error.",
    repairability: "CODE_ACTIONABLE",
  });
}

function sortPaths(paths: string[]): string[] {
  return [...paths].map((path) => path.replace(/\\/g, "/")).sort();
}

function sameFiles(left: string[], right: string[]): boolean {
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

function isRequiredSourceFile(relativePath: string): boolean {
  return relativePath.endsWith(".java") || relativePath.endsWith(".ts") || relativePath.endsWith(".vue");
}

function fileSetEvidence(expected: string[], manifest: string[], staged: string[]): string {
  return [
    listDelta("Missing from manifest", expected.filter((path) => !manifest.includes(path))),
    listDelta("Unexpected in manifest", manifest.filter((path) => !expected.includes(path))),
    listDelta("Missing from staging", expected.filter((path) => !staged.includes(path))),
    listDelta("Unexpected in staging", staged.filter((path) => !expected.includes(path))),
  ].filter(Boolean).join("\n") || "File sets differ.";
}

function listDelta(label: string, paths: string[]): string {
  return paths.length ? `${label}: ${paths.join(", ")}` : "";
}

function vueScriptBlocks(
  script: SFCScriptBlock | null,
  scriptSetup: SFCScriptBlock | null,
): SFCScriptBlock[] {
  return [script, scriptSetup].filter((block): block is SFCScriptBlock => block !== null);
}

function rawErrorMessage(error: unknown): string {
  if (typeof error === "string") return error;
  if (error instanceof Error) return error.message;
  return String(error);
}

function javaErrorLocation(error: unknown): { line?: number; column?: number } {
  const candidate = error as {
    token?: { startLine?: number; startColumn?: number };
    previousToken?: { endLine?: number; endColumn?: number; startLine?: number; startColumn?: number };
  };
  const line = candidate.token?.startLine
    ?? candidate.previousToken?.endLine
    ?? candidate.previousToken?.startLine
    ?? lineFromMessage(error);
  const column = candidate.token?.startColumn
    ?? candidate.previousToken?.endColumn
    ?? candidate.previousToken?.startColumn
    ?? columnFromMessage(error);
  return { line, column };
}

function vueErrorLocation(error: unknown): { line?: number; column?: number } {
  const candidate = error as { loc?: { start?: { line?: number; column?: number } } };
  return {
    line: candidate.loc?.start?.line ?? lineFromMessage(error),
    column: candidate.loc?.start?.column ?? columnFromMessage(error),
  };
}

function lineFromMessage(error: unknown): number | undefined {
  const match = /line:\s*(\d+)/i.exec(rawErrorMessage(error));
  return match ? Number(match[1]) : undefined;
}

function columnFromMessage(error: unknown): number | undefined {
  const match = /column:\s*(\d+)/i.exec(rawErrorMessage(error));
  return match ? Number(match[1]) : undefined;
}
