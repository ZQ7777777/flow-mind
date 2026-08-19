import { describe, expect, it } from "vitest";
import {
  ENTRY_APPLICATION_REQUIREMENT,
  type BusinessRequirement,
  type GenerationTargetContract,
  type ProcessNodeType,
} from "@flowmind/agent-contracts";
import {
  deriveGenerationSpec,
  GenerationRequirementError,
  validateGenerationRequirement,
} from "../src/generation/generation-spec.js";

const contract = {
  backend: { rootDir: "backend", generatedSourceDir: "src/main/java/com/example/generated", generatedTestDir: "src/test/java/com/example/generated", basePackage: "com.example" },
  frontend: { rootDir: "frontend", generatedViewDir: "src/modules/generated", generatedApiDir: "src/api/generated", generatedTestDir: "src/modules/generated/__tests__", routeRegistry: "src/router/generated-routes.ts" },
} as GenerationTargetContract;
const APPLY_SUCCESSOR_ISSUE = "apply 必须且只能流向一个后续用户任务、知会节点、排他网关或并行分支网关";

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

  it.each([
    "NOTICE",
    "EXCLUSIVE_GATEWAY",
    "PARALLEL_SPLIT_GATEWAY",
  ] satisfies ProcessNodeType[])("allows apply to flow to one %s", (nodeType) => {
    const requirement = requirementWithApplySuccessor(nodeType);

    expect(validateGenerationRequirement(requirement)).not.toContain(APPLY_SUCCESSOR_ISSUE);
    expect(deriveGenerationSpec(requirement, contract).processCode).toBe(requirement.businessCode);
  });

  it.each([
    "PARALLEL_JOIN_GATEWAY",
    "END",
  ] satisfies ProcessNodeType[])("rejects apply flowing directly to %s", (nodeType) => {
    const requirement = requirementWithApplySuccessor(nodeType);

    expect(validateGenerationRequirement(requirement)).toContain(APPLY_SUCCESSOR_ISSUE);
  });

  it("rejects apply flowing to a missing node or more than one successor", () => {
    const missing = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    missing.edges.find((edge) => edge.sourceNodeCode === "apply")!.targetNodeCode = "missing";
    expect(validateGenerationRequirement(missing)).toContain(APPLY_SUCCESSOR_ISSUE);

    const multiple = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    multiple.edges.push({
      edgeCode: "e_apply_finance",
      sourceNodeCode: "apply",
      targetNodeCode: "finance_confirm",
      defaultEdge: false,
      sortOrder: 5,
    });
    expect(validateGenerationRequirement(multiple)).toContain(APPLY_SUCCESSOR_ISSUE);
  });
});

function requirementWithApplySuccessor(nodeType: ProcessNodeType): BusinessRequirement {
  const requirement = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
  const successor = requirement.nodes.find((node) => node.nodeCode === "manager_approve")!;
  successor.nodeType = nodeType;
  delete successor.approverRule;
  delete successor.multiInstanceMode;
  delete successor.listenerConfig;
  delete successor.timeoutConfig;
  delete successor.reminderConfig;
  if (nodeType === "PARALLEL_SPLIT_GATEWAY") successor.pairedGatewayCode = "parallel_join";
  return requirement;
}
