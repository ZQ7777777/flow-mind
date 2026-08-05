import { describe, expect, it, vi } from "vitest";
import {
  COMPACTION_SECTIONS,
  calculateCompactionSettings,
  calculateCompactionTokenEstimate,
  createFlowMindCompactionNotice,
  isValidFlowMindSummary,
  resolveKeptRecentTokens,
} from "../src/pi/flowmind-compaction.js";

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

  it("estimates tokens after compaction from summary plus kept recent context", () => {
    expect(calculateCompactionTokenEstimate(50000, 1200, 8000)).toEqual({
      keptRecentTokens: 8000,
      tokensAfterEstimate: 9200,
      tokensReducedEstimate: 40800,
    });
    expect(calculateCompactionTokenEstimate(1000, 800, 600)).toEqual({
      keptRecentTokens: 600,
      tokensAfterEstimate: 1400,
      tokensReducedEstimate: 0,
    });
  });

  it("builds a user-visible compaction event payload with the summary text", () => {
    const summary = "## Current task and workflow state\nstate";
    expect(createFlowMindCompactionNotice("threshold", 50000, summary, 8000)).toEqual({
      reason: "threshold",
      tokensBefore: 50000,
      summaryTokens: Math.ceil(summary.length / 4),
      summary,
      keptRecentTokens: 8000,
      tokensAfterEstimate: 8000 + Math.ceil(summary.length / 4),
      tokensReducedEstimate: 50000 - 8000 - Math.ceil(summary.length / 4),
    });
  });

  it("resolves kept recent token count from preparation settings as fallback", () => {
    expect(resolveKeptRecentTokens({ keptRecentTokens: 3000, settings: { keepRecentTokens: 8000 } })).toBe(3000);
    expect(resolveKeptRecentTokens({ settings: { keepRecentTokens: 8000 } })).toBe(8000);
    expect(resolveKeptRecentTokens({ settings: {} })).toBeNull();
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
