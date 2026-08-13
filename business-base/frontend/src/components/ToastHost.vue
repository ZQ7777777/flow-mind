<script setup lang="ts">
import { storeToRefs } from "pinia";
import { useToastStore } from "../stores/toast";
import { messageTypeLabel } from "../utils/message";

const toastStore = useToastStore();
const { toasts } = storeToRefs(toastStore);
</script>

<template>
  <div class="toast-host" role="region" aria-label="消息提醒" aria-live="polite">
    <transition-group name="toast">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="toast"
        :class="`tone-${toast.tone}`"
        :data-test="`toast-${toast.id}`"
        role="alert"
      >
        <div class="toast-body">
          <strong class="toast-title" data-test="toast-title">{{ toast.title }}</strong>
          <span v-if="toast.messageType" class="toast-tag">{{ messageTypeLabel(toast.messageType) }}</span>
          <p v-if="toast.content" class="toast-content">{{ toast.content }}</p>
        </div>
        <button
          type="button"
          class="toast-close"
          aria-label="关闭提醒"
          data-test="toast-close"
          @click="toastStore.dismiss(toast.id)"
        >
          ×
        </button>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 1000;
  display: grid;
  gap: 10px;
  width: min(360px, calc(100vw - 32px));
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 12px 14px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.16);
  pointer-events: auto;
}

.toast.tone-error {
  border-left: 4px solid #be123c;
  background: #fff1f2;
}

.toast.tone-warning {
  border-left: 4px solid #b45309;
  background: #fffbeb;
}

.toast.tone-info {
  border-left: 4px solid #1d4ed8;
  background: #eff6ff;
}

.toast.tone-success {
  border-left: 4px solid #15803d;
  background: #f0fdf4;
}

.toast-body {
  flex: 1;
  min-width: 0;
}

.toast-title {
  color: #17202a;
  font-size: 14px;
}

.toast-tag {
  display: inline-block;
  margin-left: 8px;
  padding: 1px 7px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  color: #374151;
  font-size: 11px;
  font-weight: 600;
}

.toast-content {
  margin: 6px 0 0;
  color: #5d6978;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.toast-close {
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.toast-close:hover {
  color: #17202a;
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(16px);
}
</style>
