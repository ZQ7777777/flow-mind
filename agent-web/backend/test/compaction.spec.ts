import { describe, expect, it } from "vitest";
import { COMPACTION_SECTIONS, calculateCompactionSettings, isValidFlowMindSummary } from "../src/pi/flowmind-compaction.js";

describe("Flow Mind compaction policy", () => {
  it("calculates bounded reserve and recent token budgets", () => {
    expect(calculateCompactionSettings(32000)).toEqual({ reserveTokens: 8192, keepRecentTokens: 4800 });
    expect(calculateCompactionSettings(200000)).toEqual({ reserveTokens: 16384, keepRecentTokens: 12000 });
  });

  it("accepts only summaries with all seven required sections", () => {
    const summary = COMPACTION_SECTIONS.map((heading) => `${heading}\nstate`).join("\n");
    expect(isValidFlowMindSummary(summary)).toBe(true);
    expect(isValidFlowMindSummary(summary.replace(COMPACTION_SECTIONS[3], "## Files"))).toBe(false);
    expect(isValidFlowMindSummary("")).toBe(false);
  });
});
