import { HttpStatus, Injectable } from "@nestjs/common";
import AjvModule from "ajv";
import { createHash } from "node:crypto";
import { existsSync, lstatSync, readFileSync, realpathSync } from "node:fs";
import { isAbsolute, relative, resolve, sep } from "node:path";
import {
  generationTargetContractSchema,
  type GenerationTargetContract,
} from "@flowmind/agent-contracts";
import { AgentError } from "../common/agent-error.js";
import { loadConfig } from "../config.js";
import { REQUIRED_OUTPUT_PATTERNS } from "./generation.constants.js";
import { assertInside, assertNoLinkInExistingPath, canonicalExistingDirectory, normalizeRelativePath } from "./path-safety.js";

export interface ValidatedGenerationTarget {
  targetRoot: string;
  contract: GenerationTargetContract;
}

export const DEFAULT_PLATFORM_API_REFERENCE = ".flowmind/references/platform-starter-0.1.0.md";

export function normalizeGenerationContract(contract: GenerationTargetContract): GenerationTargetContract {
  if (!contract.backend) return contract;
  if (contract.contractVersion === "1.1" && contract.backend.apiReferences) return contract;
  const trustedUserContext = `${contract.backend.rootDir}/src/main/java/${contract.backend.trustedUserContext.accessorType.replace(/\./g, "/")}.java`;
  return {
    ...contract,
    contractVersion: "1.1",
    backend: {
      ...contract.backend,
      apiReferences: {
        platformRuntime: DEFAULT_PLATFORM_API_REFERENCE,
        trustedUserContext,
      },
    },
    readableReferenceFiles: [...new Set([
      ...contract.readableReferenceFiles,
      DEFAULT_PLATFORM_API_REFERENCE,
      trustedUserContext,
    ])],
  };
}

export function apiReferencePaths(contract: GenerationTargetContract): { platformRuntime: string; trustedUserContext: string } {
  const references = normalizeGenerationContract(contract).backend?.apiReferences;
  if (!references) throw new Error("backend API references are unavailable for a frontend-only generation target");
  return references;
}

export function declaredApiReferencePaths(contract: GenerationTargetContract): string[] {
  const normalized = normalizeGenerationContract(contract);
  return [
    normalized.backend?.apiReferences?.platformRuntime,
    normalized.backend?.apiReferences?.trustedUserContext,
    normalized.frontend.apiReferences?.businessReferenceData,
  ].filter((path): path is string => Boolean(path));
}

@Injectable()
export class TargetContractService {
  private readonly config = loadConfig();
  private readonly validateSchema = new (AjvModule as any)({ allErrors: true, strict: false }).compile(generationTargetContractSchema);

  validate(targetRootInput: string, sessionId?: string): ValidatedGenerationTarget {
    if (!targetRootInput?.trim()) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_ROOT_REQUIRED", "targetRoot is required before code generation", sessionId);
    }
    if (!this.config.allowedTargetRoots.length) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_ROOT_NOT_ALLOWED", "AGENT_ALLOWED_TARGET_ROOTS is not configured", sessionId);
    }
    const rawTargetRoot = resolve(targetRootInput.trim());
    const targetRoot = canonicalExistingDirectory(rawTargetRoot, sessionId);
    const allowed = this.config.allowedTargetRoots.some((configuredRoot) => {
      if (!existsSync(configuredRoot) || lstatSync(configuredRoot).isSymbolicLink()) return false;
      const realAllowed = realpathSync.native(configuredRoot);
      const rel = relative(realAllowed, targetRoot);
      const contained = rel === "" || (!rel.startsWith(`..${sep}`) && rel !== ".." && !isAbsolute(rel));
      if (contained) {
        const rawAllowed = resolve(configuredRoot);
        const rawRel = relative(rawAllowed, rawTargetRoot);
        const rawContained = rawRel === "" || (!rawRel.startsWith(`..${sep}`) && rawRel !== ".." && !isAbsolute(rawRel));
        assertNoLinkInExistingPath(rawContained ? rawAllowed : realAllowed, rawContained ? rawTargetRoot : targetRoot, sessionId);
      }
      return contained;
    });
    if (!allowed) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_TARGET_ROOT_NOT_ALLOWED", "targetRoot is outside AGENT_ALLOWED_TARGET_ROOTS", sessionId);
    }

    const contractPath = resolve(targetRoot, ".flowmind", "generation-target.json");
    assertInside(targetRoot, contractPath, sessionId);
    assertNoLinkInExistingPath(targetRoot, contractPath, sessionId);
    if (!existsSync(contractPath) || !lstatSync(contractPath).isFile()) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_CONTRACT_MISSING", "generation target contract is missing", sessionId);
    }
    let contract: GenerationTargetContract;
    try {
      contract = JSON.parse(readFileSync(contractPath, "utf8")) as GenerationTargetContract;
    } catch {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_CONTRACT_INVALID", "generation target contract is not valid JSON", sessionId);
    }
    if (contract.contractVersion !== "2.1" || contract.generationMode !== "FRONTEND_ONLY" || contract.backend) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_CONTRACT_UPGRADE_REQUIRED", "generation target must use contractVersion 2.1 and generationMode FRONTEND_ONLY without backend configuration", sessionId);
    }
    if (!this.validateSchema(contract)) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_CONTRACT_INVALID", "generation target contract does not match the supported schema", sessionId, {
        errors: this.validateSchema.errors || [],
      });
    }
    const businessReference = contract.frontend.apiReferences?.businessReferenceData;
    if (businessReference && (!contract.readableReferenceFiles.includes(businessReference)
      || !contract.protectedFiles.some((item) => item.path === businessReference))) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_CONTRACT_INVALID", "business reference-data API must be readable and protected", sessionId);
    }
    if (contract.allowedOutputPatterns.length !== REQUIRED_OUTPUT_PATTERNS.length
      || REQUIRED_OUTPUT_PATTERNS.some((pattern, index) => contract.allowedOutputPatterns[index] !== pattern)) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_CONTRACT_INVALID", "allowed output patterns do not match the Flow Mind boundary", sessionId);
    }

    for (const file of contract.readableReferenceFiles) this.requireFile(targetRoot, normalizeRelativePath(file, sessionId), sessionId);
    for (const protectedFile of contract.protectedFiles) {
      const relativePath = normalizeRelativePath(protectedFile.path, sessionId);
      const content = this.requireFile(targetRoot, relativePath, sessionId);
      if (sha256(content) !== protectedFile.sha256.toLowerCase()) {
        throw new AgentError(HttpStatus.CONFLICT, "AGENT_TARGET_CONTRACT_DRIFT", `protected file hash changed: ${relativePath}`, sessionId);
      }
    }

    const routePath = `${contract.frontend.rootDir}/${contract.frontend.routeRegistry}`;
    const interfacePaths = [
      `${contract.frontend.rootDir}/${contract.frontend.sharedStartShell}`,
      `${contract.frontend.rootDir}/${contract.frontend.sharedWorkflowTypes}`,
    ];
    for (const path of interfacePaths) {
      if (!contract.readableReferenceFiles.includes(path)) {
        throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_CONTRACT_INVALID", `shared TypeScript contract must be readable: ${path}`, sessionId);
      }
    }
    this.requireFile(targetRoot, normalizeRelativePath(routePath, sessionId), sessionId, "generated route registry");
    this.requireFile(targetRoot, `${contract.frontend.rootDir}/${contract.frontend.sharedStartShell}`, sessionId, "shared workflow start shell");
    this.requireFile(targetRoot, `${contract.frontend.rootDir}/${contract.frontend.sharedWorkflowTypes}`, sessionId, "shared workflow types");
    for (const path of contract.frontend.exampleReferenceFiles || []) {
      if (!contract.readableReferenceFiles.includes(path)) {
        throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_CONTRACT_INVALID", `example reference must be readable: ${path}`, sessionId);
      }
      this.requireFile(targetRoot, path, sessionId, "example reference");
    }
    const packageJson = JSON.parse(this.requireFile(targetRoot, `${contract.frontend.rootDir}/package.json`, sessionId).toString("utf8")) as { scripts?: Record<string, string> };
    if (!["typecheck", "test", "build"].every((name) => typeof packageJson.scripts?.[name] === "string")) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_PREREQUISITE_MISSING", "frontend package.json lacks stable verification scripts", sessionId);
    }
    return { targetRoot, contract };
  }

  readReference(target: ValidatedGenerationTarget, relativePathInput: string, sessionId?: string): string {
    const relativePath = normalizeRelativePath(relativePathInput, sessionId);
    if (!target.contract.readableReferenceFiles.includes(relativePath)) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_GENERATION_REFERENCE_FORBIDDEN", "reference file is not readable by the generator", sessionId);
    }
    return this.requireFile(target.targetRoot, relativePath, sessionId).toString("utf8");
  }

  private requireFile(root: string, relativePath: string, sessionId?: string, label = "reference file"): Buffer {
    const candidate = resolve(root, relativePath);
    assertInside(root, candidate, sessionId);
    assertNoLinkInExistingPath(root, candidate, sessionId);
    if (!existsSync(candidate) || !lstatSync(candidate).isFile()) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_PREREQUISITE_MISSING", `${label} is missing: ${relativePath}`, sessionId);
    }
    return readFileSync(candidate);
  }
}

export function sha256(content: Buffer | string): string {
  return createHash("sha256").update(content).digest("hex");
}
