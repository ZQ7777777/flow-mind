import type { AuthenticatedUser, LoginCredentials } from "../types/auth";
import { requestJson } from "./http";

const AUTH_BASE = "/api/auth";

export function login(credentials: LoginCredentials): Promise<AuthenticatedUser> {
  return requestJson<AuthenticatedUser>(`${AUTH_BASE}/login`, {
    method: "POST",
    body: JSON.stringify(credentials),
  });
}

export function fetchAuthenticatedUser(): Promise<AuthenticatedUser> {
  return requestJson<AuthenticatedUser>(`${AUTH_BASE}/me`);
}

export async function logout(): Promise<void> {
  await requestJson<unknown>(`${AUTH_BASE}/logout`, { method: "POST" });
}
