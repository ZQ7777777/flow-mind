import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ENTRY_APPLICATION_REQUIREMENT, type MockUser } from "@flowmind/agent-contracts";
import { PlatformSessionRegistry } from "../src/auth/platform-session-registry.service.js";
import { PlatformClientService } from "../src/platform/platform-client.service.js";

const admin: MockUser = {
  userId: "u_admin_01",
  userName: "系统管理员一",
  departmentId: "dept_company",
  departmentName: "总公司",
};

describe("PlatformClientService session mode", () => {
  beforeEach(() => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("FLOW_PLATFORM_AUTH_MODE", "session");
    vi.stubEnv("FLOW_PLATFORM_BASE_URL", "http://business.test");
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it("forwards the verified administrator session and operator identity", async () => {
    const registry = new PlatformSessionRegistry();
    registry.set(admin.userId, "JSESSIONID=admin-session");
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: "definition-1" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    await new PlatformClientService(registry).createDefinition(ENTRY_APPLICATION_REQUIREMENT, admin, "create-1");

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(headers.Cookie).toBe("JSESSIONID=admin-session");
    expect(headers["X-Flow-User-Id"]).toBeUndefined();
    expect(JSON.parse(init.body as string).operatorUserId).toBe("u_admin_01");
  });

  it("clears an expired session and never replays a failed write", async () => {
    const registry = new PlatformSessionRegistry();
    registry.set(admin.userId, "JSESSIONID=expired-session");
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "请先登录" }), {
      status: 401,
      headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(new PlatformClientService(registry)
      .createDefinition(ENTRY_APPLICATION_REQUIREMENT, admin, "create-expired"))
      .rejects.toMatchObject({ response: expect.objectContaining({ code: "FLOW_PLATFORM_AUTHENTICATION_REQUIRED" }) });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(registry.get(admin.userId)).toBeUndefined();
  });
});
