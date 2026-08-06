import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { ArtifactManifest, GenerationTargetContract } from "@flowmind/agent-contracts";
import {
  VerificationWorkerService,
  type VerificationCommand,
  type VerificationCommandResult,
} from "../src/verification/verification-worker.service.js";
import { write } from "./generation-fixture.js";

describe("verification worker", () => {
  let root: string;
  let target: string;
  let staging: string;
  let contract: GenerationTargetContract;
  let manifest: ArtifactManifest;

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), "flowmind-worker-"));
    target = join(root, "target");
    staging = join(root, "staging");
    write(target, "backend/pom.xml", "<project/>\n");
    write(target, "frontend/package.json", "{}\n");
    write(target, "frontend/src/api/generated/example.ts", "export const value = 'target';\n");
    write(staging, "frontend/src/api/generated/example.ts", "export const value = 'staged';\n");
    contract = createContract();
    manifest = {
      generationId: "generation-worker",
      targetRoot: target,
      contractVersion: "1.0",
      revision: 1,
      files: [{
        relativePath: "frontend/src/api/generated/example.ts",
        changeType: "MODIFY",
        baseSha256: "a".repeat(64),
        stagedSha256: "b".repeat(64),
        sizeBytes: 31,
        validationStatus: "PENDING",
        editedByUser: false,
      }],
    };
  });

  afterEach(() => rmSync(root, { recursive: true, force: true }));

  it("overlays a disposable copy and runs only fixed commands", async () => {
    const commands: VerificationCommand[] = [];
    const workspaces: string[] = [];
    const execute = async (command: VerificationCommand): Promise<VerificationCommandResult> => {
      commands.push(command);
      workspaces.push(command.workspaceRoot);
      expect(command.shell).toBe(false);
      expect(readFileSync(join(command.workspaceRoot, "frontend/src/api/generated/example.ts"), "utf8"))
        .toContain("staged");
      return { exitCode: 0, stdout: "ok", stderr: "", timedOut: false, cancelled: false };
    };

    const worker = new VerificationWorkerService();
    const result = await worker.run({
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      execute,
    });

    const expectedCommands = process.platform === "win32"
      ? [
        { stage: "BACKEND_COMPILE", executable: process.env.COMSPEC || "cmd.exe", args: ["/d", "/s", "/c", "mvn.cmd -q -DskipTests compile"] },
        { stage: "BACKEND_TESTS", executable: process.env.COMSPEC || "cmd.exe", args: ["/d", "/s", "/c", "mvn.cmd -q test"] },
        { stage: "FRONTEND_TYPECHECK", executable: process.env.COMSPEC || "cmd.exe", args: ["/d", "/s", "/c", "npm.cmd run typecheck"] },
        { stage: "FRONTEND_TESTS", executable: process.env.COMSPEC || "cmd.exe", args: ["/d", "/s", "/c", "npm.cmd run test -- --run"] },
        { stage: "FRONTEND_BUILD", executable: process.env.COMSPEC || "cmd.exe", args: ["/d", "/s", "/c", "npm.cmd run build"] },
      ]
      : [
        { stage: "BACKEND_COMPILE", executable: "mvn", args: ["-q", "-DskipTests", "compile"] },
        { stage: "BACKEND_TESTS", executable: "mvn", args: ["-q", "test"] },
        { stage: "FRONTEND_TYPECHECK", executable: "npm", args: ["run", "typecheck"] },
        { stage: "FRONTEND_TESTS", executable: "npm", args: ["run", "test", "--", "--run"] },
        { stage: "FRONTEND_BUILD", executable: "npm", args: ["run", "build"] },
      ];
    expect(commands.map(({ stage, executable, args }) => ({ stage, executable, args }))).toEqual(expectedCommands);
    expect(result.stages.every(({ status }) => status === "PASSED")).toBe(true);
    expect(readFileSync(join(target, "frontend/src/api/generated/example.ts"), "utf8")).toContain("target");
    expect(new Set(workspaces).size).toBe(1);
    expect(existsSync(workspaces[0])).toBe(false);
  });

  it("classifies hard and soft failures and truncates command output", async () => {
    const worker = new VerificationWorkerService();
    const result = await worker.run({
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      maxOutputBytes: 64,
      execute: async (command) => ({
        exitCode: command.stage === "BACKEND_TESTS" ? 1 : 0,
        stdout: `${"x".repeat(256)}\nFINAL_FAILURE_MARKER`,
        stderr: "",
        timedOut: false,
        cancelled: false,
      }),
    });
    const tests = result.stages.find(({ stage }) => stage === "BACKEND_TESTS")!;
    expect(tests).toEqual(expect.objectContaining({
      status: "FAILED",
      hardGate: false,
      outputTruncated: true,
    }));
    expect(readFileSync(tests.logPath!, "utf8").length).toBeLessThan(256);
    expect(readFileSync(tests.logPath!, "utf8")).toContain("FINAL_FAILURE_MARKER");
  });
  it("normalizes TypeScript compiler output into source diagnostics", async () => {
    const worker = new VerificationWorkerService();
    const result = await worker.run({
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      execute: async (command) => ({
        exitCode: command.stage === "FRONTEND_TYPECHECK" ? 2 : 0,
        stdout: "",
        stderr: command.stage === "FRONTEND_TYPECHECK"
          ? "src/modules/generated/example.ts(7,9): error TS2345: Argument is invalid.\n"
          : "",
        timedOut: false,
        cancelled: false,
      }),
    });

    const typecheck = result.stages.find(({ stage }) => stage === "FRONTEND_TYPECHECK")!;
    expect(typecheck.diagnostics).toContainEqual(expect.objectContaining({
      diagnosticId: expect.any(String),
      stage: "FRONTEND_TYPECHECK",
      code: "TS2345",
      relativePath: "frontend/src/modules/generated/example.ts",
      line: 7,
      column: 9,
      message: "Argument is invalid.",
      evidence: expect.stringContaining("TS2345"),
      repairability: "CODE_ACTIONABLE",
    }));
  });

  it("keeps a redacted actionable excerpt when command output is not recognized", async () => {
    const worker = new VerificationWorkerService();
    const result = await worker.run({
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      execute: async (command) => ({
        exitCode: command.stage === "BACKEND_TESTS" ? 1 : 0,
        stdout: "",
        stderr: command.stage === "BACKEND_TESTS"
          ? "Assertion failed for applicationNo\nAuthorization: Bearer top-secret-token\nexpected: 42\nactual: 41\n"
          : "",
        timedOut: false,
        cancelled: false,
      }),
    });

    const diagnostic = result.stages.find(({ stage }) => stage === "BACKEND_TESTS")!.diagnostics[0];
    expect(diagnostic).toEqual(expect.objectContaining({
      diagnosticId: expect.any(String),
      stage: "BACKEND_TESTS",
      code: "VERIFICATION_COMMAND_FAILED",
      evidence: expect.stringContaining("expected: 42"),
      repairability: "CODE_ACTIONABLE",
    }));
    expect(diagnostic.evidence).not.toContain("top-secret-token");
  });

  it("normalizes JUnit and Vitest assertion failures with stable fingerprints", async () => {
    const execute = async (command: VerificationCommand): Promise<VerificationCommandResult> => {
      if (command.stage === "BACKEND_TESTS") {
        return {
          exitCode: 1,
          stdout: "",
          stderr: "org.opentest4j.AssertionFailedError: expected: <42> but was: <41>\n\tat com.flowmind.business.generated.EntryApplicationServiceTest.mapsAmount(EntryApplicationServiceTest.java:27)\n",
          timedOut: false,
          cancelled: false,
        };
      }
      if (command.stage === "FRONTEND_TESTS") {
        return {
          exitCode: 1,
          stdout: "FAIL src/modules/generated/__tests__/EntryApplicationApply.test.ts > submits amount\nAssertionError: expected 41 to be 42\n❯ src/modules/generated/__tests__/EntryApplicationApply.test.ts:18:9\n",
          stderr: "",
          timedOut: false,
          cancelled: false,
        };
      }
      return { exitCode: 0, stdout: "ok", stderr: "", timedOut: false, cancelled: false };
    };
    const worker = new VerificationWorkerService();
    const input = {
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      execute,
    };
    const first = await worker.run(input);
    const second = await worker.run(input);
    const junit = first.stages.find(({ stage }) => stage === "BACKEND_TESTS")!.diagnostics
      .find(({ code }) => code === "JUNIT_TEST_FAILURE")!;
    const vitest = first.stages.find(({ stage }) => stage === "FRONTEND_TESTS")!.diagnostics
      .find(({ code }) => code === "VITEST_TEST_FAILURE")!;
    expect(junit).toEqual(expect.objectContaining({
      relativePath: "backend/EntryApplicationServiceTest.java",
      line: 27,
      actual: expect.stringContaining("41"),
      expected: expect.stringContaining("42"),
    }));
    expect(vitest).toEqual(expect.objectContaining({
      relativePath: "frontend/src/modules/generated/__tests__/EntryApplicationApply.test.ts",
      line: 18,
      column: 9,
      evidence: expect.stringContaining("expected 41 to be 42"),
    }));
    expect(second.stages.find(({ stage }) => stage === "BACKEND_TESTS")!.diagnostics
      .find(({ code }) => code === "JUNIT_TEST_FAILURE")!.diagnosticId).toBe(junit.diagnosticId);
  });

  it("restores frontend dependencies from the lockfile instead of copying node_modules", async () => {
    write(target, "frontend/package-lock.json", "{\"lockfileVersion\":3}\n");
    write(target, "frontend/node_modules/broken-package/index.js", "incomplete dependency tree\n");
    const commands: VerificationCommand[] = [];
    const worker = new VerificationWorkerService();

    await worker.run({
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      execute: async (command) => {
        commands.push(command);
        if (command.args.join(" ").includes(" ci ")) {
          expect(existsSync(join(command.workspaceRoot, "frontend/node_modules/broken-package/index.js"))).toBe(false);
        }
        return { exitCode: 0, stdout: "ok", stderr: "", timedOut: false, cancelled: false };
      },
    });

    expect(commands[0]).toEqual(expect.objectContaining({
      stage: "FRONTEND_TYPECHECK",
      cwd: expect.stringContaining("frontend"),
    }));
    expect(commands[0].args.join(" ")).toContain("ci");
  });
});

function createContract(): GenerationTargetContract {
  return {
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
    readableReferenceFiles: [],
    allowedOutputPatterns: [],
    protectedFiles: [],
  };
}
