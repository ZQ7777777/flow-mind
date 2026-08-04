import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  globalSetup: "./e2e/global-setup.mjs",
  timeout: 30000,
  fullyParallel: false,
  use: {
    baseURL: `http://127.0.0.1:${process.env.AGENT_E2E_PORT || "3199"}`,
    channel: process.env.AGENT_E2E_BROWSER || "msedge",
    trace: "retain-on-failure",
  },
});
