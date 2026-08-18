import { describe, expect, it } from "vitest";
import {
  ENTRY_APPLICATION_REQUIREMENT,
  type GenerationTargetContract,
} from "@flowmind/agent-contracts";
import { deriveGenerationSpec } from "../src/generation/generation-spec.js";
import { createFakeGenerationFiles } from "../src/pi/fake-generation-files.js";

const contract: GenerationTargetContract = {
  contractVersion: "1.1",
  projectId: "fixture",
  backend: {
    rootDir: "backend", javaVersion: "8", springBootVersion: "2.7.18", basePackage: "com.flowmind.business",
    generatedSourceDir: "src/main/java/com/flowmind/business/generated",
    generatedTestDir: "src/test/java/com/flowmind/business/generated",
    starter: { groupId: "com.flowmind", artifactId: "platform-starter", version: "0.1.0-SNAPSHOT", allowedApi: "ProcessRuntimeService#startAndSubmit(StartProcessRequest)" },
    trustedUserContext: { accessorType: "com.flowmind.business.security.CurrentBusinessUserProvider", accessorMethod: "currentUser", userIdProperty: "userId", departmentIdProperty: "departmentId" },
    apiReferences: { platformRuntime: "platform.md", trustedUserContext: "user.java" },
    verificationProfile: "maven-java8",
  },
  frontend: {
    rootDir: "frontend", framework: "vue3", generatedViewDir: "src/modules/generated",
    generatedApiDir: "src/api/generated", generatedTestDir: "src/modules/generated/__tests__",
    routeRegistry: "src/router/generated-routes.ts", verificationProfile: "vue3-npm",
  },
  readableReferenceFiles: ["platform.md", "user.java"],
  allowedOutputPatterns: [
    "backend/src/main/java/com/flowmind/business/generated/**/*.java",
    "backend/src/test/java/com/flowmind/business/generated/**/*.java",
    "frontend/src/modules/generated/**/*",
    "frontend/src/api/generated/**/*",
    "frontend/src/router/generated-routes.ts",
  ],
  protectedFiles: [{ path: "platform.md", sha256: "0".repeat(64) }],
};

describe("fake generator reference-data support", () => {
  it("generates cascading API calls, product autofill, and multi-value DTOs", () => {
    const requirement = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    requirement.businessCode = "pledge_application";
    requirement.businessName = "质押申请";
    requirement.formFields.push(
      { fieldCode: "futuresAccountNo", fieldName: "期货账号", fieldType: "select", controlType: "select", required: true, validation: {}, sortOrder: 10, referenceDataSource: { resource: "FUTURES_ACCOUNTS" } },
      { fieldCode: "exchangeCode", fieldName: "交易所", fieldType: "select", controlType: "select", required: true, validation: {}, sortOrder: 20, referenceDataSource: { resource: "EXCHANGES" } },
      { fieldCode: "tradingCode", fieldName: "交易编码", fieldType: "string", controlType: "input", required: true, readOnly: true, validation: {}, sortOrder: 30, referenceDataSource: { resource: "TRADING_CODES", parameterBindings: { accountNo: "futuresAccountNo", exchangeCode: "exchangeCode" } } },
      { fieldCode: "contractMultiplier", fieldName: "合约乘数", fieldType: "number", controlType: "number", required: true, validation: {}, sortOrder: 40 },
      { fieldCode: "pledgeUnitQuantity", fieldName: "质押品单位数量", fieldType: "number", controlType: "number", required: true, validation: {}, sortOrder: 50 },
      { fieldCode: "previousSettlementPrice", fieldName: "昨结算价", fieldType: "number", controlType: "number", required: true, validation: {}, sortOrder: 60 },
      { fieldCode: "productCodes", fieldName: "品种", fieldType: "select", controlType: "select", required: true, multiple: true, validation: {}, sortOrder: 70, referenceDataSource: { resource: "FUTURES_PRODUCTS", parameterBindings: { exchangeCode: "exchangeCode" }, autofillBindings: { contractMultiplier: "contractMultiplier", pledgeUnitQuantity: "pledgeUnitQuantity", previousSettlementPrice: "previousSettlementPrice" } } },
    );
    const spec = deriveGenerationSpec(requirement, contract);
    const files = createFakeGenerationFiles(
      requirement,
      spec,
      contract,
      'import type { RouteRecordRaw } from "vue-router";\nexport const generatedRoutes: RouteRecordRaw[] = [];\n',
    );
    const api = files[spec.paths.api];
    const view = files[spec.paths.view];
    const request = files[spec.paths.requestDto];

    expect(api).toContain("/api/reference-data/futures-accounts");
    expect(api).toContain("/api/reference-data/futures-products?");
    expect(view).toContain("loadTradingCodes");
    expect(view).toContain("form.contractMultiplier = selected ? selected.contractMultiplier");
    expect(view).toContain('multiple');
    expect(request).toContain("java.util.List<String> productCodes");
    expect(request).toContain("@NotEmpty");
  });
});
