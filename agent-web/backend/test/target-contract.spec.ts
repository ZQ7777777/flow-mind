import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdtempSync, readFileSync, rmSync, unlinkSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { TargetContractService } from "../src/generation/target-contract.service.js";
import { createGenerationTarget } from "./generation-fixture.js";

describe("GenerationTargetContract preflight", () => {
  let parent: string;
  beforeEach(() => { parent = mkdtempSync(join(tmpdir(), "flowmind-target-")); process.env.AGENT_ALLOWED_TARGET_ROOTS = parent; });
  afterEach(() => { rmSync(parent, { recursive: true, force: true }); delete process.env.AGENT_ALLOWED_TARGET_ROOTS; });

  it("accepts a complete v1 target and rejects targets outside configured roots", () => {
    const target = createGenerationTarget(parent);
    expect(new TargetContractService().validate(target).contract.projectId).toBe("flowmind-business-base");
    const outsideParent = mkdtempSync(join(tmpdir(), "flowmind-outside-"));
    try { expect(() => new TargetContractService().validate(createGenerationTarget(outsideParent))).toThrow(/outside AGENT_ALLOWED_TARGET_ROOTS/); }
    finally { rmSync(outsideParent, { recursive: true, force: true }); }
  });

  it("rejects protected file drift and a missing trusted accessor", () => {
    const drift = createGenerationTarget(parent, "drift");
    writeFileSync(join(drift, "backend", "pom.xml"), "changed", "utf8");
    expect(() => new TargetContractService().validate(drift)).toThrow(/protected file hash changed/);
    const missing = createGenerationTarget(parent, "missing");
    unlinkSync(join(missing, "backend", "src", "main", "java", "com", "flowmind", "business", "security", "CurrentBusinessUserProvider.java"));
    expect(() => new TargetContractService().validate(missing)).toThrow(/trusted user accessor is missing/);
  });

  it("rejects widened output patterns", () => {
    const target = createGenerationTarget(parent);
    const path = join(target, ".flowmind", "generation-target.json");
    const contract = JSON.parse(readFileSync(path, "utf8"));
    contract.allowedOutputPatterns[0] = "backend/**/*";
    writeFileSync(path, JSON.stringify(contract), "utf8");
    expect(() => new TargetContractService().validate(target)).toThrow(/allowed output patterns/);
  });
});
