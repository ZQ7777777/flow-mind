import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchAuthenticatedUser, login, logout } from "./auth";

describe("auth api", () => {
  afterEach(() => vi.restoreAllMocks());

  it("logs in with JSON credentials and same-origin session cookies", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      userId: "u_sales_01",
      username: "sales01",
      realName: "张三",
      departmentId: "dept_sales",
      departmentName: "业务一部",
      userType: "USER",
      administrator: false,
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);

    await login({ username: "sales01", password: "123456" });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/auth/login");
    expect(init.method).toBe("POST");
    expect(init.credentials).toBe("same-origin");
    expect(JSON.parse(init.body as string)).toEqual({ username: "sales01", password: "123456" });
  });

  it("uses the session for current-user and logout requests", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ userId: "u_sales_01" }), {
        status: 200, headers: { "Content-Type": "application/json" },
      }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchAuthenticatedUser();
    await logout();

    expect(fetchMock.mock.calls[0][0]).toBe("/api/auth/me");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/auth/logout");
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: "POST", credentials: "same-origin" });
  });
});
