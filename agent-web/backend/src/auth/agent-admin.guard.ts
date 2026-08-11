import { CanActivate, ExecutionContext, HttpStatus, Inject, Injectable } from "@nestjs/common";
import { AgentError } from "../common/agent-error.js";
import { loadConfig } from "../config.js";
import { IdentityService } from "../identity/identity.service.js";
import { BusinessAuthClient, browserSessionCookie } from "./business-auth-client.service.js";
import { PlatformSessionRegistry } from "./platform-session-registry.service.js";

@Injectable()
export class AgentAdminGuard implements CanActivate {
  private readonly config = loadConfig();

  constructor(
    @Inject(BusinessAuthClient) private readonly auth: BusinessAuthClient,
    @Inject(PlatformSessionRegistry) private readonly sessions: PlatformSessionRegistry,
    @Inject(IdentityService) private readonly identity: IdentityService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    if (this.config.platformAuthMode === "trusted-header") return true;
    const request = context.switchToHttp().getRequest<{
      path: string;
      headers: Record<string, string | string[] | undefined>;
    }>();
    if (isPublicPath(request.path)) return true;

    const rawCookie = request.headers.cookie;
    const cookie = browserSessionCookie(typeof rawCookie === "string" ? rawCookie : undefined);
    if (!cookie) {
      throw new AgentError(HttpStatus.UNAUTHORIZED, "AGENT_AUTHENTICATION_REQUIRED", "请先登录管理员账号");
    }

    const user = await this.auth.me(cookie);
    if (!user.administrator) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_ADMIN_REQUIRED", "仅管理员可以访问 Agent 工作台");
    }
    this.sessions.set(user.userId, cookie);
    this.identity.rememberAuthenticated(user);
    request.headers["x-agent-user-id"] = user.userId;
    request.headers["x-agent-user-name"] = user.realName;
    return true;
  }
}

function isPublicPath(path: string): boolean {
  return path.startsWith("/health/") || path.startsWith("/api/agent/auth/");
}
