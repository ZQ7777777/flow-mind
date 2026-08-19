import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { ArtifactManifest, GenerationTargetContract } from "@flowmind/agent-contracts";
import {
  executeVerificationCommand,
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
    write(target, "frontend/tsconfig.json", "{\"compilerOptions\":{\"strict\":true},\"include\":[\"src/**/*.ts\",\"src/**/*.vue\"]}\n");
    write(target, "frontend/src/api/generated/example.ts", "export const value = 'target';\n");
    write(staging, "frontend/src/api/generated/example.ts", "export const value = 'staged';\n");
    write(staging, "backend/src/main/java/com/flowmind/business/generated/EntryApplicationService.java", "class EntryApplicationService {}\n");
    write(staging, "backend/src/test/java/com/flowmind/business/generated/EntryApplicationServiceTest.java", "class EntryApplicationServiceTest {}\n");
    write(staging, "backend/src/test/java/com/flowmind/business/generated/EntryApplicationControllerTest.java", "class EntryApplicationControllerTest {}\n");
    write(staging, "frontend/src/modules/generated/__tests__/EntryApplicationApply.test.ts", "export {};\n");
    write(staging, "frontend/src/modules/generated/example.ts", "export {};\n");
    contract = createContract();
    manifest = {
      generationId: "generation-worker",
      targetRoot: target,
      contractVersion: "1.0",
      revision: 1,
      files: [{
        relativePath: "backend/src/main/java/com/flowmind/business/generated/EntryApplicationService.java",
        changeType: "ADD",
        stagedSha256: "1".repeat(64),
        sizeBytes: 32,
        validationStatus: "PENDING",
        editedByUser: false,
      }, {
        relativePath: "frontend/src/api/generated/example.ts",
        changeType: "MODIFY",
        baseSha256: "a".repeat(64),
        stagedSha256: "b".repeat(64),
        sizeBytes: 31,
        validationStatus: "PENDING",
        editedByUser: false,
      }, {
        relativePath: "backend/src/test/java/com/flowmind/business/generated/EntryApplicationServiceTest.java",
        changeType: "ADD",
        stagedSha256: "c".repeat(64),
        sizeBytes: 37,
        validationStatus: "PENDING",
        editedByUser: false,
      }, {
        relativePath: "backend/src/test/java/com/flowmind/business/generated/EntryApplicationControllerTest.java",
        changeType: "ADD",
        stagedSha256: "d".repeat(64),
        sizeBytes: 40,
        validationStatus: "PENDING",
        editedByUser: false,
      }, {
        relativePath: "frontend/src/modules/generated/example.ts",
        changeType: "ADD",
        stagedSha256: "f".repeat(64),
        sizeBytes: 11,
        validationStatus: "PENDING",
        editedByUser: false,
      }, {
        relativePath: "frontend/src/modules/generated/__tests__/EntryApplicationApply.test.ts",
        changeType: "ADD",
        stagedSha256: "e".repeat(64),
        sizeBytes: 11,
        validationStatus: "PENDING",
        editedByUser: false,
      }],
    };
  });

  afterEach(() => rmSync(root, { recursive: true, force: true }));

  it("decodes UTF-8 output correctly when a character spans process chunks", async () => {
    const result = await executeVerificationCommand({
      stage: "BACKEND_TESTS",
      executable: process.execPath,
      args: [
        "-e",
        "const value=Buffer.from('付款','utf8');process.stdout.write(value.subarray(0,1));setTimeout(()=>process.stdout.write(value.subarray(1)),10);",
      ],
      cwd: root,
      env: { ...process.env },
      shell: false,
      workspaceRoot: root,
      timeoutMs: 2_000,
      maxOutputBytes: 4_096,
    });

    expect(result.exitCode).toBe(0);
    expect(result.stdout).toBe("付款");
    expect(result.stdout).not.toContain("�");
  });

  it("overlays a disposable copy and runs only fixed commands", async () => {
    const commands: VerificationCommand[] = [];
    const workspaces: string[] = [];
    let generatedTsconfig: unknown;
    const previousMavenRepoLocal = process.env.AGENT_MAVEN_REPO_LOCAL;
    const mavenRepoLocal = join(root, "stable repo", "repository");
    process.env.AGENT_MAVEN_REPO_LOCAL = mavenRepoLocal;
    const execute = async (command: VerificationCommand): Promise<VerificationCommandResult> => {
      commands.push(command);
      workspaces.push(command.workspaceRoot);
      expect(command.shell).toBe(false);
      expect(command.env.JAVA_TOOL_OPTIONS).toBe(
        "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8",
      );
      expect(readFileSync(join(command.workspaceRoot, "frontend/src/api/generated/example.ts"), "utf8"))
        .toContain("staged");
      if (command.stage === "FRONTEND_TYPECHECK") {
        generatedTsconfig = JSON.parse(readFileSync(join(command.workspaceRoot, "frontend/tsconfig.generated.json"), "utf8"));
      }
      return { exitCode: 0, stdout: "ok", stderr: "", timedOut: false, cancelled: false };
    };

    const worker = new VerificationWorkerService();
    let result;
    try {
      result = await worker.run({
        generationId: "generation-worker",
        revision: 1,
        targetRoot: target,
        stagingDir: staging,
        contract,
        manifest,
        dataDir: join(root, "data"),
        execute,
      });
    } finally {
      if (previousMavenRepoLocal === undefined) delete process.env.AGENT_MAVEN_REPO_LOCAL;
      else process.env.AGENT_MAVEN_REPO_LOCAL = previousMavenRepoLocal;
    }

    const expectedCommands = process.platform === "win32"
      ? [
        { stage: "BACKEND_COMPILE", executable: process.env.COMSPEC || "cmd.exe", args: ["/d", "/s", "/c", `mvn.cmd -q "-Dmaven.repo.local=${mavenRepoLocal}" -DskipTests compile`] },
        { stage: "FRONTEND_TYPECHECK", executable: process.env.COMSPEC || "cmd.exe", args: ["/d", "/s", "/c", "npm.cmd exec -- vue-tsc -p tsconfig.generated.json --noEmit"] },
        { stage: "FRONTEND_BUILD", executable: process.env.COMSPEC || "cmd.exe", args: ["/d", "/s", "/c", "npm.cmd run build"] },
        { stage: "BACKEND_TESTS", executable: process.env.COMSPEC || "cmd.exe", args: ["/d", "/s", "/c", `mvn.cmd -q "-Dmaven.repo.local=${mavenRepoLocal}" test`] },
        { stage: "FRONTEND_TESTS", executable: process.env.COMSPEC || "cmd.exe", args: ["/d", "/s", "/c", "npm.cmd run test -- --run"] },
      ]
      : [
        { stage: "BACKEND_COMPILE", executable: "mvn", args: ["-q", `-Dmaven.repo.local=${mavenRepoLocal}`, "-DskipTests", "compile"] },
        { stage: "FRONTEND_TYPECHECK", executable: "npm", args: ["exec", "--", "vue-tsc", "-p", "tsconfig.generated.json", "--noEmit"] },
        { stage: "FRONTEND_BUILD", executable: "npm", args: ["run", "build"] },
        { stage: "BACKEND_TESTS", executable: "mvn", args: ["-q", `-Dmaven.repo.local=${mavenRepoLocal}`, "test"] },
        { stage: "FRONTEND_TESTS", executable: "npm", args: ["run", "test", "--", "--run"] },
      ];
    expect(commands.map(({ stage, executable, args }) => ({ stage, executable, args }))).toEqual(expectedCommands);
    expect(generatedTsconfig).toEqual({
      extends: "./tsconfig.json",
      include: [
        "src/api/generated/example.ts",
        "src/modules/generated/example.ts",
        "src/modules/generated/__tests__/EntryApplicationApply.test.ts",
      ],
    });
    expect(result.stages.every(({ status }) => status === "PASSED")).toBe(true);
    expect(readFileSync(join(target, "frontend/src/api/generated/example.ts"), "utf8")).toContain("target");
    expect(new Set(workspaces).size).toBe(1);
    expect(existsSync(workspaces[0])).toBe(false);
  });

  it("runs only frontend commands and reports backend stages as not applicable", async () => {
    const frontendContract = structuredClone(contract);
    frontendContract.contractVersion = "2.1";
    frontendContract.generationMode = "FRONTEND_ONLY";
    delete frontendContract.backend;
    const commands: string[] = [];
    const result = await new VerificationWorkerService().run({
      generationId: "generation-frontend-only",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract: frontendContract,
      manifest,
      dataDir: join(root, "data"),
      execute: async (command) => {
        commands.push(command.stage);
        return { exitCode: 0, stdout: "ok", stderr: "", timedOut: false, cancelled: false };
      },
    });

    expect(commands).toEqual(["FRONTEND_TYPECHECK", "FRONTEND_BUILD", "FRONTEND_TESTS"]);
    expect(result.stages.filter(({ stage }) => stage.startsWith("BACKEND"))).toEqual([
      expect.objectContaining({ stage: "BACKEND_COMPILE", status: "SKIPPED", hardGate: false, summary: expect.stringContaining("Not applicable") }),
      expect.objectContaining({ stage: "BACKEND_TESTS", status: "SKIPPED", hardGate: false, summary: expect.stringContaining("Not applicable") }),
    ]);
    expect(result.stages.filter(({ stage }) => stage.startsWith("FRONTEND")).every(({ status }) => status === "PASSED")).toBe(true);
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

  it("keeps Maven compiler symbol context and command metadata for repair", async () => {
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
        exitCode: command.stage === "BACKEND_COMPILE" ? 1 : 0,
        stdout: "",
        stderr: command.stage === "BACKEND_COMPILE"
          ? [
            `[ERROR] ${command.workspaceRoot}/backend/src/main/java/com/flowmind/business/generated/EntryApplicationService.java:[87,31] cannot find symbol`,
            "[ERROR]   symbol:   method getApplicationNo()",
            "[ERROR]   location: variable payload of type EntryApplicationRequest",
          ].join("\n")
          : "",
        timedOut: false,
        cancelled: false,
      }),
    });

    const compile = result.stages.find(({ stage }) => stage === "BACKEND_COMPILE")!;
    expect(compile.command).toContain("mvn");
    expect(compile.exitCode).toBe(1);
    expect(compile.diagnostics).toContainEqual(expect.objectContaining({
      code: "JAVA_COMPILER_ERROR",
      relativePath: "backend/src/main/java/com/flowmind/business/generated/EntryApplicationService.java",
      line: 87,
      column: 31,
      evidence: expect.stringContaining("symbol:   method getApplicationNo()"),
      command: expect.stringContaining("compile"),
      exitCode: 1,
    }));
    expect(compile.diagnostics[0].evidence).toContain("location: variable payload of type EntryApplicationRequest");
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
      relativePath: "backend/src/test/java/com/flowmind/business/generated/EntryApplicationServiceTest.java",
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

  it("returns a production UTF-8 response repair for corrupted Chinese controller text", async () => {
    const worker = new VerificationWorkerService();
    const result = await worker.run({
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      execute: async (command) => command.stage === "BACKEND_TESTS" ? {
        exitCode: 1,
        stdout: "",
        stderr: [
          "java.lang.AssertionError: Response content expected:<付款凭证为必传附件> but was:<?????????>",
          "\tat com.flowmind.business.generated.EntryApplicationControllerTest.rejectsMissingAttachment(EntryApplicationControllerTest.java:131)",
        ].join("\n"),
        timedOut: false,
        cancelled: false,
      } : { exitCode: 0, stdout: "ok", stderr: "", timedOut: false, cancelled: false },
    });

    const diagnostic = result.stages.find(({ stage }) => stage === "BACKEND_TESTS")!.diagnostics[0];
    expect(diagnostic).toEqual(expect.objectContaining({
      code: "JUNIT_TEST_FAILURE",
      actual: expect.stringContaining("?"),
      expected: expect.stringContaining("付款凭证为必传附件"),
      repairHint: expect.stringContaining("production controller's plain-text error response"),
      acceptedForms: expect.arrayContaining([expect.stringContaining("text/plain;charset=UTF-8")]),
    }));
    expect(diagnostic.evidence).toContain("付款凭证为必传附件");
  });

  it("keeps every JUnit and Vitest failure and maps locations to Manifest paths", async () => {
    const worker = new VerificationWorkerService();
    const result = await worker.run({
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      execute: async (command) => {
        if (command.stage === "BACKEND_TESTS") {
          return {
            exitCode: 1,
            stdout: "",
            stderr: [
              "java.lang.AssertionError: No value at JSON path $.instanceId",
              "\tat com.flowmind.business.generated.EntryApplicationControllerTest.shouldSubmitWithoutFiles(EntryApplicationControllerTest.java:170)",
              "org.mockito.exceptions.base.MockitoException: Cannot mock final class",
              "\tat com.flowmind.business.generated.EntryApplicationServiceTest.shouldMapTasks(EntryApplicationServiceTest.java:81)",
            ].join("\n"),
            timedOut: false,
            cancelled: false,
          };
        }
        if (command.stage === "FRONTEND_TESTS") {
          return {
            exitCode: 1,
            stdout: [
              "FAIL src/modules/generated/__tests__/EntryApplicationApply.test.ts > validates required fields",
              "AssertionError: expected false to be true",
              "❯ src/modules/generated/__tests__/EntryApplicationApply.test.ts:64:28",
              "FAIL src/modules/generated/__tests__/EntryApplicationApply.test.ts > rejects oversized files",
              "AssertionError: expected true to be false",
              "❯ src/modules/generated/__tests__/EntryApplicationApply.test.ts:286:20",
            ].join("\n"),
            stderr: "",
            timedOut: false,
            cancelled: false,
          };
        }
        return { exitCode: 0, stdout: "ok", stderr: "", timedOut: false, cancelled: false };
      },
    });

    const junit = result.stages.find(({ stage }) => stage === "BACKEND_TESTS")!.diagnostics;
    const vitest = result.stages.find(({ stage }) => stage === "FRONTEND_TESTS")!.diagnostics;
    expect(junit).toHaveLength(2);
    expect(junit.map(({ relativePath }) => relativePath)).toEqual([
      "backend/src/test/java/com/flowmind/business/generated/EntryApplicationControllerTest.java",
      "backend/src/test/java/com/flowmind/business/generated/EntryApplicationServiceTest.java",
    ]);
    expect(vitest).toHaveLength(2);
    expect(vitest.map(({ line }) => line)).toEqual([64, 286]);
    expect(vitest.every(({ relativePath }) => relativePath === "frontend/src/modules/generated/__tests__/EntryApplicationApply.test.ts"))
      .toBe(true);
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

  it("parses Mockito unnecessary stubbing failures at their generated test lines", async () => {
    const worker = new VerificationWorkerService();
    const result = await worker.run({
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      execute: async (command) => command.stage === "BACKEND_TESTS" ? {
        exitCode: 1,
        stdout: "",
        stderr: [
          "org.mockito.exceptions.misusing.UnnecessaryStubbingException: Unnecessary stubbings detected.",
          "  1. -> at com.flowmind.business.generated.EntryApplicationServiceTest.rejectsTooManyFiles(EntryApplicationServiceTest.java:222)",
        ].join("\n"),
        timedOut: false,
        cancelled: false,
      } : { exitCode: 0, stdout: "ok", stderr: "", timedOut: false, cancelled: false },
    });

    expect(result.stages.find(({ stage }) => stage === "BACKEND_TESTS")!.diagnostics[0]).toEqual(expect.objectContaining({
      code: "JUNIT_UNNECESSARY_STUBBING",
      relativePath: "backend/src/test/java/com/flowmind/business/generated/EntryApplicationServiceTest.java",
      line: 222,
      message: expect.stringContaining("UnnecessaryStubbingException"),
    }));
  });

  it("enters repair after the first hard command failure and skips later gates", async () => {
    const executed: string[] = [];
    const worker = new VerificationWorkerService();
    const result = await worker.run({
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      execute: async (command) => {
        executed.push(command.stage);
        return command.stage === "BACKEND_COMPILE"
          ? { exitCode: 1, stdout: "compile failed", stderr: "", timedOut: false, cancelled: false }
          : { exitCode: 0, stdout: "ok", stderr: "", timedOut: false, cancelled: false };
      },
    });

    expect(executed).not.toContain("BACKEND_TESTS");
    expect(executed).toEqual(["BACKEND_COMPILE"]);
    expect(result.stages.find(({ stage }) => stage === "BACKEND_TESTS")).toEqual(expect.objectContaining({
      status: "SKIPPED",
      blockedBy: ["BACKEND_COMPILE"],
    }));
    expect(result.stages.find(({ stage }) => stage === "FRONTEND_TYPECHECK")).toEqual(expect.objectContaining({
      status: "SKIPPED",
      blockedBy: ["BACKEND_COMPILE"],
    }));
  });

  it("runs only the selected command stages for targeted repair reverify", async () => {
    const executed: string[] = [];
    const worker = new VerificationWorkerService();
    const result = await worker.run({
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      stages: ["FRONTEND_TYPECHECK", "FRONTEND_BUILD", "FRONTEND_TESTS"],
      execute: async (command) => {
        executed.push(command.stage);
        return { exitCode: 0, stdout: "ok", stderr: "", timedOut: false, cancelled: false };
      },
    });

    expect(executed).toEqual(["FRONTEND_TYPECHECK", "FRONTEND_BUILD", "FRONTEND_TESTS"]);
    expect(result.stages.map(({ stage }) => stage)).toEqual(executed);
  });

  it("skips frontend build after typecheck hard-gate failure", async () => {
    const worker = new VerificationWorkerService();
    const typeError = "src/modules/generated/example.ts(65,24): error TS2307: Cannot find module '../../api/generated/entry-application'.";
    const result = await worker.run({
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      execute: async (command) => command.stage === "FRONTEND_TYPECHECK"
        ? { exitCode: 1, stdout: typeError, stderr: "", timedOut: false, cancelled: false }
        : { exitCode: 0, stdout: "ok", stderr: "", timedOut: false, cancelled: false },
    });
    const typecheck = result.stages.find(({ stage }) => stage === "FRONTEND_TYPECHECK")!;
    const build = result.stages.find(({ stage }) => stage === "FRONTEND_BUILD")!;

    expect(typecheck.status).toBe("FAILED");
    expect(build).toEqual(expect.objectContaining({
      status: "SKIPPED",
      blockedBy: ["FRONTEND_TYPECHECK"],
    }));
  });

  it("keeps unrelated external TypeScript diagnostics as pre-existing without blocking generated gates", async () => {
    const executed: string[] = [];
    const worker = new VerificationWorkerService();
    const result = await worker.run({
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      execute: async (command) => {
        executed.push(command.stage);
        return command.stage === "FRONTEND_TYPECHECK"
          ? {
            exitCode: 2,
            stdout: "src/views/LegacyView.vue(3,5): error TS2322: Type 'string' is not assignable to type 'number'.",
            stderr: "",
            timedOut: false,
            cancelled: false,
          }
          : { exitCode: 0, stdout: "ok", stderr: "", timedOut: false, cancelled: false };
      },
    });

    const typecheck = result.stages.find(({ stage }) => stage === "FRONTEND_TYPECHECK")!;
    expect(typecheck.status).toBe("PASSED");
    expect(typecheck.summary).toContain("Command exited with code 2");
    expect(typecheck.diagnostics).toContainEqual(expect.objectContaining({
      code: "TS2322",
      relativePath: "frontend/src/views/LegacyView.vue",
      scope: "PRE_EXISTING",
    }));
    expect(executed).toContain("FRONTEND_BUILD");
  });

  it("marks external diagnostics from files directly importing Manifest files as integration impact", async () => {
    write(target, "frontend/src/views/LegacyView.vue", [
      "<script setup lang=\"ts\">",
      "import { value } from \"../api/generated/example\";",
      "const legacyValue: number = value;",
      "</script>",
    ].join("\n"));
    const worker = new VerificationWorkerService();
    const result = await worker.run({
      generationId: "generation-worker",
      revision: 1,
      targetRoot: target,
      stagingDir: staging,
      contract,
      manifest,
      dataDir: join(root, "data"),
      execute: async (command) => command.stage === "FRONTEND_TYPECHECK"
        ? {
          exitCode: 2,
          stdout: "src/views/LegacyView.vue(3,7): error TS2322: Type 'string' is not assignable to type 'number'.",
          stderr: "",
          timedOut: false,
          cancelled: false,
        }
        : { exitCode: 0, stdout: "ok", stderr: "", timedOut: false, cancelled: false },
    });

    const typecheck = result.stages.find(({ stage }) => stage === "FRONTEND_TYPECHECK")!;
    expect(typecheck.status).toBe("PASSED");
    expect(typecheck.diagnostics).toContainEqual(expect.objectContaining({
      code: "TS2322",
      relativePath: "frontend/src/views/LegacyView.vue",
      scope: "INTEGRATION_IMPACT",
    }));
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
