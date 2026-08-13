import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useToastStore } from "./toast";

describe("toast store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("pushes a toast and auto-dismisses after the duration", () => {
    const store = useToastStore();
    store.push({ tone: "error", title: "告警", content: "内容" });

    expect(store.toasts).toHaveLength(1);
    expect(store.toasts[0].title).toBe("告警");
    expect(store.toasts[0].tone).toBe("error");

    vi.advanceTimersByTime(6000);
    expect(store.toasts).toHaveLength(0);
  });

  it("dismisses a toast by id", () => {
    const store = useToastStore();
    const id = store.push({ tone: "info", title: "提示" });

    store.dismiss(id);

    expect(store.toasts).toHaveLength(0);
  });

  it("clears all toasts", () => {
    const store = useToastStore();
    store.push({ tone: "info", title: "a" });
    store.push({ tone: "warning", title: "b" });

    store.clear();

    expect(store.toasts).toHaveLength(0);
  });
});
