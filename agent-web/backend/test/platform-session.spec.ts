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

  it("loads registered roles with the verified administrator session", async () => {
    const registry = new PlatformSessionRegistry();
    registry.set(admin.userId, "JSESSIONID=admin-session");
    const payload = {
      users: [],
      departments: [],
      roles: [{ roleCode: "finance", roleName: "财务" }],
    };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(payload), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(new PlatformClientService(registry).getOrganizationOptions(admin)).resolves.toEqual(payload);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://business.test/api/admin/process-definition-options",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({ Cookie: "JSESSIONID=admin-session" }),
      }),
    );
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

  it("upserts the generated business entry with the administrator session", async () => {
    const registry = new PlatformSessionRegistry();
    registry.set(admin.userId, "JSESSIONID=admin-session");
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: "entry-1" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);
    const payload = {
      entryDisplayName: "入金申请",
      entryPageUrl: "/generated/entry-application/apply",
      entrySource: "AGENT_GENERATED" as const,
      enabled: true as const,
      generationId: "generation-1",
      artifactRevision: "3",
    };

    await new PlatformClientService(registry)
      .upsertBusinessEntryConfig("definition / 1", payload, admin);

    expect(fetchMock).toHaveBeenCalledWith(
      "http://business.test/api/admin/business-entry-configs/by-definition/definition%20%2F%201",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify(payload),
        headers: expect.objectContaining({
          "Content-Type": "application/json",
          Cookie: "JSESSIONID=admin-session",
        }),
      }),
    );
  });
});
