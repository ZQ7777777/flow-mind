import { describe, expect, it } from "vitest";
import {
  ENTRY_APPLICATION_REQUIREMENT,
  normalizeBusinessRequirement,
  renderBusinessRequirementMarkdown,
  type LegacyBusinessRequirement,
} from "@flowmind/agent-contracts";
import { validateRequirement } from "../src/requirement/requirement-validator.js";

describe("BusinessRequirement 1.2", () => {
  it("normalizes legacy 1.0 requirements deterministically", () => {
    const canonical = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
    const legacy = {
      ...canonical,
      schemaVersion: "1.0",
    } as unknown as LegacyBusinessRequirement;
    delete (legacy as unknown as Record<string, unknown>).entryDisplayName;
    delete (legacy as unknown as Record<string, unknown>).entryPageTitle;
    delete (legacy as unknown as Record<string, unknown>).nodeFieldPermissions;

    const normalized = normalizeBusinessRequirement(legacy);
    expect(normalized.schemaVersion).toBe("1.2");
    expect(normalized.frontendBehavior?.sections).toHaveLength(1);
    expect(normalized.entryDisplayName).toBe("入金申请");
    expect(normalized.entryPageTitle).toBe("发起入金申请");
    expect(normalized.nodeFieldPermissions).toContainEqual(expect.objectContaining({
      nodeCode: "apply", fieldCode: "amount", visible: true, editable: true, required: true,
    }));
    expect(validateRequirement(normalized).readyForReview).toBe(true);
  });

  it("renders a stable Markdown requirement document", () => {
    const first = renderBusinessRequirementMarkdown(ENTRY_APPLICATION_REQUIREMENT);
    const second = renderBusinessRequirementMarkdown(structuredClone(ENTRY_APPLICATION_REQUIREMENT));
    expect(second).toBe(first);
    expect(first).toContain("# 入金申请需求文档");
    expect(first).toContain("## 节点字段权限");
    expect(first).toContain("POST /api/workflow/processes/entry_application/start-submit");
    expect(first).toContain("apply");
    expect(first).toContain("approverRule");
    expect(first).toContain("roleCode");
    expect(first).toContain("multiInstanceMode");
    expect(first.indexOf("apply")).toBeLessThan(first.indexOf("manager_approve"));
    expect(first.indexOf("## 页面行为")).toBeLessThan(first.indexOf("- 分区"));
    expect(first.indexOf("- 分区")).toBeLessThan(first.indexOf("## 流程节点与审批规则"));
  });
});
