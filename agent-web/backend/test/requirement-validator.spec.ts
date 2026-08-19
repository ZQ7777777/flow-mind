import { describe, expect, it } from "vitest";
import { ENTRY_APPLICATION_REQUIREMENT, WAREHOUSE_PLEDGE_REQUIREMENT } from "@flowmind/agent-contracts";
import { validateRequirement } from "../src/requirement/requirement-validator.js";

describe("validateRequirement", () => {
  it("accepts the entry application baseline", () => {
    const result = validateRequirement(ENTRY_APPLICATION_REQUIREMENT);
    expect(result.structurallyValid).toBe(true);
    expect(result.readyForReview).toBe(true);
    expect(result.missingItems).toEqual([]);
  });

  it("accepts the 1.2 warehouse pledge page behavior gold requirement", () => {
    const result = validateRequirement(WAREHOUSE_PLEDGE_REQUIREMENT);
    expect(result).toEqual(expect.objectContaining({ structurallyValid: true, readyForReview: true }));
  });

  it.each([
    ["assignment", "quantity = 1"],
    ["function call", "Math.abs(quantity)"],
    ["conditional expression", "quantity > 0 ? quantity : 0"],
    ["unknown identifier", "quantity + secretValue"],
  ])("rejects unsafe frontend expressions containing %s", (_label, expression) => {
    const input = structuredClone(WAREHOUSE_PLEDGE_REQUIREMENT);
    input.frontendBehavior!.calculations[0].expression = expression;
    const result = validateRequirement(input);
    expect(result.readyForReview).toBe(false);
    expect(result.ambiguities.join(" ")).toContain("不允许的语法或未知标识符");
  });

  it("accepts a non-blocking NOTICE node addressed to the starter", () => {
    const input = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    const finance = input.nodes.find((node) => node.nodeCode === "finance_confirm")!;
    finance.nodeType = "NOTICE";
    finance.nodeName = "知会经办";
    finance.approverRule = { type: "STARTER", config: {} };
    finance.multiInstanceMode = "SINGLE";
    finance.noticeConfig = { title: "流程知会", content: "已办理完成，请知悉。" };
    delete finance.listenerConfig;
    delete finance.timeoutConfig;
    delete finance.reminderConfig;
    delete input.nodes.find((node) => node.nodeCode === "manager_approve")!.listenerConfig;

    const result = validateRequirement(input);

    expect(result.structurallyValid).toBe(true);
    expect(result.readyForReview).toBe(true);
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

  it("rejects the non-canonical keys that an agent must not submit", () => {
    const input = {
      businessCode: "DEPOSIT_APPLY_001",
      businessName: "入金申请",
      systemCode: "FINANCE_SYS_001",
      processTarget: "完成入金申请",
      roles: [],
      formFields: [],
      attachments: [],
      nodes: [],
      edges: [],
      approvalRules: {},
      multiPersonMode: {},
      businessRules: [],
    };
    const result = validateRequirement(input);

    expect(result.structurallyValid).toBe(false);
    expect(result.schemaErrors).toEqual(expect.arrayContaining([
      expect.objectContaining({ params: expect.objectContaining({ missingProperty: "schemaVersion" }) }),
      expect.objectContaining({ params: expect.objectContaining({ missingProperty: "goal" }) }),
      expect.objectContaining({ params: expect.objectContaining({ missingProperty: "participants" }) }),
      expect.objectContaining({ params: expect.objectContaining({ additionalProperty: "processTarget" }) }),
    ]));
  });

  it("reports runtime policies that cannot be applied to a user task", () => {
    const input = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    const manager = input.nodes.find((node) => node.nodeCode === "manager_approve")!;
    manager.listenerConfig = {
      taskActionRules: { reject: { enabled: true, targetNodeCodes: ["end"] } },
    };

    const result = validateRequirement(input);

    expect(result.structurallyValid).toBe(true);
    expect(result.readyForReview).toBe(false);
    expect(result.ambiguities).not.toHaveLength(0);
  });

  it("reports user task approver configs that platform cannot resolve", () => {
    const input = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    const finance = input.nodes.find((node) => node.nodeCode === "finance_confirm")!;
    finance.approverRule = { type: "ROLE", config: {} };

    const result = validateRequirement(input);

    expect(result.structurallyValid).toBe(true);
    expect(result.readyForReview).toBe(false);
    expect(result.ambiguities.join(" ")).toContain("roleCode");
  });

  it("keeps version 1.0 compatible and accepts valid version 1.1 dynamic sources", () => {
    const legacy = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    legacy.schemaVersion = "1.0";
    expect(validateRequirement(legacy).readyForReview).toBe(true);

    const dynamic = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    dynamic.formFields.push(
      {
        fieldCode: "exchangeCode", fieldName: "交易所", fieldType: "select", controlType: "select",
        required: true, validation: {}, sortOrder: 4,
        referenceDataSource: { resource: "EXCHANGES" },
      },
      {
        fieldCode: "productCodes", fieldName: "品种", fieldType: "select", controlType: "select",
        required: true, validation: {}, sortOrder: 5, multiple: true,
        referenceDataSource: {
          resource: "FUTURES_PRODUCTS",
          parameterBindings: { exchangeCode: "exchangeCode" },
        },
      },
    );

    const result = validateRequirement(dynamic);
    expect(result.structurallyValid).toBe(true);
    expect(result.readyForReview).toBe(true);
  });

  it("rejects missing, self-referencing, and unknown dynamic field dependencies", () => {
    const input = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    input.formFields.push({
      fieldCode: "tradingCode", fieldName: "交易编码", fieldType: "string", controlType: "input",
      required: true, validation: {}, readOnly: true, sortOrder: 4,
      referenceDataSource: {
        resource: "TRADING_CODES",
        parameterBindings: { accountNo: "tradingCode", exchangeCode: "missingExchange" },
      },
    });

    const result = validateRequirement(input);
    expect(result.readyForReview).toBe(false);
    expect(result.ambiguities.join(" ")).toContain("不能依赖自身");
    expect(result.ambiguities.join(" ")).toContain("missingExchange");
  });
});
