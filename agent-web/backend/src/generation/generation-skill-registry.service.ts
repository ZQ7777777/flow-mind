import { HttpStatus, Injectable } from "@nestjs/common";
import { createHash } from "node:crypto";
import { existsSync, lstatSync, readFileSync, readdirSync, realpathSync } from "node:fs";
import { isAbsolute, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import type { GenerationSkillSnapshot } from "@flowmind/agent-contracts";
import { AgentError } from "../common/agent-error.js";

const REQUIRED_SKILL = "flowmind-business-generation";
const SKILL_ALLOWLIST = new Set([REQUIRED_SKILL]);
const MAX_SKILL_FILE_BYTES = 256 * 1024;

@Injectable()
export class GenerationSkillRegistry {
  private readonly root = resolve(process.env.AGENT_GENERATION_SKILL_ROOT
    || resolve(fileURLToPath(new URL(".", import.meta.url)), "../../../skills"));

  loadRequired(sessionId?: string): GenerationSkillSnapshot[] {
    return [this.load(REQUIRED_SKILL, true, 100, sessionId)];
  }

  read(
    snapshots: GenerationSkillSnapshot[],
    skillName: string,
    relativePathInput: string,
    sessionId?: string,
  ): string {
    if (!SKILL_ALLOWLIST.has(skillName)) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_GENERATION_SKILL_FORBIDDEN", "generation skill is not allowlisted", sessionId);
    }
    const relativePath = normalizeSkillPath(relativePathInput, sessionId);
    if (relativePath !== "SKILL.md" && !relativePath.startsWith("references/")) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_GENERATION_SKILL_REFERENCE_FORBIDDEN", "only SKILL.md and references are readable", sessionId);
    }
    const snapshot = snapshots.find((item) => item.name === skillName);
    const file = snapshot?.files.find((item) => item.relativePath === relativePath);
    if (!file) {
      throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_GENERATION_SKILL_FILE_NOT_FOUND", "skill snapshot file was not found", sessionId);
    }
    return file.content;
  }

  private load(name: string, required: boolean, priority: number, sessionId?: string): GenerationSkillSnapshot {
    if (!SKILL_ALLOWLIST.has(name)) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_GENERATION_SKILL_FORBIDDEN", "generation skill is not allowlisted", sessionId);
    }
    const skillRoot = resolve(this.root, name);
    assertContained(this.root, skillRoot, sessionId);
    if (!existsSync(skillRoot) || !lstatSync(skillRoot).isDirectory() || lstatSync(skillRoot).isSymbolicLink()) {
      throw new AgentError(HttpStatus.SERVICE_UNAVAILABLE, "AGENT_GENERATION_SKILL_MISSING", `required generation skill is missing: ${name}`, sessionId);
    }
    assertContained(realpathSync.native(this.root), realpathSync.native(skillRoot), sessionId);
    const relativePaths = collectFiles(skillRoot, skillRoot, sessionId).sort();
    const files = relativePaths.map((relativePath) => {
      const absolutePath = resolve(skillRoot, ...relativePath.split("/"));
      const stat = lstatSync(absolutePath);
      if (stat.size > MAX_SKILL_FILE_BYTES) {
        throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_SKILL_FILE_TOO_LARGE", `skill file is too large: ${relativePath}`, sessionId);
      }
      const bytes = readFileSync(absolutePath);
      const content = bytes.toString("utf8");
      if (Buffer.from(content, "utf8").compare(bytes) !== 0) {
        throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_SKILL_ENCODING_INVALID", `skill file must be UTF-8: ${relativePath}`, sessionId);
      }
      return { relativePath, sha256: hash(bytes), content };
    });
    const skillFile = files.find((file) => file.relativePath === "SKILL.md");
    if (!skillFile) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_SKILL_INVALID", "SKILL.md is missing", sessionId);
    }
    validateFrontmatter(skillFile.content, name, sessionId);
    return {
      name,
      source: "REPOSITORY",
      required,
      priority,
      sha256: hash(files.map((file) => `${file.relativePath}\0${file.sha256}`).join("\n")),
      files,
    };
  }
}

function collectFiles(root: string, current: string, sessionId?: string): string[] {
  const result: string[] = [];
  for (const entry of readdirSync(current, { withFileTypes: true })) {
    const absolute = resolve(current, entry.name);
    assertContained(root, absolute, sessionId);
    if (entry.isSymbolicLink()) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_SKILL_LINK_FORBIDDEN", "symbolic links are forbidden in generation skills", sessionId);
    }
    if (entry.isDirectory()) result.push(...collectFiles(root, absolute, sessionId));
    else if (entry.isFile()) result.push(relative(root, absolute).split(sep).join("/"));
  }
  return result;
}

function validateFrontmatter(content: string, expectedName: string, sessionId?: string): void {
  const match = content.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n/);
  if (!match) throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_SKILL_INVALID", "SKILL.md frontmatter is invalid", sessionId);
  const entries = match[1].split(/\r?\n/).filter(Boolean).map((line) => {
    const separator = line.indexOf(":");
    return separator < 1 ? [line, ""] : [line.slice(0, separator).trim(), line.slice(separator + 1).trim()];
  });
  const keys = entries.map(([key]) => key);
  const values = Object.fromEntries(entries);
  if (keys.length !== 2 || !keys.includes("name") || !keys.includes("description")
    || values.name !== expectedName || !values.description) {
    throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_SKILL_INVALID", "SKILL.md must contain only matching name and non-empty description", sessionId);
  }
}

function normalizeSkillPath(value: string, sessionId?: string): string {
  const normalized = value.replace(/\\/g, "/");
  if (!normalized || isAbsolute(value) || normalized.startsWith("/")
    || normalized.split("/").some((part) => part === ".." || part === "." || !part)) {
    throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_SKILL_PATH_INVALID", "skill path must be a normalized relative path", sessionId);
  }
  return normalized;
}

function assertContained(root: string, candidate: string, sessionId?: string): void {
  const rel = relative(root, candidate);
  if (rel === "" || (!rel.startsWith(`..${sep}`) && rel !== ".." && !isAbsolute(rel))) return;
  throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_GENERATION_SKILL_PATH_FORBIDDEN", "skill path escapes the configured root", sessionId);
}

function hash(value: Buffer | string): string {
  return createHash("sha256").update(value).digest("hex");
}
