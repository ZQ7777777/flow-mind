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
        if (input.signal?.aborted) {
          stages.push(stageResult(command, {
            exitCode: null,
            stdout: "",
            stderr: "",
            timedOut: false,
            cancelled: true,
          }, undefined, false));
          break;
        }
        const result = await execute(command);
        const combined = `[stdout]\n${result.stdout}\n[stderr]\n${result.stderr}`;
        const truncated = truncateUtf8(combined, command.maxOutputBytes);
        const logPath = join(logDir, `${command.stage.toLowerCase()}.log`);
        writeFileSync(logPath, truncated.value, "utf8");
        stages.push(stageResult(command, result, logPath, truncated.truncated));
        if (result.infrastructureError || result.timedOut || result.cancelled) break;
      }
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
    let bytes = 0;
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
      if (bytes >= command.maxOutputBytes) {
        truncated = true;
        return;
      }
      const remaining = command.maxOutputBytes - bytes;
      const kept = chunk.subarray(0, remaining);
      bytes += kept.length;
      if (kept.length < chunk.length) truncated = true;
      if (target === "stdout") stdout += kept.toString("utf8");
      else stderr += kept.toString("utf8");
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
  const parsedDiagnostics = status === "PASSED" ? [] : parseCommandDiagnostics(command, result, hardGate);
  return {
    stage: command.stage,
    status,
    hardGate,
    summary: message,
    diagnostics: status === "PASSED" ? [] : parsedDiagnostics.length ? parsedDiagnostics : [{
      code: status === "INFRASTRUCTURE_FAILED" ? "VERIFICATION_INFRASTRUCTURE_FAILED" : "VERIFICATION_COMMAND_FAILED",
      message,
      severity: "ERROR",
      hardGate,
    }],
    exitCode: result.exitCode === null ? undefined : result.exitCode,
    logPath,
    outputTruncated,
  };
}

function parseCommandDiagnostics(
  command: VerificationCommand,
  result: VerificationCommandResult,
  hardGate: boolean,
): QualityDiagnostic[] {
  const diagnostics: QualityDiagnostic[] = [];
  const output = `${result.stdout}\n${result.stderr}`;
  for (const rawLine of output.split(/\r?\n/)) {
    const line = rawLine.trim();
    let match = line.match(/^(.+?)\((\d+),(\d+)\):\s*(?:error|warning)\s+(TS\d+):\s*(.+)$/i);
    if (match) {
      diagnostics.push({
        code: match[4],
        message: match[5].trim(),
        severity: "ERROR",
        hardGate,
        relativePath: diagnosticPath(command, match[1]),
        line: Number(match[2]),
        column: Number(match[3]),
      });
      continue;
    }
    match = line.match(/^(?:\[ERROR\]\s*)?(.+?\.java):\[(\d+),(\d+)\]\s*(.+)$/);
    if (!match) match = line.match(/^(?:\[ERROR\]\s*)?(.+?\.java):(\d+):(?:(\d+):)?\s*(?:error:\s*)?(.+)$/i);
    if (match) {
      diagnostics.push({
        code: "JAVA_COMPILER_ERROR",
        message: match[4].trim(),
        severity: "ERROR",
        hardGate,
        relativePath: diagnosticPath(command, match[1]),
        line: Number(match[2]),
        column: match[3] ? Number(match[3]) : undefined,
      });
      continue;
    }
    match = line.match(/^[>\s❯]*([^\s]+\.(?:spec|test)\.[jt]sx?):(\d+):(\d+)/);
    if (match) {
      diagnostics.push({
        code: "TEST_FAILURE",
        message: line,
        severity: "ERROR",
        hardGate,
        relativePath: diagnosticPath(command, match[1]),
        line: Number(match[2]),
        column: Number(match[3]),
      });
    }
  }
  return diagnostics;
}

function diagnosticPath(command: VerificationCommand, input: string): string | undefined {
  const absolute = isAbsolute(input) ? resolve(input) : resolve(command.cwd, input);
  const path = relative(command.workspaceRoot, absolute).replace(/\\/g, "/");
  return path === ".." || path.startsWith("../") || isAbsolute(path) ? undefined : path;
}

function truncateUtf8(value: string, maxBytes: number): { value: string; truncated: boolean } {
  const buffer = Buffer.from(value, "utf8");
  if (buffer.length <= maxBytes) return { value, truncated: false };
  return {
    value: Buffer.concat([buffer.subarray(0, maxBytes), Buffer.from("\n[output truncated]\n")]).toString("utf8"),
    truncated: true,
  };
}

function assertInside(root: string, candidate: string): void {
  const rel = relative(resolve(root), resolve(candidate));
  if (rel === ".." || rel.startsWith(`..${sep}`) || isAbsolute(rel)) {
    throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_PATH_FORBIDDEN", "Path escapes verification workspace.");
  }
}
