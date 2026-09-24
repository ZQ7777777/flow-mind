import { HttpStatus, Inject, Injectable } from "@nestjs/common";
import { createHash } from "node:crypto";
import { existsSync, lstatSync, readFileSync, realpathSync } from "node:fs";
import { dirname, isAbsolute, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import type {
  GenerationContextSnapshot,
  GenerationContextSummary,
  GenerationReferenceSnapshot,
  RequirementIrDraft,
} from "@flowmind/agent-contracts";
import { AgentError } from "../common/agent-error.js";
import { GenerationSkillRegistry } from "./generation-skill-registry.service.js";
import { TargetContractService, type ValidatedGenerationTarget } from "./target-contract.service.js";
import { extractTypeScriptContract } from "./typescript-contract-extractor.js";
import { routeGenerationContext } from "./generation-context-router.js";

const MAX_REFERENCE_BYTES = 1024 * 1024;
const GOLDEN_REQUIREMENT_PATH = "doc/example_process/仓单、国债（解）质押申请.md";

@Injectable()
export class GenerationContextRegistry {
  constructor(
    @Inject(GenerationSkillRegistry) private readonly skills: GenerationSkillRegistry,
    @Inject(TargetContractService) private readonly targets: TargetContractService,
  ) {}

  capture(target: ValidatedGenerationTarget, sessionId?: string, ir?: RequirementIrDraft): GenerationContextSnapshot {
    const skills = this.skills.loadRequired(sessionId);
    const frontendRoot = target.contract.frontend.rootDir;
    const targetPaths = [
      target.contract.frontend.sharedStartShell ? `${frontendRoot}/${target.contract.frontend.sharedStartShell}` : undefined,
      target.contract.frontend.sharedWorkflowTypes ? `${frontendRoot}/${target.contract.frontend.sharedWorkflowTypes}` : undefined,
      target.contract.frontend.apiReferences?.businessReferenceData,
      ...(target.contract.frontend.exampleReferenceFiles || []),
    ].filter((path): path is string => Boolean(path));
    const references: GenerationReferenceSnapshot[] = deduplicateReferences([
      this.readRepositoryReference(GOLDEN_REQUIREMENT_PATH, sessionId),
      ...targetPaths.map((relativePath) => {
        const content = this.targets.readReference(target, relativePath, sessionId);
        return {
          source: "TARGET" as const,
          relativePath,
          required: true,
          sha256: hash(content),
          content,
        };
      }),
    ]);
    const interfaces = references
      .filter(({ source, relativePath }) => source === "TARGET" && /\.(?:ts|vue)$/.test(relativePath))
      .map(({ relativePath, content }) => extractTypeScriptContract(relativePath, content))
      .filter(({ declarations }) => declarations.length > 0);
    const routing = ir ? routeGenerationContext(ir, skills, references) : undefined;
    const sha256 = hash([
      ...skills.map((skill) => `skill\0${skill.name}\0${skill.sha256}`),
      ...references.map((item) => `reference\0${item.source}\0${item.relativePath}\0${item.sha256}`),
      ...interfaces.map((item) => `interface\0${item.relativePath}\0${item.sha256}\0${JSON.stringify(item.declarations)}`),
      routing ? `routing\0${JSON.stringify(routing)}` : "",
    ].join("\n"));
    return { version: "1.0", sha256, skills, references, ...(routing ? { routing } : {}), interfaces };
  }

  readSkill(snapshot: GenerationContextSnapshot, skillName: string, path: string, sessionId?: string): string {
    return this.skills.read(snapshot.skills, skillName, path, sessionId);
  }

  readReference(snapshot: GenerationContextSnapshot, source: "REPOSITORY" | "TARGET", path: string, sessionId?: string): string {
    const item = snapshot.references.find((reference) => reference.source === source && reference.relativePath === normalize(path));
    if (!item) throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_GENERATION_CONTEXT_FILE_NOT_FOUND", "generation context file was not found", sessionId);
    return item.content;
  }

  summary(snapshot: GenerationContextSnapshot): GenerationContextSummary {
    return {
      sha256: snapshot.sha256,
      skills: snapshot.skills.map(({ name, sha256 }) => ({ name, sha256 })),
      references: snapshot.references.map(({ source, relativePath, sha256 }) => ({ source, relativePath, sha256 })),
      routing: snapshot.routing,
      interfaces: snapshot.interfaces,
    };
  }

  private readRepositoryReference(relativePath: string, sessionId?: string): GenerationReferenceSnapshot {
    const root = realpathSync.native(resolve(process.env.AGENT_GENERATION_REFERENCE_ROOT
      || resolve(dirname(fileURLToPath(import.meta.url)), "../../../..")));
    const normalized = normalize(relativePath);
    const candidate = resolve(root, ...normalized.split("/"));
    assertContained(root, candidate, sessionId);
    if (!existsSync(candidate) || !lstatSync(candidate).isFile() || lstatSync(candidate).isSymbolicLink()) {
      throw new AgentError(HttpStatus.SERVICE_UNAVAILABLE, "AGENT_GENERATION_REFERENCE_MISSING", `required generation reference is missing: ${normalized}`, sessionId);
    }
    assertContained(root, realpathSync.native(candidate), sessionId);
    const bytes = readFileSync(candidate);
    if (bytes.length > MAX_REFERENCE_BYTES) throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_REFERENCE_TOO_LARGE", `generation reference is too large: ${normalized}`, sessionId);
    const content = bytes.toString("utf8");
    if (Buffer.from(content, "utf8").compare(bytes) !== 0) throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_REFERENCE_ENCODING_INVALID", `generation reference must be UTF-8: ${normalized}`, sessionId);
    return { source: "REPOSITORY", relativePath: normalized, required: true, sha256: hash(bytes), content };
  }
}

function deduplicateReferences(items: GenerationReferenceSnapshot[]): GenerationReferenceSnapshot[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    const key = `${item.source}:${item.relativePath}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function normalize(value: string): string {
  const normalized = value.replace(/\\/g, "/");
  if (!normalized || normalized.startsWith("/") || isAbsolute(normalized) || normalized.split("/").some((part) => !part || part === "." || part === "..")) {
    throw new Error("generation context path must be normalized and relative");
  }
  return normalized;
}

function assertContained(root: string, candidate: string, sessionId?: string): void {
  const rel = relative(root, candidate);
  if (rel === "" || (!rel.startsWith(`..${sep}`) && rel !== ".." && !isAbsolute(rel))) return;
  throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_GENERATION_REFERENCE_FORBIDDEN", "generation reference escapes the configured root", sessionId);
}

function hash(content: Buffer | string): string {
  return createHash("sha256").update(content).digest("hex");
}
