import { afterEach, describe, expect, it } from "vitest";
import { loadConfig } from "../src/config.js";

describe("agent config", () => {
  const originalBusinessUrl = process.env.BUSINESS_BASE_FRONTEND_URL;

  afterEach(() => {
    if (originalBusinessUrl === undefined) delete process.env.BUSINESS_BASE_FRONTEND_URL;
    else process.env.BUSINESS_BASE_FRONTEND_URL = originalBusinessUrl;
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
