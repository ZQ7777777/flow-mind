import { Module } from "@nestjs/common";
import { AppController } from "./app.controller.js";
import { HealthController } from "./health.controller.js";
import { IdentityService } from "./identity/identity.service.js";
import { DatabaseService } from "./persistence/database.service.js";
import { PiAdapterService } from "./pi/pi-adapter.service.js";
import { PlatformClientService } from "./platform/platform-client.service.js";
import { EventBusService } from "./workflow/event-bus.service.js";
import { WorkflowService } from "./workflow/workflow.service.js";
import { GenerationService } from "./generation/generation.service.js";
import { StagingService } from "./generation/staging.service.js";
import { TargetContractService } from "./generation/target-contract.service.js";
import { StaticValidatorService } from "./validation/static-validator.service.js";
import { VerificationWorkerService } from "./verification/verification-worker.service.js";
import { ReviewerService } from "./review/reviewer.service.js";
import { QualityPipelineService } from "./verification/quality-pipeline.service.js";
import { ArtifactWriterService } from "./artifact/artifact-writer.service.js";
import { RepairCoordinatorService } from "./repair/repair-coordinator.service.js";

@Module({
  controllers: [AppController, HealthController],
  providers: [
    IdentityService,
    DatabaseService,
    PiAdapterService,
    PlatformClientService,
    EventBusService,
    WorkflowService,
    GenerationService,
    StagingService,
    TargetContractService,
    StaticValidatorService,
    VerificationWorkerService,
    ReviewerService,
    QualityPipelineService,
    ArtifactWriterService,
    RepairCoordinatorService,
  ],
})
export class AppModule {}
