import { computed, ref } from "vue";
import { defineStore } from "pinia";
import type { AgentAuthenticatedUser } from "@flowmind/agent-contracts";
import { ApiError, apiRequest } from "../api";

export const useAuthStore = defineStore("auth", () => {
  const user = ref<AgentAuthenticatedUser>();
  const initialized = ref(false);
  const loading = ref(false);
  const forbidden = ref(false);
  const error = ref("");
  const authenticated = computed(() => Boolean(user.value?.administrator));

  async function initialize(): Promise<void> {
    loading.value = true;
    try {
      user.value = await apiRequest<AgentAuthenticatedUser>("/api/agent/auth/me");
      forbidden.value = !user.value.administrator;
      error.value = "";
    } catch (cause) {
      applyFailure(cause);
    } finally {
      initialized.value = true;
      loading.value = false;
    }
  }

  async function login(username: string, password: string): Promise<boolean> {
    loading.value = true;
    error.value = "";
    forbidden.value = false;
    try {
      user.value = await apiRequest<AgentAuthenticatedUser>("/api/agent/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: username.trim(), password }),
      });
      return authenticated.value;
    } catch (cause) {
      applyFailure(cause);
      return false;
    } finally {
      initialized.value = true;
      loading.value = false;
    }
  }

  async function logout(): Promise<void> {
    try {
      await apiRequest("/api/agent/auth/logout", { method: "POST" });
    } finally {
      clear();
    }
  }

  function clear(): void {
    user.value = undefined;
    forbidden.value = false;
    error.value = "";
    initialized.value = true;
  }

  function applyFailure(cause: unknown): void {
    user.value = undefined;
    forbidden.value = cause instanceof ApiError && cause.status === 403;
    error.value = cause instanceof ApiError && cause.status === 401
      ? ""
      : cause instanceof Error ? cause.message : "认证失败";
  }

  return { user, initialized, loading, forbidden, error, authenticated, initialize, login, logout, clear };
});
