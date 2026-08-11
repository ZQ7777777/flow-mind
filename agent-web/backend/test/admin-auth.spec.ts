import { HttpStatus, type ExecutionContext } from "@nestjs/common";
import { NestFactory } from "@nestjs/core";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import request from "supertest";
import { mkdtempSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import type { AgentAuthenticatedUser } from "@flowmind/agent-contracts";
import { AgentError } from "../src/common/agent-error.js";
import { BusinessAuthClient } from "../src/auth/business-auth-client.service.js";
import { PlatformSessionRegistry } from "../src/auth/platform-session-registry.service.js";
import { AgentAdminGuard } from "../src/auth/agent-admin.guard.js";
import { AgentAuthController } from "../src/auth/agent-auth.controller.js";
import { IdentityService } from "../src/identity/identity.service.js";
import { AppModule } from "../src/app.module.js";
import { AgentExceptionFilter } from "../src/common/agent-exception.filter.js";

const admin: AgentAuthenticatedUser = {
  userId: "u_admin_01",
  username: "admin01",
  realName: "系统管理员一",
  departmentId: "dept_company",
  departmentName: "总公司",
  userType: "ADMIN",
  administrator: true,
};

const user: AgentAuthenticatedUser = {
  ...admin,
  userId: "u_sales_01",
  username: "sales01",
  realName: "张三",
  userType: "USER",
  administrator: false,
};

describe("agent administrator authentication", () => {
  beforeEach(() => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("FLOW_PLATFORM_BASE_URL", "http://business.test");
    vi.stubEnv("FLOW_PLATFORM_AUTH_MODE", "session");
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it("extracts the business session from a successful login", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(admin), {
      status: 200,
      headers: {
        "Content-Type": "application/json",
        "Set-Cookie": "JSESSIONID=session-admin; Path=/; HttpOnly; SameSite=Lax",
      },
    })));

    const result = await new BusinessAuthClient().login({ username: "admin01", password: "123456" });

    expect(result.user).toEqual(admin);
    expect(result.cookie).toBe("JSESSIONID=session-admin");
  });

  it("injects authentication dependencies when bootstrapped through Nest", async () => {
    const root = mkdtempSync(join(tmpdir(), "flowmind-admin-auth-"));
    vi.stubEnv("AGENT_DATA_DIR", root);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(admin), {
      status: 200,
      headers: {
        "Content-Type": "application/json",
        "Set-Cookie": "JSESSIONID=session-admin; Path=/; HttpOnly; SameSite=Lax",
      },
    })));
    const app = await NestFactory.create(AppModule, { logger: false });
    app.useGlobalFilters(new AgentExceptionFilter());
    await app.init();
    try {
      await request(app.getHttpServer())
        .post("/api/agent/auth/login")
        .send({ username: "admin01", password: "123456" })
        .expect(200)
        .expect((response) => expect(response.body).toEqual(admin));
    } finally {
      await app.close();
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("rejects a valid non-administrator login and does not forward its cookie", async () => {
    const client = {
      login: vi.fn().mockResolvedValue({ user, cookie: "JSESSIONID=session-user" }),
      logout: vi.fn().mockResolvedValue(undefined),
    };
    const response = { setHeader: vi.fn() };
    const controller = new AgentAuthController(client as never, new PlatformSessionRegistry());

    await expect(controller.login({ username: "sales01", password: "123456" }, response as never))
      .rejects.toMatchObject({ status: HttpStatus.FORBIDDEN });
    expect(response.setHeader).not.toHaveBeenCalledWith("Set-Cookie", expect.stringContaining("session-user"));
  });

  it("uses the authenticated administrator instead of forged identity headers", async () => {
    const authClient = { me: vi.fn().mockResolvedValue(admin) };
    const registry = new PlatformSessionRegistry();
    const identity = new IdentityService();
    const guard = new AgentAdminGuard(authClient as never, registry, identity);
    const request = {
      path: "/api/agent/config",
      headers: {
        cookie: "JSESSIONID=session-admin; theme=dark",
        "x-agent-user-id": "u_sales_01",
        "x-agent-user-name": "Forged User",
      } as Record<string, string>,
    };

    await expect(guard.canActivate(contextFor(request))).resolves.toBe(true);

    expect(request.headers["x-agent-user-id"]).toBe("u_admin_01");
    expect(request.headers["x-agent-user-name"]).toBe("系统管理员一");
    expect(registry.get("u_admin_01")).toBe("JSESSIONID=session-admin");
  });

  it("rejects protected requests without a business session", async () => {
    const guard = new AgentAdminGuard(
      { me: vi.fn() } as never,
      new PlatformSessionRegistry(),
      new IdentityService(),
    );

    await expect(guard.canActivate(contextFor({ path: "/api/agent/config", headers: {} })))
      .rejects.toBeInstanceOf(AgentError);
  });
});

function contextFor(request: { path: string; headers: Record<string, string> }): ExecutionContext {
  return {
    switchToHttp: () => ({ getRequest: () => request }),
  } as unknown as ExecutionContext;
}
