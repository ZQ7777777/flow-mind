import { onBeforeUnmount, ref, type Ref } from "vue";

export const REASONING_HEIGHT_STORAGE_KEY = "flowmind.agent.reasoningBoxHeight";
export const DEFAULT_REASONING_HEIGHT = 96;
export const MIN_REASONING_HEIGHT = 56;
export const REASONING_KEYBOARD_STEP = 16;

function readStoredHeight(): number {
  try {
    const value = Number(localStorage.getItem(REASONING_HEIGHT_STORAGE_KEY));
    if (Number.isFinite(value) && value >= MIN_REASONING_HEIGHT) return value;
  } catch {
    // Storage unavailable; fall back to the default.
  }
  return DEFAULT_REASONING_HEIGHT;
}

function maxHeight(): number {
  // Cap at roughly half the viewport so the reasoning box never dominates the
  // panel it lives in.
  if (typeof window === "undefined") return DEFAULT_REASONING_HEIGHT * 5;
  return Math.max(MIN_REASONING_HEIGHT * 2, Math.round(window.innerHeight * 0.5));
}

export function clampReasoningHeight(requested: number): number {
  return Math.min(Math.max(requested, MIN_REASONING_HEIGHT), maxHeight());
}

/**
 * Resizable height for the streaming reasoning box. The box keeps its initial
 * 96px size but exposes a top drag handle (plus ArrowUp/ArrowDown keys) so the
 * user can grow it to read more of the model's thinking. The chosen height is
 * persisted to localStorage and shared between the generation and quality views.
 */
export function useResizableHeight(): {
  height: Ref<number>;
  startResize: (event: PointerEvent) => void;
  resizeByKeyboard: (event: KeyboardEvent) => void;
} {
  const height = ref(clampReasoningHeight(readStoredHeight()));
  let activePointerId: number | undefined;
  let startY = 0;
  let startHeight = 0;

  function setHeight(requested: number): void {
    height.value = clampReasoningHeight(requested);
  }

  function persist(): void {
    try {
      localStorage.setItem(REASONING_HEIGHT_STORAGE_KEY, String(height.value));
    } catch {
      // The in-memory height still works when storage is unavailable or full.
    }
  }

  function startResize(event: PointerEvent): void {
    if (event.button !== 0) return;
    activePointerId = event.pointerId;
    startY = event.clientY;
    startHeight = height.value;
    document.body.classList.add("is-resizing-reasoning");
    (event.currentTarget as HTMLElement | null)?.setPointerCapture?.(event.pointerId);
    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", stopResize);
    window.addEventListener("pointercancel", stopResize);
    event.preventDefault();
  }

  // The handle sits on the TOP edge of the box, so dragging the pointer upward
  // (clientY decreases) grows the height.
  function handlePointerMove(event: PointerEvent): void {
    if (event.pointerId !== activePointerId) return;
    setHeight(startHeight - (event.clientY - startY));
  }

  function stopResize(event: PointerEvent): void {
    if (event.pointerId !== activePointerId) return;
    activePointerId = undefined;
    document.body.classList.remove("is-resizing-reasoning");
    window.removeEventListener("pointermove", handlePointerMove);
    window.removeEventListener("pointerup", stopResize);
    window.removeEventListener("pointercancel", stopResize);
    persist();
  }

  function resizeByKeyboard(event: KeyboardEvent): void {
    const direction = event.key === "ArrowUp" ? 1 : event.key === "ArrowDown" ? -1 : 0;
    if (direction === 0) return;
    setHeight(height.value + direction * REASONING_KEYBOARD_STEP);
    persist();
    event.preventDefault();
  }

  onBeforeUnmount(() => {
    document.body.classList.remove("is-resizing-reasoning");
    window.removeEventListener("pointermove", handlePointerMove);
    window.removeEventListener("pointerup", stopResize);
    window.removeEventListener("pointercancel", stopResize);
  });

  return { height, startResize, resizeByKeyboard };
}
