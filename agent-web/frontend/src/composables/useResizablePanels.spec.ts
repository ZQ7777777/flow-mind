import { beforeEach, describe, expect, it } from "vitest";
import {
  DEFAULT_PANEL_RATIO,
  MIN_CONVERSATION_WIDTH,
  MIN_REVIEW_WIDTH,
  PANEL_DIVIDER_WIDTH,
  clampConversationWidth,
  readStoredPanelRatio,
} from "./useResizablePanels";

describe("useResizablePanels helpers", () => {
  beforeEach(() => localStorage.clear());

  it("keeps both panels above their minimum widths", () => {
    const containerWidth = 1200;
    expect(clampConversationWidth(containerWidth, 100)).toBe(MIN_CONVERSATION_WIDTH);
    expect(clampConversationWidth(containerWidth, 1100)).toBe(containerWidth - MIN_REVIEW_WIDTH - PANEL_DIVIDER_WIDTH);
    expect(clampConversationWidth(containerWidth, 500)).toBe(500);
  });

  it("reads a valid stored ratio and rejects invalid values", () => {
    expect(readStoredPanelRatio("0.45")).toBe(0.45);
    expect(readStoredPanelRatio(null)).toBe(DEFAULT_PANEL_RATIO);
    expect(readStoredPanelRatio("not-a-number")).toBe(DEFAULT_PANEL_RATIO);
    expect(readStoredPanelRatio("1.2")).toBe(DEFAULT_PANEL_RATIO);
  });
});
