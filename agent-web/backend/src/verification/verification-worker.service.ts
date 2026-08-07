import { HttpStatus, Injectable } from "@nestjs/common";
import { randomUUID } from "node:crypto";
import {
  existsSync,
  lstatSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { dirname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { spawn } from "node:child_process";
import type {
  ArtifactManifest,
  GenerationTargetContract,
  QualityDiagnostic,
  QualityStageName,
  QualityStageResult,
} from "@flowmind/agent-contracts";
import { AgentError } from "../common/agent-error.js";
import { qualityDiagnostic, sanitizeDiagnosticEvidence } from "./quality-diagnostic.js";

const DEFAULT_TIMEOUT_MS = 5 * 60 * 1000;
const DEFAULT_MAX_OUTPUT_BYTES = 10 * 1024 * 1024;
const HARD_STAGES = new Set<QualityStageName>([
  "BACKEND_COMPILE",
  "FRONTEND_TYPECHECK",
  "FRONTEND_BUILD",
]);
const activeGenerations = new Set<string>();

export interface VerificationCommand {
  stage: Exclude<QualityStageName, "STATIC_VALIDATION">;
  executable: string;
  args: string[];
  cwd: string;
  workspaceRoot: string;
  shell: false;
  timeoutMs: number;
  maxOutputBytes: number;
  env: NodeJS.ProcessEnv;
  signal?: AbortSignal;
}

export interface VerificationCommandResult {
  exitCode: number | null;
  stdout: string;
  stderr: string;
  timedOut: boolean;
  cancelled: boolean;
  infrastructureError?: string;
}

export type VerificationCommandExecutor =
  (command: VerificationCommand) => Promise<VerificationCommandResult>;

export interface VerificationWorkerInput {
  generationId: string;
  revision: number;
  targetRoot: string;
  stagingDir: string;
  contract: GenerationTargetContract;
  manifest: ArtifactManifest;
  dataDir: string;
  timeoutMs?: number;
  maxOutputBytes?: number;
  signal?: AbortSignal;
  execute?: VerificationCommandExecutor;
}

export interface VerificationWorkerResult {
  runId: string;
  workspaceRoot: string;
  logDir: string;
  stages: QualityStageResult[];
  infrastructureFailed: boolean;
}

@Injectable()
export class VerificationWorkerService {
  async run(input: VerificationWorkerInput): Promise<VerificationWorkerResult> {
    if (activeGenerations.has(input.generationId)) {
      throw new AgentError(
        HttpStatus.CONFLICT,
        "AGENT_VERIFICATION_ALREADY_RUNNING",
        "A verification run is already active for this generation.",
      );
    }
    activeGenerations.add(input.generationId);
    const runId = `verify_${randomUUID()}`;
    const workspaceRoot = join(input.dataDir, "verification-workspaces", input.generationId, runId);
    const logDir = join(input.dataDir, "verification-logs", input.generationId, runId);
    mkdirSync(logDir, { recursive: true });

    try {
      copyDirectory(input.targetRoot, workspaceRoot);
      overlayManifest(input, workspaceRoot);
      const commands = fixedCommands(input, workspaceRoot);
      const stages: QualityStageResult[] = [];
      const execute = input.execute || executeVerificationCommand;
      await installFrontendDependencies(input, workspaceRoot, execute, runId, logDir);
      for (const command of commands) {
        const blockers = commandBlockers(command.stage, stages);
        if (blockers.length) {
          stages.push(blockedStageResult(command, blockers));
          continue;
        }
        if (input.signal?.aborted) {
          stages.push(stageResult(command, {
            exitCode: null,
            stdout: "",
            stderr: "",
            timedOut: false,
            cancelled: true,
          }, undefined, false, input.manifest));
          break;
        }
        const result = await execute(command);
        const combined = `[stdout]\n${result.stdout}\n[stderr]\n${result.stderr}`;
        const truncated = truncateUtf8(combined, command.maxOutputBytes);
        const logPath = join(logDir, `${command.stage.toLowerCase()}.log`);
        writeFileSync(logPath, truncated.value, "utf8");
        stages.push(stageResult(command, result, logPath, truncated.truncated, input.manifest));
        if (result.cancelled) break;
      }
      linkDerivedFrontendDiagnostics(stages);
      return {
        runId,
        workspaceRoot,
        logDir,
        stages,
        infrastructureFailed: stages.some(({ status }) => status === "INFRASTRUCTURE_FAILED" || status === "CANCELLED"),
      };
    } finally {
      rmSync(workspaceRoot, { recursive: true, force: true });
      activeGenerations.delete(input.generationId);
    }
  }
}

export async function executeVerificationCommand(
  command: VerificationCommand,
): Promise<VerificationCommandResult> {
  return new Promise((resolveResult) => {
    let stdout = "";
    let stderr = "";
    let truncated = false;
    let timedOut = false;
    let cancelled = false;
    let settled = false;
    let child;
    try {
      child = spawn(command.executable, command.args, {
        cwd: command.cwd,
        env: command.env,
        shell: false,
        windowsHide: true,
        stdio: ["ignore", "pipe", "pipe"],
      });
    } catch (error) {
      resolveResult({
        exitCode: null,
        stdout,
        stderr,
        timedOut: false,
        cancelled: false,
        infrastructureError: error instanceof Error ? error.message : String(error),
      });
      return;
    }

    const collect = (target: "stdout" | "stderr", chunk: Buffer): void => {
      if (target === "stdout") stdout += chunk.toString("utf8");
      else stderr += chunk.toString("utf8");
      const totalBytes = Buffer.byteLength(stdout, "utf8") + Buffer.byteLength(stderr, "utf8");
      if (totalBytes <= command.maxOutputBytes) return;
      truncated = true;
      if (stdout && stderr) {
        const stdoutLimit = Math.floor(command.maxOutputBytes / 2);
        stdout = truncateUtf8Tail(stdout, stdoutLimit);
        stderr = truncateUtf8Tail(stderr, command.maxOutputBytes - stdoutLimit);
      } else if (stdout) stdout = truncateUtf8Tail(stdout, command.maxOutputBytes);
      else stderr = truncateUtf8Tail(stderr, command.maxOutputBytes);
    };
    child.stdout.on("data", (chunk: Buffer) => collect("stdout", chunk));
    child.stderr.on("data", (chunk: Buffer) => collect("stderr", chunk));

    const finish = (result: VerificationCommandResult): void => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      command.signal?.removeEventListener("abort", cancel);
      if (truncated) stderr += "\n[output truncated]";
      resolveResult(result);
    };
    const terminate = (): void => {
      if (child.pid && process.platform === "win32") {
        const killer = spawn("taskkill", ["/pid", String(child.pid), "/T", "/F"], {
          shell: false,
          windowsHide: true,
          stdio: "ignore",
        });
        killer.unref();
      } else {
        child.kill("SIGKILL");
      }
    };
    const cancel = (): void => {
      cancelled = true;
      terminate();
    };
    const timer = setTimeout(() => {
      timedOut = true;
      terminate();
    }, command.timeoutMs);
    command.signal?.addEventListener("abort", cancel, { once: true });
    child.on("error", (error) => finish({
      exitCode: null,
      stdout,
      stderr,
      timedOut,
      cancelled,
      infrastructureError: error.message,
    }));
    child.on("close", (code) => finish({ exitCode: code, stdout, stderr, timedOut, cancelled }));
  });
}

function fixedCommands(input: VerificationWorkerInput, workspaceRoot: string): VerificationCommand[] {
  const timeoutMs = input.timeoutMs || DEFAULT_TIMEOUT_MS;
  const maxOutputBytes = input.maxOutputBytes || DEFAULT_MAX_OUTPUT_BYTES;
  const common = {
    workspaceRoot,
    shell: false as const,
    timeoutMs,
    maxOutputBytes,
    env: {
      ...fixedEnvironment(),
      // The desktop process may not have access to the interactive user's npm
      // cache. Keep the verification cache under the agent data directory.
      NPM_CONFIG_CACHE: join(input.dataDir, "npm-cache"),
    },
    signal: input.signal,
  };
  const command = (executable: string, args: string[]): Pick<VerificationCommand, "executable" | "args"> => {
    if (process.platform !== "win32") return { executable, args };
    // Node cannot directly spawn .cmd files with shell disabled on Windows. The
    // commands below are fixed by this service, so use cmd.exe as the process
    // while preserving a shell-free child_process invocation.
    return {
      executable: process.env.COMSPEC || "cmd.exe",
      args: ["/d", "/s", "/c", `${executable}.cmd ${args.join(" ")}`],
    };
  };
  const backend = resolve(workspaceRoot, input.contract.backend.rootDir);
  const frontend = resolve(workspaceRoot, input.contract.frontend.rootDir);
  return [
    { ...common, stage: "BACKEND_COMPILE", ...command("mvn", ["-q", "-DskipTests", "compile"]), cwd: backend },
    { ...common, stage: "BACKEND_TESTS", ...command("mvn", ["-q", "test"]), cwd: backend },
    { ...common, stage: "FRONTEND_TYPECHECK", ...command("npm", ["run", "typecheck"]), cwd: frontend },
    { ...common, stage: "FRONTEND_TESTS", ...command("npm", ["run", "test", "--", "--run"]), cwd: frontend },
    { ...common, stage: "FRONTEND_BUILD", ...command("npm", ["run", "build"]), cwd: frontend },
  ];
}

function fixedEnvironment(): NodeJS.ProcessEnv {
  const names = [
    "PATH", "Path", "PATHEXT", "SYSTEMROOT", "WINDIR", "COMSPEC",
    "JAVA_HOME", "MAVEN_HOME", "HOME", "USERPROFILE", "HOMEDRIVE", "HOMEPATH", "TEMP", "TMP",
  ];
  const env: NodeJS.ProcessEnv = { CI: "true", NO_COLOR: "1" };
  for (const name of names) if (process.env[name] !== undefined) env[name] = process.env[name];
  return env;
}

function overlayManifest(input: VerificationWorkerInput, workspaceRoot: string): void {
  for (const file of input.manifest.files) {
    const source = resolve(input.stagingDir, file.relativePath);
    const destination = resolve(workspaceRoot, file.relativePath);
    assertInside(input.stagingDir, source);
    assertInside(workspaceRoot, destination);
    if (!existsSync(source) || lstatSync(source).isSymbolicLink() || !lstatSync(source).isFile()) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_STAGED_FILE_CHANGED", `Staged file is unavailable: ${file.relativePath}`);
    }
    mkdirSync(dirname(destination), { recursive: true });
    writeFileSync(destination, readFileSync(source));
  }
}

function copyDirectory(source: string, destination: string): void {
  const stat = lstatSync(source);
  if (stat.isSymbolicLink()) {
    throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_VERIFICATION_LINK_FORBIDDEN", "Verification source contains a symbolic link.");
  }
  if (stat.isDirectory()) {
    mkdirSync(destination, { recursive: true });
    for (const entry of readdirSync(source, { withFileTypes: true })) {
      // Dependencies are restored from the lockfile in the disposable workspace.
      // Copying node_modules can leave a partial dependency tree on Windows.
      if ([".git", "node_modules", "target", "dist", "coverage"].includes(entry.name)) continue;
      copyDirectory(join(source, entry.name), join(destination, entry.name));
    }
    return;
  }
  if (stat.isFile()) {
    mkdirSync(dirname(destination), { recursive: true });
    writeFileSync(destination, readFileSync(source));
  }
}

async function installFrontendDependencies(
  input: VerificationWorkerInput,
  workspaceRoot: string,
  execute: VerificationCommandExecutor,
  runId: string,
  logDir: string,
): Promise<void> {
  const frontend = resolve(workspaceRoot, input.contract.frontend.rootDir);
  if (!existsSync(join(frontend, "package-lock.json"))) return;

  const command = dependencyInstallCommand(frontend, workspaceRoot, input);
  const result = await execute(command);
  writeFileSync(join(logDir, "frontend_dependency_install.log"), `[stdout]\n${result.stdout}\n[stderr]\n${result.stderr}`, "utf8");
  if (result.exitCode !== 0 || result.infrastructureError || result.timedOut || result.cancelled) {
    throw new AgentError(
      HttpStatus.SERVICE_UNAVAILABLE,
      "AGENT_FRONTEND_DEPENDENCY_INSTALL_FAILED",
      `Unable to restore frontend dependencies for ${runId}; see frontend_dependency_install.log.`,
    );
  }
}

function dependencyInstallCommand(
  frontend: string,
  workspaceRoot: string,
  input: VerificationWorkerInput,
): VerificationCommand {
  const command = process.platform === "win32"
    ? {
      executable: process.env.COMSPEC || "cmd.exe",
      args: ["/d", "/s", "/c", "npm.cmd ci --ignore-scripts --no-audit --fund=false"],
    }
    : { executable: "npm", args: ["ci", "--ignore-scripts", "--no-audit", "--fund=false"] };
  return {
    ...command,
    stage: "FRONTEND_TYPECHECK",
    cwd: frontend,
    workspaceRoot,
    shell: false,
    timeoutMs: input.timeoutMs || DEFAULT_TIMEOUT_MS,
    maxOutputBytes: input.maxOutputBytes || DEFAULT_MAX_OUTPUT_BYTES,
    env: {
      ...fixedEnvironment(),
      NPM_CONFIG_CACHE: join(input.dataDir, "npm-cache"),
    },
    signal: input.signal,
  };
}

function stageResult(
  command: VerificationCommand,
  result: VerificationCommandResult,
  logPath?: string,
  outputTruncated = false,
  manifest?: ArtifactManifest,
): QualityStageResult {
  const hardGate = HARD_STAGES.has(command.stage);
  let status: QualityStageResult["status"];
  if (result.cancelled) status = "CANCELLED";
  else if (result.timedOut || result.infrastructureError) status = "INFRASTRUCTURE_FAILED";
  else status = result.exitCode === 0 ? "PASSED" : "FAILED";
  const message = result.infrastructureError
    || (result.timedOut ? "Verification command timed out."
      : result.cancelled ? "Verification command was cancelled."
        : status === "PASSED" ? "Command passed." : `Command exited with code ${result.exitCode}.`);
  const parsedDiagnostics = status === "PASSED" ? [] : parseCommandDiagnostics(command, result, hardGate, manifest);
  const fallbackEvidence = diagnosticExcerpt(`${result.stdout}\n${result.stderr}`);
  return {
    stage: command.stage,
    status,
    hardGate,
    summary: message,
    diagnostics: status === "PASSED" ? [] : parsedDiagnostics.length ? parsedDiagnostics : [qualityDiagnostic(command.stage, {
      code: status === "INFRASTRUCTURE_FAILED" ? "VERIFICATION_INFRASTRUCTURE_FAILED" : "VERIFICATION_COMMAND_FAILED",
      message,
      hardGate,
      evidence: fallbackEvidence || message,
      expected: status === "INFRASTRUCTURE_FAILED" ? "The verification environment must be available." : "The command must exit successfully.",
      repairHint: status === "INFRASTRUCTURE_FAILED"
        ? "Do not change generated code for this failure; retry after the verification environment is restored."
        : "Use the evidence and verification log to locate and correct the failing code or assertion.",
      repairability: status === "INFRASTRUCTURE_FAILED" ? "INFRASTRUCTURE" : "CODE_ACTIONABLE",
    })],
    exitCode: result.exitCode === null ? undefined : result.exitCode,
    logPath,
    outputTruncated,
  };
}

function commandBlockers(stage: QualityStageName, stages: QualityStageResult[]): QualityStageName[] {
  const dependencies: Partial<Record<QualityStageName, QualityStageName[]>> = {
    BACKEND_TESTS: ["BACKEND_COMPILE"],
  };
  return (dependencies[stage] || []).filter((dependency) =>
    stages.some((result) => result.stage === dependency && result.status !== "PASSED"),
  );
}

function blockedStageResult(
  command: VerificationCommand,
  blockedBy: QualityStageName[],
): QualityStageResult {
  const diagnostic = qualityDiagnostic(command.stage, {
    code: "QUALITY_STAGE_BLOCKED",
    message: `${command.stage} could not run because ${blockedBy.join(", ")} did not pass.`,
    hardGate: HARD_STAGES.has(command.stage),
    expected: `${blockedBy.join(", ")} must pass before ${command.stage} can run.`,
    repairHint: "Resolve the blocking stage; this dependent stage will run automatically on the next verification.",
    repairability: "UNKNOWN",
  });
  return {
    stage: command.stage,
    status: "SKIPPED",
    hardGate: HARD_STAGES.has(command.stage),
    summary: `Blocked by ${blockedBy.join(", ")}.`,
    diagnostics: [{ ...diagnostic, classification: "BLOCKED" }],
    blockedBy,
  };
}

function linkDerivedFrontendDiagnostics(stages: QualityStageResult[]): void {
  const typecheck = stages.find(({ stage }) => stage === "FRONTEND_TYPECHECK");
  const build = stages.find(({ stage }) => stage === "FRONTEND_BUILD");
  if (!typecheck || !build) return;
  for (const buildDiagnostic of build.diagnostics) {
    const primary = typecheck.diagnostics.find((candidate) =>
      candidate.code === buildDiagnostic.code
      && candidate.relativePath === buildDiagnostic.relativePath
      && candidate.line === buildDiagnostic.line
      && candidate.column === buildDiagnostic.column
      && candidate.message === buildDiagnostic.message,
    );
    if (primary?.fingerprint) buildDiagnostic.derivedFrom = [primary.fingerprint];
  }
}

function parseCommandDiagnostics(
  command: VerificationCommand,
  result: VerificationCommandResult,
  hardGate: boolean,
  manifest?: ArtifactManifest,
): QualityDiagnostic[] {
  const diagnostics: QualityDiagnostic[] = [];
  const output = `${result.stdout}\n${result.stderr}`;
  const lines = output.split(/\r?\n/);
  for (let index = 0; index < lines.length; index += 1) {
    const rawLine = lines[index];
    const line = rawLine.trim();
    let match = line.match(/^(.+?)\((\d+),(\d+)\):\s*(?:error|warning)\s+(TS\d+):\s*(.+)$/i);
    if (!match) match = line.match(/^(.+?):(\d+):(\d+)\s+-\s+(?:error|warning)\s+(TS\d+):\s*(.+)$/i);
    if (match) {
      diagnostics.push(qualityDiagnostic(command.stage, {
        code: match[4],
        message: match[5].trim(),
        hardGate,
        relativePath: diagnosticPath(command, match[1], manifest),
        line: Number(match[2]),
        column: Number(match[3]),
        evidence: line,
        expected: "TypeScript and Vue sources must pass type checking without errors.",
        repairHint: "Correct the reported type mismatch at the referenced source location.",
      }));
      continue;
    }
    match = line.match(/^(?:\[ERROR\]\s*)?(.+?\.java):\[(\d+),(\d+)\]\s*(.+)$/);
    if (!match) match = line.match(/^(?:\[ERROR\]\s*)?(.+?\.java):(\d+):(?:(\d+):)?\s*(?:error:\s*)?(.+)$/i);
    if (match) {
      diagnostics.push(qualityDiagnostic(command.stage, {
        code: "JAVA_COMPILER_ERROR",
        message: match[4].trim(),
        hardGate,
        relativePath: diagnosticPath(command, match[1], manifest),
        line: Number(match[2]),
        column: match[3] ? Number(match[3]) : undefined,
        evidence: diagnosticBlock(lines, index),
        expected: "Generated Java sources must compile against the authoritative platform API.",
        repairHint: "Correct the compiler error and consult the authoritative references before editing platform calls.",
      }));
      continue;
    }
    match = line.match(/^[>\s❯]*([^\s]+\.(?:spec|test)\.[jt]sx?):(\d+):(\d+)/);
    if (match) {
      diagnostics.push({
        code: "TEST_FAILURE",
        message: line,
        severity: "ERROR",
        hardGate,
        relativePath: diagnosticPath(command, match[1], manifest),
        line: Number(match[2]),
        column: Number(match[3]),
      });
    }
  }
  const structuredTests = parseStructuredTestDiagnostics(command, output, hardGate, manifest);
  const sourceDiagnostics = structuredTests.length
    ? [...diagnostics.filter(({ code }) => code !== "TEST_FAILURE"), ...structuredTests]
    : diagnostics;
  const enriched = sourceDiagnostics.map((item) => item.diagnosticId ? item : qualityDiagnostic(command.stage, {
    ...item,
    evidence: item.message,
    expected: item.code === "TEST_FAILURE" ? "The named test must pass." : undefined,
    repairHint: item.code === "TEST_FAILURE" ? "Correct the implementation without weakening or skipping the test." : undefined,
  }));
  return [...new Map(enriched.map((item) => [item.diagnosticId, item])).values()];
}

function parseStructuredTestDiagnostics(
  command: VerificationCommand,
  output: string,
  hardGate: boolean,
  manifest?: ArtifactManifest,
): QualityDiagnostic[] {
  if (command.stage === "FRONTEND_TESTS") return parseVitestDiagnostics(command, output, hardGate, manifest);
  if (command.stage === "BACKEND_TESTS") return parseJUnitDiagnostics(command, output, hardGate, manifest);
  return [];
}

function parseVitestDiagnostics(
  command: VerificationCommand,
  output: string,
  hardGate: boolean,
  manifest?: ArtifactManifest,
): QualityDiagnostic[] {
  const failures = [...output.matchAll(/^\s*FAIL\s+(\S+\.(?:spec|test)\.[jt]sx?)\s+>\s+([^\r\n]+)$/gm)];
  return failures.flatMap((failure, index) => {
    const blockEnd = failures[index + 1]?.index ?? output.length;
    const block = output.slice(failure.index, blockEnd);
    const location = block.match(/[>❯\s]*([^\s]+\.(?:spec|test)\.[jt]sx?):(\d+):(\d+)/);
    if (!location) return [];
    const evidence = sanitizeDiagnosticEvidence(block.slice(0, 4_000));
    const assertion = block.match(/AssertionError[^\r\n]*/i)?.[0];
    return [qualityDiagnostic(command.stage, {
      code: "VITEST_TEST_FAILURE",
      message: assertion || `Vitest test ${failure[2]} failed.`,
      hardGate,
      relativePath: diagnosticPath(command, location[1], manifest),
      line: Number(location[2]),
      column: Number(location[3]),
      evidence,
      actual: assertionValue(evidence, "actual") || assertionValue(evidence, "received"),
      expected: assertionValue(evidence, "expected") || "The Vitest assertion must pass.",
      repairHint: "Use the assertion difference to correct the implementation without weakening or skipping the test.",
    })];
  });
}

function parseJUnitDiagnostics(
  command: VerificationCommand,
  output: string,
  hardGate: boolean,
  manifest?: ArtifactManifest,
): QualityDiagnostic[] {
  const summary = [...output.matchAll(/^\[ERROR\]\s+([\w$]+)\.([\w$]+):(\d+)(?:->[^\s]+)*\s+(.+)$/gm)];
  if (summary.length) {
    return summary.map((match) => junitDiagnostic(
      command, hardGate, manifest, match[1], match[2], `${match[1]}.java`, Number(match[3]), match[4],
    ));
  }
  const frames = [...output.matchAll(/(?:at\s+)([\w.$]+)\.([\w$]+)\(([^()]+\.java):(\d+)\)/g)];
  return frames.flatMap((match) => {
    const relativePath = junitPath(command, match[1], match[3], manifest);
    if (!relativePath) return [];
    const before = output.slice(Math.max(0, (match.index || 0) - 1_000), match.index);
    const message = before.match(/(?:AssertionFailedError|AssertionError|MockitoException|UnnecessaryStubbingException)[^\r\n]*/gi)?.at(-1)
      || `JUnit test ${match[2]} failed.`;
    return [junitDiagnostic(
      command, hardGate, manifest, match[1], match[2], match[3], Number(match[4]), message,
    )];
  });
}

function junitDiagnostic(
  command: VerificationCommand,
  hardGate: boolean,
  manifest: ArtifactManifest | undefined,
  className: string,
  testName: string,
  fileName: string,
  line: number,
  message: string,
): QualityDiagnostic {
  const evidence = sanitizeDiagnosticEvidence(`${className}.${testName}:${line} ${message}`);
  const unnecessaryStubbing = /UnnecessaryStubbingException/i.test(message);
  return qualityDiagnostic(command.stage, {
    code: unnecessaryStubbing ? "JUNIT_UNNECESSARY_STUBBING" : "JUNIT_TEST_FAILURE",
    message,
    hardGate,
    relativePath: junitPath(command, className, fileName, manifest),
    line,
    evidence,
    actual: assertionValue(evidence, "actual") || assertionValue(evidence, "but was"),
    expected: unnecessaryStubbing
      ? "Every Mockito stubbing must be used by the exercised code path."
      : assertionValue(evidence, "expected") || "The JUnit assertion must pass.",
    repairHint: unnecessaryStubbing
      ? "Remove only the unused stubbing reported for this test; do not make Mockito globally lenient."
      : "Use the assertion difference and generated-code stack frame to correct the implementation without weakening the test.",
  });
}

function diagnosticExcerpt(output: string): string {
  const trimmed = output.trim();
  if (!trimmed) return "";
  const failureIndex = Math.max(
    trimmed.lastIndexOf("FAIL"),
    trimmed.lastIndexOf("[ERROR]"),
    trimmed.lastIndexOf("Assertion"),
    trimmed.lastIndexOf("error"),
  );
  return sanitizeDiagnosticEvidence(trimmed.slice(Math.max(0, failureIndex >= 0 ? failureIndex : trimmed.length - 4_000)));
}

function diagnosticBlock(lines: string[], index: number): string {
  return sanitizeDiagnosticEvidence(lines.slice(Math.max(0, index - 4), Math.min(lines.length, index + 4)).join("\n"));
}

function assertionValue(evidence: string, label: string): string | undefined {
  const match = evidence.match(new RegExp(`${label}\\s*:?\\s*<?([^>\\n]+)>?`, "i"));
  return match?.[1]?.trim();
}

function diagnosticPath(command: VerificationCommand, input: string, manifest?: ArtifactManifest): string | undefined {
  const absolute = isAbsolute(input) ? resolve(input) : resolve(command.cwd, input);
  const path = relative(command.workspaceRoot, absolute).replace(/\\/g, "/");
  if (path === ".." || path.startsWith("../") || isAbsolute(path)) return undefined;
  return manifestPath(path, input, manifest);
}

function junitPath(
  command: VerificationCommand,
  className: string,
  fileName: string,
  manifest?: ArtifactManifest,
): string | undefined {
  const topLevelClass = className.replace(/\$.*$/, "");
  const derived = `backend/src/test/java/${topLevelClass.replace(/\./g, "/")}.java`;
  return manifestPath(derived, fileName, manifest) || diagnosticPath(command, fileName, manifest);
}

function manifestPath(candidate: string, original: string, manifest?: ArtifactManifest): string | undefined {
  if (!manifest) return candidate;
  const paths = manifest.files.map(({ relativePath }) => relativePath.replace(/\\/g, "/"));
  if (paths.includes(candidate)) return candidate;
  const normalizedOriginal = original.replace(/\\/g, "/").replace(/^\.\//, "");
  const suffixMatches = paths.filter((path) => path === normalizedOriginal || path.endsWith(`/${normalizedOriginal}`));
  return suffixMatches.length === 1 ? suffixMatches[0] : undefined;
}

function truncateUtf8(value: string, maxBytes: number): { value: string; truncated: boolean } {
  const buffer = Buffer.from(value, "utf8");
  if (buffer.length <= maxBytes) return { value, truncated: false };
  return {
    value: `[earlier output omitted]\n${truncateUtf8Tail(value, maxBytes)}`,
    truncated: true,
  };
}

function truncateUtf8Tail(value: string, maxBytes: number): string {
  const buffer = Buffer.from(value, "utf8");
  if (buffer.length <= maxBytes) return value;
  return buffer.subarray(buffer.length - maxBytes).toString("utf8").replace(/^\uFFFD+/, "");
}

function assertInside(root: string, candidate: string): void {
  const rel = relative(resolve(root), resolve(candidate));
  if (rel === ".." || rel.startsWith(`..${sep}`) || isAbsolute(rel)) {
    throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_PATH_FORBIDDEN", "Path escapes verification workspace.");
  }
}
