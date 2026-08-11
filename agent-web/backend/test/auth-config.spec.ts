import { afterEach, describe, expect, it, vi } from "vitest";
import { loadConfig } from "../src/config.js";

describe("platform authentication configuration", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("defaults production to session authentication", () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("FLOW_PLATFORM_AUTH_MODE", "");

    expect(loadConfig().platformAuthMode).toBe("session");
  });

  it("keeps trusted-header as the test default", () => {
    vi.stubEnv("NODE_ENV", "test");
    vi.stubEnv("FLOW_PLATFORM_AUTH_MODE", "");

    expect(loadConfig().platformAuthMode).toBe("trusted-header");
  });

  it("rejects unsupported authentication modes", () => {
    vi.stubEnv("FLOW_PLATFORM_AUTH_MODE", "disabled");

    expect(() => loadConfig()).toThrow(/FLOW_PLATFORM_AUTH_MODE/);
  });
});
