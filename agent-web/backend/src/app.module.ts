import { Module } from "@nestjs/common";
import { APP_GUARD } from "@nestjs/core";
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
import { GenerationContextRegistry } from "./generation/generation-context-registry.service.js";
import { StaticValidatorService } from "./validation/static-validator.service.js";
import { VerificationWorkerService } from "./verification/verification-worker.service.js";
import { ReviewerService } from "./review/reviewer.service.js";
import { QualityPipelineService } from "./verification/quality-pipeline.service.js";
import { ArtifactWriterService } from "./artifact/artifact-writer.service.js";
import { RepairCoordinatorService } from "./repair/repair-coordinator.service.js";
import { BusinessAuthClient } from "./auth/business-auth-client.service.js";
import { PlatformSessionRegistry } from "./auth/platform-session-registry.service.js";
import { AgentAdminGuard } from "./auth/agent-admin.guard.js";
import { AgentAuthController } from "./auth/agent-auth.controller.js";
import { GenerationSkillRegistry } from "./generation/generation-skill-registry.service.js";
import { RagRetrieverService } from "./retrieval/rag-retriever.service.js";
import { ModelBudgetService } from "./budget/model-budget.service.js";

@Module({
  controllers: [AppController, HealthController, AgentAuthController],
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
    GenerationSkillRegistry,
    RagRetrieverService,
    ModelBudgetService,
    GenerationContextRegistry,
    StaticValidatorService,
    VerificationWorkerService,
    ReviewerService,
    QualityPipelineService,
    ArtifactWriterService,
    RepairCoordinatorService,
    GenerationSkillRegistry,
    BusinessAuthClient,
    PlatformSessionRegistry,
    AgentAdminGuard,
    { provide: APP_GUARD, useExisting: AgentAdminGuard },
  ],
})
export class AppModule {}
