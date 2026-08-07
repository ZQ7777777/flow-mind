import { ref } from "vue";

/**
 * Keeps a scroll container pinned to the bottom as its content grows (typical
 * log/stream behaviour), but releases the pin while the user scrolls up to read
 * earlier content. The pin is restored when the content is cleared, so the next
 * streaming burst sticks to the bottom again.
 */
export function useStickToBottom() {
  const el = ref<HTMLElement | null>(null);
  let pinned = true;

  function onScroll(): void {
    const node = el.value;
    if (!node) return;
    // Treat the user as "at the bottom" unless they are more than a few pixels
    // above it, so minor sub-pixel jitter does not release the pin.
    pinned = node.scrollTop + node.clientHeight >= node.scrollHeight - 8;
  }

  function stick(): void {
    const node = el.value;
    if (!node || !pinned) return;
    node.scrollTop = node.scrollHeight;
  }

  function resetPin(): void {
    pinned = true;
  }

  return { el, onScroll, stick, resetPin };
}
