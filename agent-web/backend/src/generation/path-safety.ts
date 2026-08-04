import { HttpStatus } from "@nestjs/common";
import { existsSync, lstatSync, realpathSync } from "node:fs";
import { isAbsolute, relative, resolve, sep } from "node:path";
import { AgentError } from "../common/agent-error.js";

export function normalizeRelativePath(value: string, sessionId?: string): string {
  if (typeof value !== "string" || !value.trim() || value.includes("\0") || value.includes("\\")) {
    throw pathError(sessionId);
  }
  const normalized = value.replace(/\/+/g, "/");
  if (isAbsolute(normalized) || normalized.startsWith("/") || normalized.startsWith("//") || /^[A-Za-z]:/.test(normalized)) {
    throw pathError(sessionId);
  }
  const segments = normalized.split("/");
  if (segments.some((segment) => !segment || segment === "." || segment === "..")) throw pathError(sessionId);
  return segments.join("/");
}

export function assertInside(root: string, candidate: string, sessionId?: string): void {
  const rel = relative(root, candidate);
  if (rel === "" || (!rel.startsWith(`..${sep}`) && rel !== ".." && !isAbsolute(rel))) return;
  throw pathError(sessionId);
}

export function assertNoLinkInExistingPath(root: string, candidate: string, sessionId?: string): void {
  assertInside(root, candidate, sessionId);
  const rel = relative(root, candidate);
  let current = root;
  for (const part of rel.split(sep).filter(Boolean)) {
    current = resolve(current, part);
    if (!existsSync(current)) break;
    if (lstatSync(current).isSymbolicLink()) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_REPARSE_POINT", "symbolic links and reparse points are not allowed", sessionId);
    }
  }
}

export function canonicalExistingDirectory(value: string, sessionId?: string): string {
  if (!isAbsolute(value) || value.startsWith("\\\\") || value.split(/[\\/]/).includes("..")) throw pathError(sessionId);
  if (!existsSync(value) || !lstatSync(value).isDirectory()) {
    throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_ROOT_NOT_FOUND", "targetRoot must be an existing directory", sessionId);
  }
  if (lstatSync(value).isSymbolicLink()) {
    throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_TARGET_REPARSE_POINT", "symbolic links and reparse points are not allowed", sessionId);
  }
  return realpathSync.native(resolve(value));
}

function pathError(sessionId?: string): AgentError {
  return new AgentError(HttpStatus.BAD_REQUEST, "AGENT_GENERATION_PATH_FORBIDDEN", "path is outside the allowed generation boundary", sessionId);
}
