import { defineStore } from "pinia";
import {
  fetchAuthenticatedUser,
  login as loginRequest,
  logout as logoutRequest,
} from "../api/auth";
import { WorkflowApiError } from "../api/http";
import type { AuthenticatedUser } from "../types/auth";

interface AuthState {
  user: AuthenticatedUser | null;
  initialized: boolean;
  loading: boolean;
  error: string;
}

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => ({ user: null, initialized: false, loading: false, error: "" }),
  getters: {
    authenticated: (state): boolean => state.user !== null,
  },
  actions: {
    async login(username: string, password: string): Promise<boolean> {
      this.loading = true;
      this.error = "";
      try {
        this.user = await loginRequest({ username: username.trim(), password });
        this.initialized = true;
        return true;
      } catch (error) {
        this.user = null;
        this.initialized = true;
        this.error = error instanceof Error ? error.message : "登录失败";
        return false;
      } finally {
        this.loading = false;
      }
    },

    async loadCurrentUser(): Promise<boolean> {
      this.loading = true;
      try {
        this.user = await fetchAuthenticatedUser();
        return true;
      } catch (error) {
        this.user = null;
        if (!(error instanceof WorkflowApiError && error.status === 401)) throw error;
        return false;
      } finally {
        this.initialized = true;
        this.loading = false;
      }
    },

    async ensureAuthenticated(): Promise<boolean> {
      return this.initialized ? this.authenticated : this.loadCurrentUser();
    },

    async logout(): Promise<void> {
      try {
        await logoutRequest();
      } finally {
        this.clear();
      }
    },

    clear(): void {
      this.user = null;
      this.initialized = true;
      this.error = "";
    },
  },
});
