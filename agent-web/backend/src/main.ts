import "reflect-metadata";
import { existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { NestFactory } from "@nestjs/core";
import type { NestExpressApplication } from "@nestjs/platform-express";
import { AppModule } from "./app.module.js";
import { AgentExceptionFilter } from "./common/agent-exception.filter.js";
import { loadConfig } from "./config.js";

export async function bootstrap() {
  const config = loadConfig();
  const app = await NestFactory.create<NestExpressApplication>(AppModule, { cors: true });
  app.useGlobalFilters(new AgentExceptionFilter());
  app.enableShutdownHooks();

  const currentDir = dirname(fileURLToPath(import.meta.url));
  const frontendDist = join(currentDir, "../../frontend/dist");
  if (existsSync(frontendDist)) {
    app.useStaticAssets(frontendDist);
  }
  await app.listen(config.port, config.bindHost);
  return app;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  void bootstrap();
}
