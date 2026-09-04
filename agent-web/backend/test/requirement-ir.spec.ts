import { describe, expect, it } from "vitest";
import { ENTRY_APPLICATION_REQUIREMENT } from "@flowmind/agent-contracts";
import { deriveAcceptanceAssertions, deriveRequirementIr, validateRequirementIr } from "../src/requirement/requirement-ir.js";

describe("Requirement IR", () => {
  it("normalizes a confirmed requirement into a generation-ready IR", () => {
    const ir = deriveRequirementIr(structuredClone(ENTRY_APPLICATION_REQUIREMENT));
    const validation = validateRequirementIr(ir);

    expect(validation).toMatchObject({ structurallyValid: true, generationReady: true, schemaErrors: [], semanticErrors: [] });
    expect(ir.identity.businessCode).toBe("entry_application");
    expect(ir.submission).toMatchObject({ processCode: "entry_application", action: "START_AND_SUBMIT" });
    expect(ir.fields.map(({ fieldCode }) => fieldCode)).toEqual(["applicantName", "amount", "accountNo"]);
    expect(ir.attachments.map(({ attachmentCode }) => attachmentCode)).toEqual(["bankReceipt"]);
  });

  it("turns unsafe requirement references into blocking ambiguities", () => {
    const requirement = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    requirement.frontendBehavior = {
      sections: [{ sectionCode: "business", title: "业务信息", fieldCodes: ["applicantName", "inventedField"], sortOrder: 1 }],
      dataQueries: [], calculations: [], checks: [],
    };

    const ir = deriveRequirementIr(requirement);
    const validation = validateRequirementIr(ir);

    expect(ir.ambiguities.some(({ question }) => question.includes("inventedField"))).toBe(true);
    expect(validation.generationReady).toBe(false);
    expect(validation.semanticErrors.some((message) => message.includes("inventedField"))).toBe(true);
  });

  it("derives runner-side assertions without asking the generator to define success", () => {
    const ir = deriveRequirementIr(structuredClone(ENTRY_APPLICATION_REQUIREMENT));
    const assertions = deriveAcceptanceAssertions(ir);

    expect(assertions.some(({ assertionId }) => assertionId === "submission-action")).toBe(true);
    expect(assertions.some(({ assertionId }) => assertionId === "required-amount")).toBe(true);
    expect(assertions.some(({ assertionId }) => assertionId === "attachment-bankReceipt")).toBe(true);
    expect(new Set(assertions.map(({ assertionId }) => assertionId)).size).toBe(assertions.length);
  });
});
