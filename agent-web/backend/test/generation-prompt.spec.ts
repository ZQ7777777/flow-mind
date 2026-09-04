import { describe, expect, it } from "vitest";
import type { BusinessRequirement, GenerationTargetContract, RequirementIrDraft } from "@flowmind/agent-contracts";
import { buildGenerationPrompt } from "../src/pi/generation-prompt.js";
import type { GenerationSpec } from "../src/generation/generation-spec.js";

describe("frontend-only generation prompt", () => {
  it("contains dynamic generation inputs and immutable context", () => {
    const prompt = buildGenerationPrompt(
      { formFields: [], frontendBehavior: { sections: [], dataQueries: [], calculations: [], checks: [] } } as unknown as BusinessRequirement,
      { irVersion: "0.1", identity: { businessCode: "test" }, fields: [] } as unknown as RequirementIrDraft,
      {},
      { contractVersion: "2.1", generationMode: "FRONTEND_ONLY" } as unknown as GenerationTargetContract,
      { businessName: "Test", processCode: "test", kebabCode: "test", routePath: "/generated/test/apply", routeName: "generated-test-apply", files: ["frontend/src/modules/generated/test/BusinessForm.vue"], applyAttachments: [], hasBusinessApi: true } as unknown as GenerationSpec,
      { businessReferenceData: "GET /api/reference-data/futures-products", contextSummary: "golden sha256" },
    );
    expect(prompt).toContain("golden sha256");
    expect(prompt).toContain("frontend/src/modules/generated/test/BusinessForm.vue");
    expect(prompt).toContain("BusinessForm.vue must not read or write modelValue keys outside Requirement IR fields");
    expect(prompt).toContain("Authoritative Requirement IR");
    expect(prompt).toContain("only authority for generated behavior");
    expect(prompt).not.toContain("Hard boundaries:");
    expect(prompt).not.toContain("meta.standalone: true");
    expect(prompt).not.toContain("GET only");
    expect(prompt).not.toContain("WorkflowStartShell");
    expect(prompt).not.toContain("Spring Boot");
    expect(prompt).not.toContain("ProcessRuntimeService");
    expect(prompt).not.toContain("MockMvc");
  });
});
