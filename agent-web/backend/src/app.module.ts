import { Module } from "@nestjs/common";
import { AppController } from "./app.controller.js";
import { HealthController } from "./health.controller.js";
import { IdentityService } from "./identity/identity.service.js";
import { DatabaseService } from "./persistence/database.service.js";
import { PiAdapterService } from "./pi/pi-adapter.service.js";
import { PlatformClientService } from "./platform/platform-client.service.js";
import { EventBusService } from "./workflow/event-bus.service.js";
import { WorkflowService } from "./workflow/workflow.service.js";

@Module({
  controllers: [AppController, HealthController],
  providers: [
    IdentityService,
    DatabaseService,
    PiAdapterService,
    PlatformClientService,
    EventBusService,
    WorkflowService,
  ],
})
export class AppModule {}
