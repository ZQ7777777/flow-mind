#!/usr/bin/env node
/**
 * Synchronize protectedFiles sha256 in .flowmind/generation-target.json
 * with the actual on-disk content of those files.
 *
 * The agent-web backend (generation/target-contract.service.ts) validates a
 * generation contract by reading each protected file with readFileSync and
 * hashing the raw bytes. This script uses the exact same algorithm, so the
 * hashes it writes are the ones the backend will accept.
 *
 * Usage:
 *   node scripts/sync-generation-target.mjs           # rewrite all drifted contracts
 *   node scripts/sync-generation-target.mjs --staged  # pre-commit: only touch contracts
 *                                                      #   involved in this commit, then
 *                                                      #   `git add` them
 *   node scripts/sync-generation-target.mjs --check   # report drift only, write nothing,
 *                                                      #   exit 1 if any drift found
 */
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";

const args = new Set(process.argv.slice(2));
const STAGED = args.has("--staged");
const CHECK = args.has("--check");

const repoRoot = execFileSync("git", ["rev-parse", "--show-toplevel"], { encoding: "utf8" }).trim();
const toPosix = (p) => relative(repoRoot, p).split("\\").join("/");

// Substrings that mark a generation-target.json as a generated fixture rather
// than a source-of-truth contract — never touch these.
const EXCLUDE = ["agent-web/data", "rollback-backups", "verification-workspaces", "node_modules"];
const SKIP_DIRS = new Set([".git", "node_modules", "target", "dist", "build", ".m2", ".m2-agent"]);

function sha256(buffer) {
  return createHash("sha256").update(buffer).digest("hex");
}

function findContracts(dir, acc = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      findContracts(full, acc);
    } else if (entry.name === "generation-target.json" && dirname(full).split("\\").join("/").endsWith(".flowmind")) {
      const rel = toPosix(full);
      if (EXCLUDE.some((s) => rel.includes(s))) continue;
      acc.push(full);
    }
  }
  return acc;
}

let stagedCache = null;
function stagedFiles() {
  if (stagedCache) return stagedCache;
  const out = execFileSync("git", ["diff", "--cached", "--name-only"], { encoding: "utf8", cwd: repoRoot });
  stagedCache = new Set(out.split("\n").map((s) => s.trim()).filter(Boolean));
  return stagedCache;
}

let driftCount = 0;
const updated = [];

for (const contractPath of findContracts(repoRoot)) {
  const contractRel = toPosix(contractPath);
  let contract;
  try {
    contract = JSON.parse(readFileSync(contractPath, "utf8"));
  } catch {
    console.warn(`skip ${contractRel} (not valid JSON)`);
    continue;
  }
  const protectedFiles = Array.isArray(contract.protectedFiles) ? contract.protectedFiles : [];
  if (protectedFiles.length === 0) continue;

  // protectedFile.path is relative to the target root (the parent of .flowmind),
  // matching how the backend resolves it.
  const targetRoot = dirname(dirname(contractPath));
  const protectedRels = protectedFiles.map((f) => toPosix(join(targetRoot, f.path)));

  if (STAGED) {
    const staged = stagedFiles();
    const relevant = staged.has(contractRel) || protectedRels.some((p) => staged.has(p));
    if (!relevant) continue; // unrelated commit — leave this contract alone
  }

  const text = readFileSync(contractPath, "utf8");
  const endsWithNewline = text.endsWith("\n");
  let changed = false;
  const lines = [];

  for (let i = 0; i < protectedFiles.length; i++) {
    const f = protectedFiles[i];
    const abs = join(targetRoot, f.path);
    if (!existsSync(abs)) {
      lines.push(`  ! missing  ${f.path}`);
      continue;
    }
    const actual = sha256(readFileSync(abs));
    const before = (f.sha256 || "").toLowerCase();
    if (actual !== before) {
      driftCount++;
      lines.push(`  ~ update   ${f.path}  ${before ? before.slice(0, 8) : "<none>"} -> ${actual.slice(0, 8)}`);
      if (!CHECK) {
        f.sha256 = actual;
        changed = true;
      }
    } else {
      lines.push(`  = ok       ${f.path}`);
    }
  }

  if (changed) {
    writeFileSync(contractPath, JSON.stringify(contract, null, 2) + (endsWithNewline ? "\n" : ""));
    updated.push(contractRel);
    if (STAGED) execFileSync("git", ["add", contractRel], { cwd: repoRoot, stdio: "inherit" });
  }
  const tag = CHECK ? (lines.some((l) => l.startsWith("  ~")) ? "DRIFT" : "ok") : (changed ? "updated" : "ok");
  console.log(`${tag}  ${contractRel}`);
  console.log(lines.join("\n"));
}

if (updated.length) {
  console.log(`\nrewrote ${updated.length} contract(s): ${updated.join(", ")}`);
}
if (CHECK && driftCount > 0) {
  console.log(`\n${driftCount} protected file hash(es) drifted — run without --check to fix.`);
  process.exit(1);
}
if (!CHECK && driftCount === 0) {
  console.log("\nall protected hashes in sync.");
}
