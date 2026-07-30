import { Controller, Get, HttpStatus, Inject } from "@nestjs/common";
import { AgentError } from "./common/agent-error.js";
import { DatabaseService } from "./persistence/database.service.js";
import { PiAdapterService } from "./pi/pi-adapter.service.js";
import { PlatformClientService } from "./platform/platform-client.service.js";

@Controller()
export class HealthController {
  constructor(
    @Inject(DatabaseService) private readonly database: DatabaseService,
    @Inject(PiAdapterService) private readonly pi: PiAdapterService,
    @Inject(PlatformClientService) private readonly platform: PlatformClientService,
  ) {}

  @Get("/health/live")
  live() {
    return { status: "UP" };
  }

  @Get("/health/ready")
  async ready() {
    try {
      this.database.db.prepare("SELECT 1").get();
      const pi = await this.pi.ready();
      if (!pi.ready) throw new Error(pi.message);
      await this.platform.ping();
      return { status: "UP", checks: { sqlite: "UP", model: "UP", platform: "UP" } };
    } catch (error) {
      throw new AgentError(
        HttpStatus.SERVICE_UNAVAILABLE,
        "AGENT_NOT_READY",
        error instanceof Error ? error.message : String(error),
      );
    }
  }
}
