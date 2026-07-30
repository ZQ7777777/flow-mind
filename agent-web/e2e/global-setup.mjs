import { rmSync } from "node:fs";
import { resolve } from "node:path";
import { startMockPlatform } from "./mock-platform.mjs";

export default async function globalSetup() {
  const dataDir = resolve(".e2e-data");
  rmSync(dataDir, { recursive: true, force: true });
  process.env.AGENT_FAKE_PI = "true";
  process.env.AGENT_BIND_HOST = "127.0.0.1";
  process.env.AGENT_PORT = "3100";
  process.env.AGENT_DATA_DIR = dataDir;
  process.env.FLOW_PLATFORM_BASE_URL = "http://127.0.0.1:18080";

  const platform = startMockPlatform(18080);
  await new Promise((resolveReady) => platform.once("listening", resolveReady));
  const { bootstrap } = await import("../backend/dist/main.js");
  const app = await bootstrap();

  return async () => {
    await app.close();
    await new Promise((resolveClosed) => platform.close(resolveClosed));
    rmSync(dataDir, { recursive: true, force: true });
  };
}
