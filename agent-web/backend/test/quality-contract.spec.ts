import { describe, expect, it } from "vitest";
import { WORKFLOW_STATES } from "@flowmind/agent-contracts";

describe("M4-M5 quality workflow contract", () => {
  it("publishes every quality and artifact-writing workflow state", () => {
    expect(WORKFLOW_STATES).toEqual(
      expect.arrayContaining([
        "CODE_VERIFYING",
        "CODE_REVIEWING",
        "CODE_REPAIRING",
        "WRITING_ARTIFACTS",
        "ARTIFACT_WRITE_FAILED",
        "BUSINESS_ENTRY_CONFIGURING",
        "BUSINESS_ENTRY_CONFIG_FAILED",
        "COMPLETED",
      ]),
    );
  });
});
