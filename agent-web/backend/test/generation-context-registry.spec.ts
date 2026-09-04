import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createHash } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { GenerationContextRegistry } from "../src/generation/generation-context-registry.service.js";
import { GenerationSkillRegistry } from "../src/generation/generation-skill-registry.service.js";
import { TargetContractService } from "../src/generation/target-contract.service.js";
import { createGenerationTarget } from "./generation-fixture.js";
import { ENTRY_APPLICATION_REQUIREMENT, WAREHOUSE_PLEDGE_REQUIREMENT } from "@flowmind/agent-contracts";
import { deriveRequirementIr } from "../src/requirement/requirement-ir.js";

describe("GenerationContextRegistry", () => {
  let parent: string;
  beforeEach(() => { parent = mkdtempSync(join(tmpdir(), "flowmind-context-")); process.env.AGENT_ALLOWED_TARGET_ROOTS = parent; });
  afterEach(() => { rmSync(parent, { recursive: true, force: true }); delete process.env.AGENT_ALLOWED_TARGET_ROOTS; });

  it("captures the skill, source process, target examples and stable SHA-256 values", () => {
    const targetRoot = createGenerationTarget(parent);
    const targets = new TargetContractService();
    const registry = new GenerationContextRegistry(new GenerationSkillRegistry(), targets);
    const target = targets.validate(targetRoot);
    const snapshot = registry.capture(target);

    expect(snapshot.skills).toContainEqual(expect.objectContaining({ name: "flowmind-business-generation" }));
    expect(snapshot.references).toContainEqual(expect.objectContaining({
      source: "REPOSITORY", relativePath: "doc/example_process/仓单、国债（解）质押申请.md",
    }));
    for (const relativePath of target.contract.frontend.exampleReferenceFiles || []) {
      const item = snapshot.references.find((reference) => reference.source === "TARGET" && reference.relativePath === relativePath)!;
      expect(item.sha256).toBe(createHash("sha256").update(item.content).digest("hex"));
    }
    expect(snapshot.sha256).toMatch(/^[a-f0-9]{64}$/);
    expect(JSON.stringify(registry.summary(snapshot))).not.toContain("sample form");
  });

  it("keeps captured target content immutable after the sample changes", () => {
    const targetRoot = createGenerationTarget(parent);
    const targets = new TargetContractService();
    const registry = new GenerationContextRegistry(new GenerationSkillRegistry(), targets);
    const snapshot = registry.capture(targets.validate(targetRoot));
    const relativePath = "frontend/src/modules/generated/sample/BusinessForm.vue";
    const before = registry.readReference(snapshot, "TARGET", relativePath);
    const path = join(targetRoot, ...relativePath.split("/"));
    writeFileSync(path, `${readFileSync(path, "utf8")}\n<!-- newer sample -->\n`, "utf8");

    expect(registry.readReference(snapshot, "TARGET", relativePath)).toBe(before);
    expect(() => registry.readReference(snapshot, "TARGET", "../outside.md")).toThrow();
  });

  it("routes required context by IR capability and extracts target interfaces", () => {
    const targetRoot = createGenerationTarget(parent);
    const targets = new TargetContractService();
    const registry = new GenerationContextRegistry(new GenerationSkillRegistry(), targets);
    const snapshot = registry.capture(targets.validate(targetRoot), undefined, deriveRequirementIr(WAREHOUSE_PLEDGE_REQUIREMENT));

    expect(snapshot.routing?.capabilities).toEqual(expect.arrayContaining([
      "BASE_FORM", "MULTI_SELECT", "DYNAMIC_REFERENCE", "CASCADE", "DATA_QUERY", "CALCULATION", "BUSINESS_CHECK",
    ]));
    expect(snapshot.routing?.items).toContainEqual(expect.objectContaining({
      key: "skill:flowmind-business-generation:references/backend-api-contract.md",
      required: true,
      reasons: expect.arrayContaining(["DYNAMIC_REFERENCE", "DATA_QUERY"]),
    }));
    expect(snapshot.interfaces).toContainEqual(expect.objectContaining({
      relativePath: "frontend/src/components/workflow/WorkflowStartShell.vue",
      declarations: expect.arrayContaining([expect.objectContaining({ kind: "PROPS", name: "defineProps", signature: expect.stringContaining("processCode") })]),
    }));
    expect(snapshot.interfaces).toContainEqual(expect.objectContaining({
      relativePath: "frontend/src/types/workflow.ts",
      declarations: expect.arrayContaining([expect.objectContaining({ kind: "INTERFACE", name: "WorkflowFormField" })]),
    }));
  });

  it("does not require composite guidance for a basic static form", () => {
    const targetRoot = createGenerationTarget(parent);
    const targets = new TargetContractService();
    const registry = new GenerationContextRegistry(new GenerationSkillRegistry(), targets);
    const snapshot = registry.capture(targets.validate(targetRoot), undefined, deriveRequirementIr(ENTRY_APPLICATION_REQUIREMENT));
    const routed = new Map(snapshot.routing?.items.map((item) => [item.key, item]));

    expect(snapshot.routing?.capabilities).toEqual(["BASE_FORM"]);
    expect(routed.get("skill:flowmind-business-generation:references/generated-form-contract.md")?.required).toBe(true);
    expect(routed.get("skill:flowmind-business-generation:references/backend-api-contract.md")?.required).toBe(false);
    expect(routed.get("skill:flowmind-business-generation:references/golden-example.md")?.required).toBe(false);
  });
});
