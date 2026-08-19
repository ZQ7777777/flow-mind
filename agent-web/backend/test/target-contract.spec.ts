import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createHash } from "node:crypto";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, unlinkSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { TargetContractService } from "../src/generation/target-contract.service.js";
import { createGenerationTarget } from "./generation-fixture.js";

describe("GenerationTargetContract 2.1 preflight", () => {
  let parent: string;
  beforeEach(() => { parent = mkdtempSync(join(tmpdir(), "flowmind-target-")); process.env.AGENT_ALLOWED_TARGET_ROOTS = parent; });
  afterEach(() => { rmSync(parent, { recursive: true, force: true }); delete process.env.AGENT_ALLOWED_TARGET_ROOTS; });

  it("accepts a complete frontend-only target and rejects targets outside configured roots", () => {
    const target = createGenerationTarget(parent);
    expect(new TargetContractService().validate(target).contract).toEqual(expect.objectContaining({
      contractVersion: "2.1", generationMode: "FRONTEND_ONLY", projectId: "flowmind-business-base",
    }));
    expect(new TargetContractService().validate(target).contract.backend).toBeUndefined();
    const outsideParent = mkdtempSync(join(tmpdir(), "flowmind-outside-"));
    try { expect(() => new TargetContractService().validate(createGenerationTarget(outsideParent))).toThrow(/outside AGENT_ALLOWED_TARGET_ROOTS/); }
    finally { rmSync(outsideParent, { recursive: true, force: true }); }
  });

  it.each(["1.0", "1.1", "2.0"])("rejects obsolete contract %s with an upgrade error", (version) => {
    const target = createGenerationTarget(parent, `legacy-${version.replace(".", "-")}`);
    const path = join(target, ".flowmind", "generation-target.json");
    const contract = JSON.parse(readFileSync(path, "utf8"));
    contract.contractVersion = version;
    if (version === "2.0") contract.generationMode = "FRONTEND_FORM_ONLY";
    else delete contract.generationMode;
    writeFileSync(path, JSON.stringify(contract), "utf8");
    expect(() => new TargetContractService().validate(target)).toThrow(/contractVersion 2.1/);
  });

  it("requires every golden example to be readable and present", () => {
    const target = createGenerationTarget(parent, "missing-example");
    unlinkSync(join(target, "frontend", "src", "modules", "generated", "sample", "BusinessForm.vue"));
    expect(() => new TargetContractService().validate(target)).toThrow(/reference file is missing/);

    const unreadable = createGenerationTarget(parent, "unreadable-example");
    const path = join(unreadable, ".flowmind", "generation-target.json");
    const contract = JSON.parse(readFileSync(path, "utf8"));
    contract.readableReferenceFiles = contract.readableReferenceFiles.filter((item: string) => !item.endsWith("sample/Apply.vue"));
    writeFileSync(path, JSON.stringify(contract), "utf8");
    expect(() => new TargetContractService().validate(unreadable)).toThrow(/example reference must be readable/);
  });

  it("validates an optional protected read-only business API reference", () => {
    const target = createGenerationTarget(parent, "frontend-api-reference");
    const relativePath = ".flowmind/references/business-reference-data-v1.md";
    const referencePath = join(target, ...relativePath.split("/"));
    mkdirSync(join(referencePath, ".."), { recursive: true });
    const reference = "GET /api/reference-data/futures-products";
    writeFileSync(referencePath, reference, "utf8");
    const contractPath = join(target, ".flowmind", "generation-target.json");
    const contract = JSON.parse(readFileSync(contractPath, "utf8"));
    contract.frontend.apiReferences = { businessReferenceData: relativePath };
    contract.readableReferenceFiles.push(relativePath);
    contract.protectedFiles.push({ path: relativePath, sha256: createHash("sha256").update(reference).digest("hex") });
    writeFileSync(contractPath, JSON.stringify(contract), "utf8");
    expect(new TargetContractService().validate(target).contract.frontend.apiReferences)
      .toEqual({ businessReferenceData: relativePath });
    writeFileSync(referencePath, "changed", "utf8");
    expect(() => new TargetContractService().validate(target)).toThrow(/protected file hash changed/);
  });

  it("rejects protected drift and widened output patterns", () => {
    const drift = createGenerationTarget(parent, "drift");
    writeFileSync(join(drift, "frontend", "package.json"), "{}", "utf8");
    expect(() => new TargetContractService().validate(drift)).toThrow(/protected file hash changed/);

    const target = createGenerationTarget(parent, "widened");
    const path = join(target, ".flowmind", "generation-target.json");
    const contract = JSON.parse(readFileSync(path, "utf8"));
    contract.allowedOutputPatterns[0] = "backend/**/*";
    writeFileSync(path, JSON.stringify(contract), "utf8");
    expect(() => new TargetContractService().validate(target)).toThrow(/allowed output patterns/);
  });
});
