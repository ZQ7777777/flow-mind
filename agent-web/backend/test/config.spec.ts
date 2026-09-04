import { afterEach, describe, expect, it, vi } from "vitest";
import { loadConfig } from "../src/config.js";

describe("agent config", () => {
  const originalBusinessUrl = process.env.BUSINESS_BASE_FRONTEND_URL;

  afterEach(() => {
    vi.unstubAllEnvs();
    if (originalBusinessUrl === undefined) delete process.env.BUSINESS_BASE_FRONTEND_URL;
    else process.env.BUSINESS_BASE_FRONTEND_URL = originalBusinessUrl;
  });

  it("defaults production code generation to the deterministic IR strategy", () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("AGENT_FAKE_PI", "false");
    vi.stubEnv("AGENT_GENERATION_STRATEGY", "");

    expect(loadConfig().generationStrategy).toBe("DETERMINISTIC_IR_V1");
  });

  it("keeps Fake Pi tests on the legacy session protocol unless explicitly overridden", () => {
    vi.stubEnv("NODE_ENV", "test");
    vi.stubEnv("AGENT_GENERATION_STRATEGY", "");

    expect(loadConfig().generationStrategy).toBe("PI_LEGACY");
  });

  it("defaults the business frontend preview base URL", () => {
    delete process.env.BUSINESS_BASE_FRONTEND_URL;

    expect(loadConfig().businessFrontendBaseUrl).toBe("http://127.0.0.1:5174");
  });

  it("uses the configured business frontend preview base URL without a trailing slash", () => {
    process.env.BUSINESS_BASE_FRONTEND_URL = "http://127.0.0.1:6200/";

    expect(loadConfig().businessFrontendBaseUrl).toBe("http://127.0.0.1:6200");
  });
});
