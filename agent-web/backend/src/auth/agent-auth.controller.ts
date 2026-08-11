import { Body, Controller, Get, Headers, HttpCode, HttpStatus, Inject, Post, Res } from "@nestjs/common";
import type { Response } from "express";
import type { AgentAuthenticatedUser, AgentLoginCredentials } from "@flowmind/agent-contracts";
import { AgentError } from "../common/agent-error.js";
import {
  BusinessAuthClient,
  browserSessionCookie,
  browserSetCookie,
  CLEAR_SESSION_COOKIE,
} from "./business-auth-client.service.js";
import { PlatformSessionRegistry } from "./platform-session-registry.service.js";

@Controller("/api/agent/auth")
export class AgentAuthController {
  constructor(
    @Inject(BusinessAuthClient) private readonly auth: BusinessAuthClient,
    @Inject(PlatformSessionRegistry) private readonly sessions: PlatformSessionRegistry,
  ) {}

  @Post("/login")
  @HttpCode(HttpStatus.OK)
  async login(
    @Body() credentials: AgentLoginCredentials,
    @Res({ passthrough: true }) response: Response,
  ): Promise<AgentAuthenticatedUser> {
    const result = await this.auth.login(credentials);
    if (!result.user.administrator) {
      await this.auth.logout(result.cookie).catch(() => undefined);
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_ADMIN_REQUIRED", "仅管理员可以访问 Agent 工作台");
    }
    this.sessions.set(result.user.userId, result.cookie);
    response.setHeader("Set-Cookie", browserSetCookie(result.cookie));
    return result.user;
  }

  @Get("/me")
  async me(@Headers("cookie") rawCookie?: string): Promise<AgentAuthenticatedUser> {
    const cookie = this.requireCookie(rawCookie);
    const user = await this.auth.me(cookie);
    if (!user.administrator) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_ADMIN_REQUIRED", "仅管理员可以访问 Agent 工作台");
    }
    this.sessions.set(user.userId, cookie);
    return user;
  }

  @Post("/logout")
  @HttpCode(HttpStatus.NO_CONTENT)
  async logout(
    @Headers("cookie") rawCookie: string | undefined,
    @Res({ passthrough: true }) response: Response,
  ): Promise<void> {
    const cookie = browserSessionCookie(rawCookie);
    if (cookie) {
      await this.auth.logout(cookie).catch(() => undefined);
      this.sessions.deleteCookie(cookie);
    }
    response.setHeader("Set-Cookie", CLEAR_SESSION_COOKIE);
  }

  private requireCookie(rawCookie?: string): string {
    const cookie = browserSessionCookie(rawCookie);
    if (!cookie) {
      throw new AgentError(HttpStatus.UNAUTHORIZED, "AGENT_AUTHENTICATION_REQUIRED", "请先登录管理员账号");
    }
    return cookie;
  }
}
