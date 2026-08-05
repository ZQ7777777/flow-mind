import { describe, expect, it } from "vitest";
import {
  CODE_PANEL_DIVIDER_WIDTH,
  MIN_CODE_EDITOR_WIDTH,
  MIN_CODE_TREE_WIDTH,
  MIN_QUALITY_PANEL_WIDTH,
  clampCodePanelWidths,
} from "./useResizableCodePanels";

describe("code preview panel sizing", () => {
  it("keeps the tree, editor, and quality panel usable while resizing", () => {
    const width = 1_100;
    expect(clampCodePanelWidths(width, { tree: 20, quality: 20 })).toEqual({
      tree: MIN_CODE_TREE_WIDTH,
      quality: MIN_QUALITY_PANEL_WIDTH,
    });

    const constrained = clampCodePanelWidths(width, { tree: 900, quality: 900 });
    expect(constrained.tree + constrained.quality + MIN_CODE_EDITOR_WIDTH + CODE_PANEL_DIVIDER_WIDTH * 2)
      .toBeLessThanOrEqual(width);
  });
});
