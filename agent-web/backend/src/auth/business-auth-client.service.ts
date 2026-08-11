import { HttpStatus, Injectable } from "@nestjs/common";
import type { AgentAuthenticatedUser, AgentLoginCredentials } from "@flowmind/agent-contracts";
import { AgentError } from "../common/agent-error.js";
import { loadConfig } from "../config.js";

@Injectable()
export class BusinessAuthClient {
  private readonly config = loadConfig();

  async login(credentials: AgentLoginCredentials): Promise<{ user: AgentAuthenticatedUser; cookie: string }> {
    const response = await this.fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(credentials),
    });
    const user = await this.userResponse(response);
    const cookie = sessionCookie(response.headers.get("set-cookie"));
    if (!cookie) {
      throw new AgentError(HttpStatus.BAD_GATEWAY, "FLOW_PLATFORM_AUTHENTICATION_FAILED", "业务系统未返回有效会话");
    }
    return { user, cookie };
  }

  async me(cookie: string): Promise<AgentAuthenticatedUser> {
    return this.userResponse(await this.fetch("/api/auth/me", {
      headers: { Cookie: cookie },
    }));
  }

  async logout(cookie: string): Promise<void> {
    const response = await this.fetch("/api/auth/logout", {
      method: "POST",
      headers: { Cookie: cookie },
    });
    if (!response.ok) await this.throwResponse(response);
  }

  private async userResponse(response: Response): Promise<AgentAuthenticatedUser> {
    if (!response.ok) await this.throwResponse(response);
    return response.json() as Promise<AgentAuthenticatedUser>;
  }

  private async throwResponse(response: Response): Promise<never> {
    const body = await response.json().catch(() => ({})) as { message?: string };
    const status = response.status === 401 ? HttpStatus.UNAUTHORIZED
      : response.status === 403 ? HttpStatus.FORBIDDEN : HttpStatus.BAD_GATEWAY;
    const code = status === HttpStatus.UNAUTHORIZED
      ? "AGENT_AUTHENTICATION_REQUIRED"
      : status === HttpStatus.FORBIDDEN ? "AGENT_ADMIN_REQUIRED" : "FLOW_PLATFORM_ERROR";
    throw new AgentError(status, code, body.message || "业务系统认证失败");
  }

  private async fetch(path: string, init: RequestInit): Promise<Response> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.config.platformTimeoutMs);
    try {
      return await fetch(`${this.config.platformBaseUrl}${path}`, { ...init, signal: controller.signal });
    } catch (error) {
      const message = error instanceof Error && error.name === "AbortError"
        ? "业务系统认证请求超时" : "业务系统不可用";
      throw new AgentError(HttpStatus.BAD_GATEWAY, "FLOW_PLATFORM_UNAVAILABLE", message);
    } finally {
      clearTimeout(timeout);
    }
  }
}

export function sessionCookie(header?: string | null): string | undefined {
  const match = header?.match(/(?:^|[,;]\s*)JSESSIONID=([^;,\s]+)/i);
  return match ? `JSESSIONID=${match[1]}` : undefined;
}

export function browserSessionCookie(cookie?: string): string | undefined {
  return sessionCookie(cookie);
}

export const CLEAR_SESSION_COOKIE = "JSESSIONID=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax";

export function browserSetCookie(cookie: string): string {
  return `${cookie}; Path=/; HttpOnly; SameSite=Lax`;
}
