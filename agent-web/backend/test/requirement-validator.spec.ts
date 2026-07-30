import { describe, expect, it } from "vitest";
import { ENTRY_APPLICATION_REQUIREMENT } from "@flowmind/agent-contracts";
import { validateRequirement } from "../src/requirement/requirement-validator.js";

describe("validateRequirement", () => {
  it("accepts the entry application baseline", () => {
    const result = validateRequirement(ENTRY_APPLICATION_REQUIREMENT);
    expect(result.structurallyValid).toBe(true);
    expect(result.readyForReview).toBe(true);
    expect(result.missingItems).toEqual([]);
  });

  it("reports unreachable nodes and missing approval rules", () => {
    const input = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    input.edges = input.edges.filter((edge) => edge.targetNodeCode !== "finance_confirm");
    input.nodes.find((node) => node.nodeCode === "manager_approve")!.approverRule = undefined;
    const result = validateRequirement(input);
    expect(result.readyForReview).toBe(false);
    expect(result.missingItems.join(" ")).toContain("审批人规则");
    expect(result.missingItems.join(" ")).toContain("财务确认");
  });

  it("rejects unknown properties at the schema boundary", () => {
    const input = { ...ENTRY_APPLICATION_REQUIREMENT, unexpected: true };
    const result = validateRequirement(input);
    expect(result.structurallyValid).toBe(false);
    expect(result.schemaErrors.length).toBeGreaterThan(0);
  });
});
