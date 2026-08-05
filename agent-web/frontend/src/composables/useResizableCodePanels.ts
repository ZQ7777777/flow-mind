import { computed, onBeforeUnmount, ref, watch, type CSSProperties, type Ref } from "vue";

export const CODE_PANEL_DIVIDER_WIDTH = 12;
export const MIN_CODE_TREE_WIDTH = 160;
export const MIN_CODE_EDITOR_WIDTH = 250;
export const MIN_QUALITY_PANEL_WIDTH = 240;
export const CODE_PANEL_KEYBOARD_STEP = 16;
const TREE_RATIO_STORAGE_KEY = "flowmind.agent.codeTreeRatio";
const QUALITY_RATIO_STORAGE_KEY = "flowmind.agent.qualityPanelRatio";
const DEFAULT_TREE_RATIO = 0.24;
const DEFAULT_QUALITY_RATIO = 0.32;

export type CodePanelSide = "tree" | "quality";

export interface CodePanelWidths {
  tree: number;
  quality: number;
}

export function clampCodePanelWidths(
  containerWidth: number,
  requested: CodePanelWidths,
): CodePanelWidths {
  const usableWidth = Math.max(0, containerWidth - CODE_PANEL_DIVIDER_WIDTH * 2);
  const maximumTree = Math.max(MIN_CODE_TREE_WIDTH, usableWidth - MIN_CODE_EDITOR_WIDTH - MIN_QUALITY_PANEL_WIDTH);
  const tree = Math.min(Math.max(requested.tree, MIN_CODE_TREE_WIDTH), maximumTree);
  const maximumQuality = Math.max(MIN_QUALITY_PANEL_WIDTH, usableWidth - tree - MIN_CODE_EDITOR_WIDTH);
  return {
    tree,
    quality: Math.min(Math.max(requested.quality, MIN_QUALITY_PANEL_WIDTH), maximumQuality),
  };
}

export function useResizableCodePanels(): {
  panel: Ref<HTMLElement | null>;
  resizingSide: Ref<CodePanelSide | undefined>;
  gridStyle: Readonly<Ref<CSSProperties>>;
  separatorValueNow: (side: CodePanelSide) => number;
  startResize: (side: CodePanelSide, event: PointerEvent) => void;
  resizeByKeyboard: (side: CodePanelSide, event: KeyboardEvent) => void;
} {
  const panel = ref<HTMLElement | null>(null);
  const containerWidth = ref(0);
  const widths = ref<CodePanelWidths>();
  const resizingSide = ref<CodePanelSide>();
  let treeRatio = readRatio(TREE_RATIO_STORAGE_KEY, DEFAULT_TREE_RATIO);
  let qualityRatio = readRatio(QUALITY_RATIO_STORAGE_KEY, DEFAULT_QUALITY_RATIO);
  let observer: ResizeObserver | undefined;
  let pointerId: number | undefined;

  const gridStyle = computed<CSSProperties>(() => !widths.value
    ? {}
    : {
      gridTemplateColumns: `${widths.value.tree}px ${CODE_PANEL_DIVIDER_WIDTH}px minmax(${MIN_CODE_EDITOR_WIDTH}px, 1fr) ${CODE_PANEL_DIVIDER_WIDTH}px ${widths.value.quality}px`,
    });

  watch(panel, (element) => {
    observer?.disconnect();
    observer = undefined;
    if (!element) return;
    updateWidths(element.getBoundingClientRect().width);
    if (typeof ResizeObserver === "undefined") return;
    observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) updateWidths(entry.contentRect.width);
    });
    observer.observe(element);
  });

  function updateWidths(width: number): void {
    if (width <= 0) return;
    containerWidth.value = width;
    widths.value = clampCodePanelWidths(width, {
      tree: width * treeRatio,
      quality: width * qualityRatio,
    });
  }

  function setWidths(requested: CodePanelWidths): void {
    if (containerWidth.value <= 0) return;
    widths.value = clampCodePanelWidths(containerWidth.value, requested);
    treeRatio = widths.value.tree / containerWidth.value;
    qualityRatio = widths.value.quality / containerWidth.value;
  }

  function persist(): void {
    try {
      localStorage.setItem(TREE_RATIO_STORAGE_KEY, String(treeRatio));
      localStorage.setItem(QUALITY_RATIO_STORAGE_KEY, String(qualityRatio));
    } catch {
      // The panel sizes still work when browser storage is unavailable.
    }
  }

  function startResize(side: CodePanelSide, event: PointerEvent): void {
    if (event.button !== 0 || !panel.value) return;
    resizingSide.value = side;
    pointerId = event.pointerId;
    document.body.classList.add("is-resizing-code-panels");
    (event.currentTarget as HTMLElement | null)?.setPointerCapture?.(event.pointerId);
    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", stopResize);
    window.addEventListener("pointercancel", stopResize);
    event.preventDefault();
  }

  function handlePointerMove(event: PointerEvent): void {
    if (event.pointerId !== pointerId || !panel.value || !widths.value || !resizingSide.value) return;
    const bounds = panel.value.getBoundingClientRect();
    if (resizingSide.value === "tree") {
      setWidths({ ...widths.value, tree: event.clientX - bounds.left });
    } else {
      setWidths({ ...widths.value, quality: bounds.right - event.clientX });
    }
  }

  function stopResize(event: PointerEvent): void {
    if (event.pointerId !== pointerId) return;
    pointerId = undefined;
    resizingSide.value = undefined;
    document.body.classList.remove("is-resizing-code-panels");
    window.removeEventListener("pointermove", handlePointerMove);
    window.removeEventListener("pointerup", stopResize);
    window.removeEventListener("pointercancel", stopResize);
    persist();
  }

  function resizeByKeyboard(side: CodePanelSide, event: KeyboardEvent): void {
    const direction = event.key === "ArrowLeft" ? -1 : event.key === "ArrowRight" ? 1 : 0;
    if (!direction) return;
    if (!widths.value && panel.value) updateWidths(panel.value.getBoundingClientRect().width);
    if (!widths.value) return;
    setWidths(side === "tree"
      ? { ...widths.value, tree: widths.value.tree + direction * CODE_PANEL_KEYBOARD_STEP }
      : { ...widths.value, quality: widths.value.quality - direction * CODE_PANEL_KEYBOARD_STEP });
    persist();
    event.preventDefault();
  }

  function separatorValueNow(side: CodePanelSide): number {
    if (!widths.value || containerWidth.value <= 0) return side === "tree"
      ? Math.round(treeRatio * 100)
      : Math.round(qualityRatio * 100);
    return Math.round((side === "tree" ? widths.value.tree : widths.value.quality) / containerWidth.value * 100);
  }

  onBeforeUnmount(() => {
    observer?.disconnect();
    document.body.classList.remove("is-resizing-code-panels");
    window.removeEventListener("pointermove", handlePointerMove);
    window.removeEventListener("pointerup", stopResize);
    window.removeEventListener("pointercancel", stopResize);
  });

  return { panel, resizingSide, gridStyle, separatorValueNow, startResize, resizeByKeyboard };
}

function readRatio(key: string, fallback: number): number {
  try {
    const value = Number(localStorage.getItem(key));
    return Number.isFinite(value) && value > 0 && value < 1 ? value : fallback;
  } catch {
    return fallback;
  }
}
