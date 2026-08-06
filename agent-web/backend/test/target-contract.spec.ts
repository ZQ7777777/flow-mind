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

  it("accepts a complete v1.1 target and rejects targets outside configured roots", () => {
    const target = createGenerationTarget(parent);
    expect(new TargetContractService().validate(target).contract).toEqual(expect.objectContaining({
      contractVersion: "1.1",
      projectId: "flowmind-business-base",
    }));
    const outsideParent = mkdtempSync(join(tmpdir(), "flowmind-outside-"));
    try { expect(() => new TargetContractService().validate(createGenerationTarget(outsideParent))).toThrow(/outside AGENT_ALLOWED_TARGET_ROOTS/); }
    finally { rmSync(outsideParent, { recursive: true, force: true }); }
  });

  it("rejects missing, drifting, or unprotected generation API references", () => {
    const missing = createGenerationTarget(parent, "missing-api-reference");
    unlinkSync(join(missing, ".flowmind", "references", "platform-starter-0.1.0.md"));
    expect(() => new TargetContractService().validate(missing)).toThrow(/reference file is missing/);

    const drift = createGenerationTarget(parent, "drifting-api-reference");
    writeFileSync(join(drift, ".flowmind", "references", "platform-starter-0.1.0.md"), "changed", "utf8");
    expect(() => new TargetContractService().validate(drift)).toThrow(/protected file hash changed/);

    const unprotected = createGenerationTarget(parent, "unprotected-api-reference");
    const path = join(unprotected, ".flowmind", "generation-target.json");
    const contract = JSON.parse(readFileSync(path, "utf8"));
    contract.protectedFiles = contract.protectedFiles.filter((item: { path: string }) => !item.path.includes("platform-starter"));
    writeFileSync(path, JSON.stringify(contract), "utf8");
    expect(() => new TargetContractService().validate(unprotected)).toThrow(/must be readable and protected/);
  });

  it("rejects protected file drift and a missing trusted accessor", () => {
    const drift = createGenerationTarget(parent, "drift");
    writeFileSync(join(drift, "backend", "pom.xml"), "changed", "utf8");
    expect(() => new TargetContractService().validate(drift)).toThrow(/protected file hash changed/);
    const missing = createGenerationTarget(parent, "missing");
    unlinkSync(join(missing, "backend", "src", "main", "java", "com", "flowmind", "business", "security", "CurrentBusinessUserProvider.java"));
    expect(() => new TargetContractService().validate(missing)).toThrow(/reference file is missing/);
  });

  it("rejects widened output patterns", () => {
    const target = createGenerationTarget(parent);
    const path = join(target, ".flowmind", "generation-target.json");
    const contract = JSON.parse(readFileSync(path, "utf8"));
    contract.allowedOutputPatterns[0] = "backend/**/*";
    writeFileSync(path, JSON.stringify(contract), "utf8");
    expect(() => new TargetContractService().validate(target)).toThrow(/allowed output patterns/);
  });

  it("normalizes a legacy v1.0 target to the v1.1 runtime reference profile", () => {
    const target = createGenerationTarget(parent, "legacy");
    const path = join(target, ".flowmind", "generation-target.json");
    const contract = JSON.parse(readFileSync(path, "utf8"));
    contract.contractVersion = "1.0";
    delete contract.backend.apiReferences;
    contract.readableReferenceFiles = contract.readableReferenceFiles.filter((item: string) =>
      !item.includes("platform-starter-0.1.0.md") && !item.includes("CurrentBusinessUserProvider.java"),
    );
    writeFileSync(path, JSON.stringify(contract), "utf8");
    const normalized = new TargetContractService().validate(target).contract;
    expect(normalized.contractVersion).toBe("1.1");
    expect(normalized.backend.apiReferences).toEqual({
      platformRuntime: ".flowmind/references/platform-starter-0.1.0.md",
      trustedUserContext: "backend/src/main/java/com/flowmind/business/security/CurrentBusinessUserProvider.java",
    });
  });
});
