import { describe, expect, it } from "vitest";
import { ENTRY_APPLICATION_REQUIREMENT, type GenerationTargetContract } from "@flowmind/agent-contracts";
import { deriveGenerationSpec, GenerationRequirementError } from "../src/generation/generation-spec.js";

const contract = {
  backend: { rootDir: "backend", generatedSourceDir: "src/main/java/com/example/generated", generatedTestDir: "src/test/java/com/example/generated", basePackage: "com.example" },
  frontend: { rootDir: "frontend", generatedViewDir: "src/modules/generated", generatedApiDir: "src/api/generated", generatedTestDir: "src/modules/generated/__tests__", routeRegistry: "src/router/generated-routes.ts" },
} as GenerationTargetContract;

describe("deriveGenerationSpec", () => {
  it("derives stable Java and frontend names from underscores and hyphens", () => {
    const requirement = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    requirement.businessCode = "travel_expense-2026";
    const spec = deriveGenerationSpec(requirement, contract);
    expect(spec.classPrefix).toBe("TravelExpense2026");
    expect(spec.packageSegment).toBe("travelexpense2026");
    expect(spec.kebabCode).toBe("travel-expense-2026");
    expect(spec.files).toHaveLength(11);
  });

  it("rejects reserved words, illegal identifiers and case-insensitive collisions", () => {
    const reserved = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    reserved.formFields[0].fieldCode = "class";
    expect(() => deriveGenerationSpec(reserved, contract)).toThrow(GenerationRequirementError);

    const collision = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    collision.attachments[0].attachmentCode = "ApplicantName";
    expect(() => deriveGenerationSpec(collision, contract)).toThrow(GenerationRequirementError);

    const illegal = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    illegal.businessCode = "2026 expense";
    expect(() => deriveGenerationSpec(illegal, contract)).toThrow(GenerationRequirementError);
  });
});
