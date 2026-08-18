import { describe, expect, it } from "vitest";
import { REQUIREMENT_SYSTEM_PROMPT } from "../src/pi/requirement-prompt.js";

describe("requirement prompt", () => {
  it("teaches the agent the NOTICE contract", () => {
    expect(REQUIREMENT_SYSTEM_PROMPT).toContain("nodeType START|USER_TASK|NOTICE|");
    expect(REQUIREMENT_SYSTEM_PROMPT).toContain("For NOTICE nodes, approverRule is the recipient rule");
    expect(REQUIREMENT_SYSTEM_PROMPT).toContain("noticeConfig may contain plain-text title/content");
  });
});
