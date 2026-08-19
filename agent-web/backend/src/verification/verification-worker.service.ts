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
import { StringDecoder } from "node:string_decoder";
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

export type VerificationCommandStage = Exclude<QualityStageName, "STATIC_VALIDATION">;

export interface VerificationCommand {
  stage: VerificationCommandStage;
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
  /** Optional command-stage subset used after repair to avoid rerunning unaffected gates. */
  stages?: VerificationCommandStage[];
  /** Per-stage progress sink so the orchestrator can stream stage status to the UI. */
  onStage?: (stage: VerificationCommandStage, status: QualityStageResult["status"], hardGate: boolean) => void;
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
      writeGeneratedFrontendTsconfig(input, workspaceRoot);
      const commands = fixedCommands(input, workspaceRoot);
      const frontendOnly = input.contract.generationMode === "FRONTEND_ONLY";
      const stages: QualityStageResult[] = frontendOnly ? [notApplicableStage("BACKEND_COMPILE")] : [];
      const execute = input.execute || executeVerificationCommand;
      await installFrontendDependencies(input, workspaceRoot, execute, runId, logDir);
      for (const command of commands) {
        const hardGate = HARD_STAGES.has(command.stage);
        const blockers = commandBlockers(command.stage, stages);
        if (blockers.length) {
          stages.push(blockedStageResult(command, blockers));
          continue;
        }
        if (input.signal?.aborted) {
          const cancelledStage = stageResult(command, {
            exitCode: null,
            stdout: "",
            stderr: "",
            timedOut: false,
            cancelled: true,
          }, undefined, false, input.manifest);
          stages.push(cancelledStage);
          input.onStage?.(command.stage, cancelledStage.status, hardGate);
          break;
        }
        input.onStage?.(command.stage, "RUNNING", hardGate);
        const result = await execute(command);
        const combined = `[stdout]\n${result.stdout}\n[stderr]\n${result.stderr}`;
        const truncated = truncateUtf8(combined, command.maxOutputBytes);
        const logPath = join(logDir, `${command.stage.toLowerCase()}.log`);
        writeFileSync(logPath, truncated.value, "utf8");
        const stage = stageResult(command, result, logPath, truncated.truncated, input.manifest);
        stages.push(stage);
        input.onStage?.(command.stage, stage.status, hardGate);
        if (result.infrastructureError || result.timedOut || result.cancelled) break;
      }
      if (frontendOnly) stages.push(notApplicableStage("BACKEND_TESTS"));
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
    const stdoutDecoder = new StringDecoder("utf8");
    const stderrDecoder = new StringDecoder("utf8");
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
      if (target === "stdout") stdout += stdoutDecoder.write(chunk);
      else stderr += stderrDecoder.write(chunk);
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
      stdout += stdoutDecoder.end();
      stderr += stderrDecoder.end();
      if (truncated) stderr += "\n[output truncated]";
      resolveResult({ ...result, stdout, stderr });
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
      args: ["/d", "/s", "/c", `${executable}.cmd ${args.map(windowsCommandArg).join(" ")}`],
    };
  };
  const mavenRepoArgs = configuredMavenRepoArgs();
  const frontendOnly = input.contract.generationMode === "FRONTEND_ONLY";
  const backend = input.contract.backend ? resolve(workspaceRoot, input.contract.backend.rootDir) : "";
  const frontend = resolve(workspaceRoot, input.contract.frontend.rootDir);
  const commands: VerificationCommand[] = [
    ...(!frontendOnly ? [{ ...common, stage: "BACKEND_COMPILE" as const, ...command("mvn", ["-q", ...mavenRepoArgs, "-DskipTests", "compile"]), cwd: backend }] : []),
    { ...common, stage: "FRONTEND_TYPECHECK", ...command("npm", ["exec", "--", "vue-tsc", "-p", "tsconfig.generated.json", "--noEmit"]), cwd: frontend },
    { ...common, stage: "FRONTEND_BUILD", ...command("npm", ["run", "build"]), cwd: frontend },
    ...(!frontendOnly ? [{ ...common, stage: "BACKEND_TESTS" as const, ...command("mvn", ["-q", ...mavenRepoArgs, "test"]), cwd: backend }] : []),
    { ...common, stage: "FRONTEND_TESTS", ...command("npm", ["run", "test", "--", "--run"]), cwd: frontend },
  ];
  const selected = input.stages ? new Set(input.stages) : undefined;
  return selected ? commands.filter(({ stage }) => selected.has(stage)) : commands;
}

function notApplicableStage(stage: "BACKEND_COMPILE" | "BACKEND_TESTS"): QualityStageResult {
  return {
    stage,
    status: "SKIPPED",
    hardGate: false,
    summary: "Not applicable for FRONTEND_ONLY generation.",
    diagnostics: [],
  };
}

function configuredMavenRepoArgs(): string[] {
  const repoLocal = process.env.AGENT_MAVEN_REPO_LOCAL?.trim();
  return repoLocal ? [`-Dmaven.repo.local=${resolve(repoLocal)}`] : [];
}

function windowsCommandArg(arg: string): string {
  return /[\s"]/u.test(arg) ? `"${arg.replace(/"/g, '\\"')}"` : arg;
}

function fixedEnvironment(): NodeJS.ProcessEnv {
  const names = [
    "PATH", "Path", "PATHEXT", "SYSTEMROOT", "WINDIR", "COMSPEC",
    "JAVA_HOME", "MAVEN_HOME", "HOME", "USERPROFILE", "HOMEDRIVE", "HOMEPATH", "TEMP", "TMP",
  ];
  const env: NodeJS.ProcessEnv = {
    CI: "true",
    NO_COLOR: "1",
    JAVA_TOOL_OPTIONS: "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8",
  };
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

function writeGeneratedFrontendTsconfig(input: VerificationWorkerInput, workspaceRoot: string): void {
  const frontendRoot = resolve(workspaceRoot, input.contract.frontend.rootDir);
  const frontendPrefix = `${input.contract.frontend.rootDir.replace(/\\/g, "/").replace(/\/$/, "")}/`;
  const include = input.manifest.files
    .map(({ relativePath }) => relativePath.replace(/\\/g, "/"))
    .filter((path) => path.startsWith(frontendPrefix) && /\.(vue|tsx?)$/i.test(path))
    .map((path) => path.slice(frontendPrefix.length));
  mkdirSync(frontendRoot, { recursive: true });
  writeFileSync(
    join(frontendRoot, "tsconfig.generated.json"),
    `${JSON.stringify({ extends: "./tsconfig.json", include }, null, 2)}\n`,
    "utf8",
  );
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
  const commandText = formatVerificationCommand(command);
  let status: QualityStageResult["status"];
  if (result.cancelled) status = "CANCELLED";
  else if (result.timedOut || result.infrastructureError) status = "INFRASTRUCTURE_FAILED";
  else status = result.exitCode === 0 ? "PASSED" : "FAILED";
  const fallbackEvidence = diagnosticExcerpt(`${result.stdout}\n${result.stderr}`);
  let parsedDiagnostics = status === "PASSED" ? [] : parseCommandDiagnostics(command, result, hardGate, manifest);
  if (status === "FAILED" && !parsedDiagnostics.length) {
    parsedDiagnostics = [qualityDiagnostic(command.stage, {
      code: "VERIFICATION_COMMAND_FAILED",
      message: `Command exited with code ${result.exitCode}.`,
      hardGate,
      evidence: fallbackEvidence || `Command exited with code ${result.exitCode}.`,
      command: commandText,
      exitCode: result.exitCode === null ? undefined : result.exitCode,
      expected: "The command must exit successfully.",
      repairHint: "Use the evidence and verification log to locate and correct the failing code or assertion.",
      repairability: "CODE_ACTIONABLE",
      scope: "CURRENT_GENERATION",
    })];
  }
  if (status === "FAILED" && parsedDiagnostics.length
    && !parsedDiagnostics.some(({ scope }) => scope === "CURRENT_GENERATION" || !scope)) {
    status = "PASSED";
  }
  const message = result.infrastructureError
    || (result.timedOut ? "Verification command timed out."
      : result.cancelled ? "Verification command was cancelled."
        : result.exitCode === 0 ? "Command passed."
          : status === "PASSED"
            ? `Command exited with code ${result.exitCode}; no current-generation diagnostics were found.`
            : `Command exited with code ${result.exitCode}.`);
  return {
    stage: command.stage,
    status,
    hardGate,
    summary: message,
    command: commandText,
    diagnostics: result.exitCode === 0 && status === "PASSED" ? [] : parsedDiagnostics.length ? parsedDiagnostics : [qualityDiagnostic(command.stage, {
      code: status === "INFRASTRUCTURE_FAILED" ? "VERIFICATION_INFRASTRUCTURE_FAILED" : "VERIFICATION_COMMAND_FAILED",
      message,
      hardGate,
      evidence: fallbackEvidence || message,
      command: commandText,
      exitCode: result.exitCode === null ? undefined : result.exitCode,
      expected: status === "INFRASTRUCTURE_FAILED" ? "The verification environment must be available." : "The command must exit successfully.",
      repairHint: status === "INFRASTRUCTURE_FAILED"
        ? "Do not change generated code for this failure; retry after the verification environment is restored."
        : "Use the evidence and verification log to locate and correct the failing code or assertion.",
      repairability: status === "INFRASTRUCTURE_FAILED" ? "INFRASTRUCTURE" : "CODE_ACTIONABLE",
      scope: status === "INFRASTRUCTURE_FAILED" ? undefined : "CURRENT_GENERATION",
    })],
    exitCode: result.exitCode === null ? undefined : result.exitCode,
    logPath,
    outputTruncated,
  };
}

function commandBlockers(stage: QualityStageName, stages: QualityStageResult[]): QualityStageName[] {
  const dependencies: Partial<Record<QualityStageName, QualityStageName[]>> = {
    FRONTEND_TYPECHECK: ["BACKEND_COMPILE"],
    FRONTEND_BUILD: ["BACKEND_COMPILE", "FRONTEND_TYPECHECK"],
    BACKEND_TESTS: ["BACKEND_COMPILE", "FRONTEND_TYPECHECK", "FRONTEND_BUILD"],
    FRONTEND_TESTS: ["BACKEND_COMPILE", "FRONTEND_TYPECHECK", "FRONTEND_BUILD"],
  };
  for (const dependency of dependencies[stage] || []) {
    const result = stages.find((item) => item.stage === dependency);
    const notApplicable = result?.status === "SKIPPED" && result.hardGate === false
      && result.diagnostics.length === 0 && result.summary.startsWith("Not applicable");
    if (result && result.status !== "PASSED" && !notApplicable) return result.blockedBy?.length ? result.blockedBy : [dependency];
  }
  return [];
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
        command: formatVerificationCommand(command),
        exitCode: result.exitCode === null ? undefined : result.exitCode,
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
        command: formatVerificationCommand(command),
        exitCode: result.exitCode === null ? undefined : result.exitCode,
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
        command: formatVerificationCommand(command),
        exitCode: result.exitCode === null ? undefined : result.exitCode,
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
  return scopeDiagnostics(command, [...new Map(enriched.map((item) => [item.diagnosticId, item])).values()], manifest);
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
  const responseCharsetMismatch = isResponseCharsetMismatch(message);
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
      : responseCharsetMismatch
        ? "Set the production controller's plain-text error response Content-Type to text/plain;charset=UTF-8, then assert its status, advertised charset, and decoded body. A CharacterEncodingFilter added only to the test is insufficient."
        : "Use the assertion difference and generated-code stack frame to correct the implementation without weakening the test.",
    acceptedForms: responseCharsetMismatch
      ? ["ResponseEntity status with Content-Type text/plain;charset=UTF-8 and the original error body"]
      : undefined,
  });
}

function isResponseCharsetMismatch(message: string): boolean {
  const match = /Response content expected:<([^>]+)> but was:<([^>]+)>/i.exec(message);
  return Boolean(match && /[^\x00-\x7F]/.test(match[1]) && /\?{2,}/.test(match[2]));
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
  return sanitizeDiagnosticEvidence(lines.slice(Math.max(0, index - 3), Math.min(lines.length, index + 8)).join("\n"));
}

function formatVerificationCommand(command: VerificationCommand): string {
  return [command.executable, ...command.args].join(" ");
}

function assertionValue(evidence: string, label: string): string | undefined {
  const match = evidence.match(new RegExp(`${label}\\s*:?\\s*<?([^>\\n]+)>?`, "i"));
  return match?.[1]?.trim();
}

function diagnosticPath(command: VerificationCommand, input: string, manifest?: ArtifactManifest): string | undefined {
  const absolute = isAbsolute(input) ? resolve(input) : resolve(command.cwd, input);
  const path = relative(command.workspaceRoot, absolute).replace(/\\/g, "/");
  if (path === ".." || path.startsWith("../") || isAbsolute(path)) return undefined;
  return bestDiagnosticPath(path, input, manifest);
}

function junitPath(
  command: VerificationCommand,
  className: string,
  fileName: string,
  manifest?: ArtifactManifest,
): string | undefined {
  const topLevelClass = className.replace(/\$.*$/, "");
  const derived = `backend/src/test/java/${topLevelClass.replace(/\./g, "/")}.java`;
  return bestDiagnosticPath(derived, fileName, manifest) || diagnosticPath(command, fileName, manifest);
}

function bestDiagnosticPath(candidate: string, original: string, manifest?: ArtifactManifest): string | undefined {
  if (!manifest) return candidate;
  const paths = manifest.files.map(({ relativePath }) => relativePath.replace(/\\/g, "/"));
  if (paths.includes(candidate)) return candidate;
  const normalizedOriginal = original.replace(/\\/g, "/").replace(/^\.\//, "");
  const suffixMatches = paths.filter((path) => path === normalizedOriginal || path.endsWith(`/${normalizedOriginal}`));
  return suffixMatches.length === 1 ? suffixMatches[0] : candidate;
}

function scopeDiagnostics(
  command: VerificationCommand,
  diagnostics: QualityDiagnostic[],
  manifest?: ArtifactManifest,
): QualityDiagnostic[] {
  if (!manifest) return diagnostics.map((diagnostic) => ({ ...diagnostic, scope: diagnostic.scope || "CURRENT_GENERATION" }));
  const manifestPaths = manifest.files.map(({ relativePath }) => relativePath.replace(/\\/g, "/"));
  const manifestSet = new Set(manifestPaths);
  return diagnostics.map((diagnostic) => {
    if (diagnostic.scope) return diagnostic;
    const relativePath = diagnostic.relativePath?.replace(/\\/g, "/");
    if (!relativePath || manifestSet.has(relativePath)) {
      return { ...diagnostic, relativePath, scope: "CURRENT_GENERATION" };
    }
    return {
      ...diagnostic,
      relativePath,
      scope: hasDirectManifestDependency(command, relativePath, manifestPaths)
        ? "INTEGRATION_IMPACT"
        : "PRE_EXISTING",
    };
  });
}

function hasDirectManifestDependency(
  command: VerificationCommand,
  diagnosticRelativePath: string,
  manifestPaths: string[],
): boolean {
  const absolute = resolve(command.workspaceRoot, diagnosticRelativePath);
  const rel = relative(command.workspaceRoot, absolute);
  if (rel === ".." || rel.startsWith(`..${sep}`) || isAbsolute(rel) || !existsSync(absolute) || !lstatSync(absolute).isFile()) {
    return false;
  }
  const source = readFileSync(absolute, "utf8");
  if (/\.(vue|tsx?|jsx?)$/i.test(diagnosticRelativePath)) {
    return frontendSourceImportsManifest(command, absolute, source, manifestPaths);
  }
  if (/\.java$/i.test(diagnosticRelativePath)) {
    return javaSourceReferencesManifest(command.workspaceRoot, source, manifestPaths);
  }
  return false;
}

function frontendSourceImportsManifest(
  command: VerificationCommand,
  absoluteSourcePath: string,
  source: string,
  manifestPaths: string[],
): boolean {
  const imports = [...source.matchAll(/\b(?:import|export)\s+(?:type\s+)?(?:[^'"]*?\s+from\s+)?["']([^"']+)["']|import\(\s*["']([^"']+)["']\s*\)/g)]
    .map((match) => match[1] || match[2])
    .filter((value): value is string => Boolean(value));
  return imports.some((specifier) => frontendImportMatchesManifest(
    command,
    absoluteSourcePath,
    specifier,
    manifestPaths,
  ));
}

function frontendImportMatchesManifest(
  command: VerificationCommand,
  absoluteSourcePath: string,
  specifier: string,
  manifestPaths: string[],
): boolean {
  const bases: string[] = [];
  if (specifier.startsWith(".")) {
    bases.push(resolve(dirname(absoluteSourcePath), specifier));
  } else if (specifier.startsWith("@/")) {
    bases.push(resolve(command.cwd, "src", specifier.slice(2)));
  } else if (specifier.startsWith("/src/")) {
    bases.push(resolve(command.cwd, specifier.slice(1)));
  }
  const candidates = bases.flatMap((base) => pathCandidates(base))
    .map((candidate) => relative(command.workspaceRoot, candidate).replace(/\\/g, "/"));
  const candidateSet = new Set(candidates);
  if (manifestPaths.some((path) => candidateSet.has(path))) return true;

  const normalizedSpecifier = specifier
    .replace(/^@\//, "frontend/src/")
    .replace(/^src\//, "frontend/src/")
    .replace(/^\//, "");
  const specifierNoExt = stripKnownExtension(normalizedSpecifier);
  return manifestPaths.some((path) => stripKnownExtension(path).endsWith(specifierNoExt));
}

function pathCandidates(base: string): string[] {
  return [
    base,
    `${base}.ts`,
    `${base}.tsx`,
    `${base}.vue`,
    `${base}.js`,
    `${base}.jsx`,
    `${base}.d.ts`,
    join(base, "index.ts"),
    join(base, "index.tsx"),
    join(base, "index.vue"),
  ];
}

function stripKnownExtension(value: string): string {
  return value.replace(/\.d\.ts$/i, "").replace(/\.(vue|tsx?|jsx?)$/i, "");
}

function javaSourceReferencesManifest(workspaceRoot: string, source: string, manifestPaths: string[]): boolean {
  const imports = [...source.matchAll(/^\s*import\s+(?:static\s+)?([\w.]+)(?:\.\*)?\s*;/gm)]
    .map((match) => match[1]);
  if (!imports.length) return false;
  const sourcePackage = source.match(/^\s*package\s+([\w.]+)\s*;/m)?.[1];
  const manifestTypes = manifestPaths.flatMap((path) => javaManifestType(workspaceRoot, path));
  return manifestTypes.some(({ packageName, className, fqn }) =>
    imports.includes(fqn)
    || imports.some((item) => item === packageName || item.startsWith(`${fqn}.`))
    || (sourcePackage === packageName && new RegExp(`\\b${className}\\b`).test(source)));
}

function javaManifestType(
  workspaceRoot: string,
  relativePath: string,
): Array<{ packageName: string; className: string; fqn: string }> {
  if (!relativePath.endsWith(".java")) return [];
  const absolute = resolve(workspaceRoot, relativePath);
  if (!existsSync(absolute) || !lstatSync(absolute).isFile()) return [];
  const source = readFileSync(absolute, "utf8");
  const packageName = source.match(/^\s*package\s+([\w.]+)\s*;/m)?.[1]
    || relativePath.replace(/^.*\/src\/(?:main|test)\/java\//, "").replace(/\/[^/]+\.java$/, "").replace(/\//g, ".");
  const className = source.match(/\b(?:class|interface|enum|record)\s+([A-Za-z_]\w*)/)?.[1]
    || relativePath.split("/").at(-1)!.replace(/\.java$/, "");
  return [{ packageName, className, fqn: `${packageName}.${className}` }];
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
