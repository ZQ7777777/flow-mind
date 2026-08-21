import { describe, expect, it } from "vitest";
import { REQUIREMENT_SYSTEM_PROMPT } from "../src/pi/requirement-prompt.js";

describe("requirement prompt", () => {
  it("teaches the agent the NOTICE contract", () => {
    expect(REQUIREMENT_SYSTEM_PROMPT).toContain("nodeType START|USER_TASK|NOTICE|");
    expect(REQUIREMENT_SYSTEM_PROMPT).toContain("For NOTICE nodes, approverRule is the recipient rule");
    expect(REQUIREMENT_SYSTEM_PROMPT).toContain("noticeConfig may contain plain-text title/content");
  });

  it("requires exact registered role codes and asks after submission rejection", () => {
    expect(REQUIREMENT_SYSTEM_PROMPT).toContain("list_registered_roles");
    expect(REQUIREMENT_SYSTEM_PROMPT).toContain("list_organization_directory");
    expect(REQUIREMENT_SYSTEM_PROMPT).not.toContain("again before submission");
    expect(REQUIREMENT_SYSTEM_PROMPT).not.toContain("prepare_approver_rule_review");
    expect(REQUIREMENT_SYSTEM_PROMPT).not.toContain("confirm_approver_rule");
    expect(REQUIREMENT_SYSTEM_PROMPT).not.toContain("Never confirm a later node in the same turn");
    expect(REQUIREMENT_SYSTEM_PROMPT).not.toContain("review status is COMPLETED");
    expect(REQUIREMENT_SYSTEM_PROMPT).toContain("exact roleCode");
    expect(REQUIREMENT_SYSTEM_PROMPT).toContain("do not guess");
    expect(REQUIREMENT_SYSTEM_PROMPT).toContain("ASK_USER");
    expect(REQUIREMENT_SYSTEM_PROMPT).not.toContain('"roleCode": "DEPARTMENT_MANAGER"');
    expect(REQUIREMENT_SYSTEM_PROMPT).not.toContain('"roleCode": "FINANCE"');
  });
});
