import { describe, expect, it, vi } from "vitest";
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

  it("does not load the Pi runtime while the compaction policy module initializes", async () => {
    vi.resetModules();
    const piAiCompatLoaded = vi.fn();
    const piAiLoaded = vi.fn();
    const piAgentLoaded = vi.fn();
    vi.doMock("@earendil-works/pi-ai/compat", () => {
      piAiCompatLoaded();
      return { complete: vi.fn() };
    });
    vi.doMock("@earendil-works/pi-ai", () => {
      piAiLoaded();
      return { uuidv7: vi.fn() };
    });
    vi.doMock("@earendil-works/pi-coding-agent", () => {
      piAgentLoaded();
      return { convertToLlm: vi.fn(), serializeConversation: vi.fn() };
    });

    await import("../src/pi/flowmind-compaction.js");

    expect(piAiCompatLoaded).not.toHaveBeenCalled();
    expect(piAiLoaded).not.toHaveBeenCalled();
    expect(piAgentLoaded).not.toHaveBeenCalled();
    vi.doUnmock("@earendil-works/pi-ai/compat");
    vi.doUnmock("@earendil-works/pi-ai");
    vi.doUnmock("@earendil-works/pi-coding-agent");
  });
});
