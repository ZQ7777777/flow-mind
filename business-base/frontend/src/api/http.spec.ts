import { afterEach, describe, expect, it, vi } from "vitest";
import { AUTHENTICATION_REQUIRED_EVENT, requestJson } from "./http";

describe("shared HTTP client", () => {
  afterEach(() => vi.restoreAllMocks());

  it("notifies the application when a protected request loses its session", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: "BUSINESS_AUTHENTICATION_REQUIRED",
      message: "请先登录",
    }), { status: 401, headers: { "Content-Type": "application/json" } })));
    const listener = vi.fn();
    window.addEventListener(AUTHENTICATION_REQUIRED_EVENT, listener);

    await expect(requestJson("/api/workflow/me")).rejects.toMatchObject({ status: 401 });

    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener(AUTHENTICATION_REQUIRED_EVENT, listener);
  });
});
