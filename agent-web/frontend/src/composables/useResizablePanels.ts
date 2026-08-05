import { computed, onBeforeUnmount, ref, watch, type CSSProperties, type Ref } from "vue";

export const PANEL_WIDTH_STORAGE_KEY = "flowmind.agent.conversationPanelRatio";
export const DEFAULT_PANEL_RATIO = 0.36;
export const MIN_CONVERSATION_WIDTH = 320;
export const MIN_REVIEW_WIDTH = 480;
export const PANEL_DIVIDER_WIDTH = 16;
export const PANEL_KEYBOARD_STEP = 16;

export function clampConversationWidth(containerWidth: number, requestedWidth: number): number {
  const maximumWidth = Math.max(MIN_CONVERSATION_WIDTH, containerWidth - MIN_REVIEW_WIDTH - PANEL_DIVIDER_WIDTH);
  return Math.min(Math.max(requestedWidth, MIN_CONVERSATION_WIDTH), maximumWidth);
}

export function readStoredPanelRatio(value: string | null): number {
  if (value === null) return DEFAULT_PANEL_RATIO;
  const ratio = Number(value);
  return Number.isFinite(ratio) && ratio > 0 && ratio < 1 ? ratio : DEFAULT_PANEL_RATIO;
}

export function useResizablePanels(): {
  workspaceGrid: Ref<HTMLElement | null>;
  resizingPanels: Ref<boolean>;
  workspaceGridStyle: Readonly<Ref<CSSProperties>>;
  separatorValueNow: Readonly<Ref<number>>;
  startPanelResize: (event: PointerEvent) => void;
  resizePanelsByKeyboard: (event: KeyboardEvent) => void;
} {
  const workspaceGrid = ref<HTMLElement | null>(null);
  const conversationWidth = ref<number | null>(null);
  const containerWidth = ref(0);
  const resizingPanels = ref(false);
  let preferredRatio = readStoredRatio();
  let resizeObserver: ResizeObserver | undefined;
  let activePointerId: number | undefined;

  const workspaceGridStyle = computed<CSSProperties>(() => conversationWidth.value === null
    ? {}
    : { gridTemplateColumns: `${conversationWidth.value}px ${PANEL_DIVIDER_WIDTH}px minmax(0, 1fr)` });
  const separatorValueNow = computed(() => containerWidth.value > 0 && conversationWidth.value !== null
    ? Math.round(conversationWidth.value / containerWidth.value * 100)
    : Math.round(preferredRatio * 100));

  watch(workspaceGrid, (element) => {
    resizeObserver?.disconnect();
    resizeObserver = undefined;
    if (!element) return;

    updateForContainerWidth(element.getBoundingClientRect().width);
    resizeObserver = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) updateForContainerWidth(entry.contentRect.width);
    });
    resizeObserver.observe(element);
  });

  function readStoredRatio(): number {
    try {
      return readStoredPanelRatio(localStorage.getItem(PANEL_WIDTH_STORAGE_KEY));
    } catch {
      return DEFAULT_PANEL_RATIO;
    }
  }

  function updateForContainerWidth(width: number): void {
    if (width <= 0) return;
    containerWidth.value = width;
    conversationWidth.value = clampConversationWidth(width, width * preferredRatio);
  }

  function setConversationWidth(requestedWidth: number): void {
    if (containerWidth.value <= 0) return;
    conversationWidth.value = clampConversationWidth(containerWidth.value, requestedWidth);
    preferredRatio = conversationWidth.value / containerWidth.value;
  }

  function persistRatio(): void {
    try {
      localStorage.setItem(PANEL_WIDTH_STORAGE_KEY, String(preferredRatio));
    } catch {
      // The layout remains usable when storage is unavailable or full.
    }
  }

  function startPanelResize(event: PointerEvent): void {
    if (event.button !== 0 || !workspaceGrid.value) return;
    activePointerId = event.pointerId;
    resizingPanels.value = true;
    document.body.classList.add("is-resizing-panels");
    (event.currentTarget as HTMLElement | null)?.setPointerCapture?.(event.pointerId);
    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", stopPanelResize);
    window.addEventListener("pointercancel", stopPanelResize);
    event.preventDefault();
  }

  function handlePointerMove(event: PointerEvent): void {
    if (event.pointerId !== activePointerId || !workspaceGrid.value) return;
    const bounds = workspaceGrid.value.getBoundingClientRect();
    setConversationWidth(event.clientX - bounds.left);
  }

  function stopPanelResize(event: PointerEvent): void {
    if (event.pointerId !== activePointerId) return;
    activePointerId = undefined;
    resizingPanels.value = false;
    document.body.classList.remove("is-resizing-panels");
    window.removeEventListener("pointermove", handlePointerMove);
    window.removeEventListener("pointerup", stopPanelResize);
    window.removeEventListener("pointercancel", stopPanelResize);
    persistRatio();
  }

  function resizePanelsByKeyboard(event: KeyboardEvent): void {
    const direction = event.key === "ArrowLeft" ? -1 : event.key === "ArrowRight" ? 1 : 0;
    if (direction === 0) return;
    if (conversationWidth.value === null && workspaceGrid.value) {
      updateForContainerWidth(workspaceGrid.value.getBoundingClientRect().width);
    }
    setConversationWidth((conversationWidth.value ?? 0) + direction * PANEL_KEYBOARD_STEP);
    persistRatio();
    event.preventDefault();
  }

  onBeforeUnmount(() => {
    resizeObserver?.disconnect();
    document.body.classList.remove("is-resizing-panels");
    window.removeEventListener("pointermove", handlePointerMove);
    window.removeEventListener("pointerup", stopPanelResize);
    window.removeEventListener("pointercancel", stopPanelResize);
  });

  return {
    workspaceGrid,
    resizingPanels,
    workspaceGridStyle,
    separatorValueNow,
    startPanelResize,
    resizePanelsByKeyboard,
  };
}
