import { computed, ref, type Ref } from "vue";

export interface GraphBounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

const MIN_SCALE = 0.25;
const MAX_SCALE = 2;

export function useSvgViewport(viewportWidth: Ref<number>, viewportHeight: Ref<number>) {
  const svgRef = ref<SVGSVGElement>();
  const scale = ref(1);
  const offsetX = ref(0);
  const offsetY = ref(0);
  const transform = computed(() => `translate(${offsetX.value} ${offsetY.value}) scale(${scale.value})`);
  const zoomPercent = computed(() => `${Math.round(scale.value * 100)}%`);
  let pan: { pointerId: number; x: number; y: number; offsetX: number; offsetY: number } | undefined;

  function svgPoint(event: Pick<PointerEvent | WheelEvent, "clientX" | "clientY">): { x: number; y: number } {
    const svg = svgRef.value;
    if (!svg) return { x: 0, y: 0 };
    if (typeof svg.createSVGPoint === "function" && svg.getScreenCTM()) {
      const point = svg.createSVGPoint();
      point.x = event.clientX;
      point.y = event.clientY;
      return point.matrixTransform(svg.getScreenCTM()!.inverse());
    }
    const rect = svg.getBoundingClientRect();
    return {
      x: rect.width ? (event.clientX - rect.left) * viewportWidth.value / rect.width : event.clientX - rect.left,
      y: rect.height ? (event.clientY - rect.top) * viewportHeight.value / rect.height : event.clientY - rect.top,
    };
  }

  function graphPoint(event: Pick<PointerEvent | WheelEvent, "clientX" | "clientY">): { x: number; y: number } {
    const point = svgPoint(event);
    return {
      x: (point.x - offsetX.value) / scale.value,
      y: (point.y - offsetY.value) / scale.value,
    };
  }

  function setScale(next: number, anchor = { x: viewportWidth.value / 2, y: viewportHeight.value / 2 }): void {
    const normalized = Math.min(MAX_SCALE, Math.max(MIN_SCALE, next));
    if (normalized === scale.value) return;
    const graphX = (anchor.x - offsetX.value) / scale.value;
    const graphY = (anchor.y - offsetY.value) / scale.value;
    offsetX.value = anchor.x - graphX * normalized;
    offsetY.value = anchor.y - graphY * normalized;
    scale.value = normalized;
  }

  function zoomIn(): void { setScale(scale.value + 0.1); }
  function zoomOut(): void { setScale(scale.value - 0.1); }
  function wheelZoom(event: WheelEvent): void {
    setScale(scale.value * (event.deltaY < 0 ? 1.1 : 0.9), svgPoint(event));
  }

  function beginPan(event: PointerEvent): void {
    if (event.button !== 0) return;
    const target = event.target as Element | null;
    if (target?.closest("[data-graph-interactive]")) return;
    const point = svgPoint(event);
    pan = { pointerId: event.pointerId, x: point.x, y: point.y, offsetX: offsetX.value, offsetY: offsetY.value };
    svgRef.value?.setPointerCapture?.(event.pointerId);
  }

  function movePan(event: PointerEvent): void {
    if (!pan || pan.pointerId !== event.pointerId) return;
    const point = svgPoint(event);
    offsetX.value = pan.offsetX + point.x - pan.x;
    offsetY.value = pan.offsetY + point.y - pan.y;
  }

  function endPan(event?: PointerEvent): void {
    if (!pan || (event && pan.pointerId !== event.pointerId)) return;
    pan = undefined;
  }

  function resetView(): void {
    scale.value = 1;
    offsetX.value = 0;
    offsetY.value = 0;
  }

  function fitView(bounds: GraphBounds, padding = 28): void {
    if (bounds.width <= 0 || bounds.height <= 0) return resetView();
    const next = Math.min(
      MAX_SCALE,
      Math.max(MIN_SCALE, Math.min(
        (viewportWidth.value - padding * 2) / bounds.width,
        (viewportHeight.value - padding * 2) / bounds.height,
      )),
    );
    scale.value = next;
    offsetX.value = (viewportWidth.value - bounds.width * next) / 2 - bounds.x * next;
    offsetY.value = (viewportHeight.value - bounds.height * next) / 2 - bounds.y * next;
  }

  return {
    svgRef, scale, offsetX, offsetY, transform, zoomPercent,
    graphPoint, zoomIn, zoomOut, wheelZoom, beginPan, movePan, endPan, resetView, fitView,
  };
}
