import { defineStore } from "pinia";
import type { ToastTone } from "../utils/message";

/** 提示条条目。 */
export interface ToastEntry {
  id: number;
  tone: ToastTone;
  title: string;
  content?: string;
  messageType?: string;
  createdAt: string;
}

interface ToastState {
  toasts: ToastEntry[];
}

const TOAST_DURATION = 6000;

let nextToastId = 0;
const dismissTimers = new Map<number, ReturnType<typeof setTimeout>>();

export const useToastStore = defineStore("toast", {
  state: (): ToastState => ({ toasts: [] }),
  actions: {
    /** 新增提示条并安排自动消失。 */
    push(toast: Omit<ToastEntry, "id" | "createdAt">): number {
      nextToastId += 1;
      const id = nextToastId;
      this.toasts.push({
        ...toast,
        id,
        createdAt: new Date().toISOString(),
      });
      dismissTimers.set(
        id,
        setTimeout(() => this.dismiss(id), TOAST_DURATION),
      );
      return id;
    },

    /** 手动关闭单条提示条。 */
    dismiss(id: number): void {
      const timer = dismissTimers.get(id);
      if (timer) {
        clearTimeout(timer);
        dismissTimers.delete(id);
      }
      this.toasts = this.toasts.filter((toast) => toast.id !== id);
    },

    /** 清空全部提示条。 */
    clear(): void {
      for (const timer of dismissTimers.values()) {
        clearTimeout(timer);
      }
      dismissTimers.clear();
      this.toasts = [];
    },
  },
});
