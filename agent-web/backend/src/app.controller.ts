import {
  Body,
  Controller,
  Get,
  Header,
  Headers,
  HttpCode,
  Inject,
  Param,
  Post,
  Put,
  Res,
} from "@nestjs/common";
import type { Response } from "express";
import { IdentityService } from "./identity/identity.service.js";
import { WorkflowService } from "./workflow/workflow.service.js";
import { EventBusService } from "./workflow/event-bus.service.js";
import { GenerationService } from "./generation/generation.service.js";
import { loadConfig } from "./config.js";

@Controller()
export class AppController {
  constructor(
    @Inject(IdentityService) private readonly identity: IdentityService,
    @Inject(WorkflowService) private readonly workflow: WorkflowService,
    @Inject(EventBusService) private readonly events: EventBusService,
    @Inject(GenerationService) private readonly generation: GenerationService,
  ) {}

  @Get("/api/agent/mock-users")
  listUsers() {
    return this.identity.listUsers();
  }

  @Get("/api/agent/config")
  getPublicConfig() {
    return { defaultTargetRoot: loadConfig().allowedTargetRoots[0] || "" };
  }

  @Post("/api/agent/test-fixtures/quality-gate")
  createQualityGateFixture(
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Body() body: { targetRoot?: string },
  ) {
    return this.generation.createTesterQualityFixture(
      this.identity.resolve(userId, userName), body?.targetRoot?.trim() || "",
    );
  }

  @Post("/api/agent/sessions")
  async createSession(
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Body() body: { targetRoot?: string },
  ) {
    return this.workflow.createSession(this.identity.resolve(userId, userName), body?.targetRoot);
  }

  @Get("/api/agent/sessions/:sessionId")
  async getSession(
    @Param("sessionId") sessionId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
  ) {
    return this.workflow.getSnapshot(sessionId, this.identity.resolve(userId, userName));
  }

  @Post("/api/agent/sessions/:sessionId/messages")
  @HttpCode(202)
  async sendMessage(
    @Param("sessionId") sessionId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
    @Body() body: { content: string },
  ) {
    return this.workflow.queueMessage(
      sessionId,
      this.identity.resolve(userId, userName),
      parseVersion(ifMatch),
      body?.content,
    );
  }

  @Get("/api/agent/sessions/:sessionId/events")
  @Header("Content-Type", "text/event-stream")
  @Header("Cache-Control", "no-cache, no-transform")
  @Header("Connection", "keep-alive")
  async streamEvents(
    @Param("sessionId") sessionId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Res() response: Response,
  ): Promise<void> {
    const user = this.identity.resolve(userId, userName);
    const snapshot = await this.workflow.getSnapshot(sessionId, user);
    response.flushHeaders();
    writeEvent(response, "workflow.snapshot", snapshot);
    const unsubscribe = this.events.subscribe(sessionId, (event) => writeEvent(response, event.type, event.data));
    const heartbeat = setInterval(() => response.write(": heartbeat\n\n"), 20000);
    response.on("close", () => {
      clearInterval(heartbeat);
      unsubscribe();
      response.end();
    });
  }

  @Get("/api/agent/sessions/:sessionId/requirement")
  getRequirement(
    @Param("sessionId") sessionId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
  ) {
    return this.workflow.getRequirement(sessionId, this.identity.resolve(userId, userName));
  }

  @Put("/api/agent/sessions/:sessionId/requirement")
  updateRequirement(
    @Param("sessionId") sessionId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
    @Body() body: { requirement: unknown },
  ) {
    return this.workflow.updateRequirement(
      sessionId,
      this.identity.resolve(userId, userName),
      parseVersion(ifMatch),
      body?.requirement,
    );
  }

  @Post("/api/agent/sessions/:sessionId/requirement/confirm")
  @HttpCode(202)
  confirmRequirement(
    @Param("sessionId") sessionId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
    @Headers("idempotency-key") idempotencyKey: string,
    @Body() body: { requirementRevision: number },
  ) {
    return this.workflow.confirmRequirement(
      sessionId,
      this.identity.resolve(userId, userName),
      parseVersion(ifMatch),
      body?.requirementRevision,
      idempotencyKey,
    );
  }

  @Post("/api/agent/sessions/:sessionId/requirement/reopen")
  reopenRequirement(
    @Param("sessionId") sessionId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
  ) {
    return this.workflow.reopenRequirement(
      sessionId,
      this.identity.resolve(userId, userName),
      parseVersion(ifMatch),
    );
  }

  @Post("/api/agent/sessions/:sessionId/reset")
  resetSession(
    @Param("sessionId") sessionId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
  ) {
    return this.workflow.resetSession(
      sessionId,
      this.identity.resolve(userId, userName),
      parseVersion(ifMatch),
    );
  }

  @Get("/api/agent/sessions/:sessionId/process-preview")
  getProcessPreview(
    @Param("sessionId") sessionId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
  ) {
    return this.workflow.getProcessPreview(sessionId, this.identity.resolve(userId, userName));
  }

  @Post("/api/agent/sessions/:sessionId/process/confirm")
  @HttpCode(202)
  confirmProcess(
    @Param("sessionId") sessionId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
    @Headers("idempotency-key") idempotencyKey: string,
    @Body() body: { platformDefinitionId: string; requirementRevision: number },
  ) {
    return this.workflow.confirmProcess(
      sessionId,
      this.identity.resolve(userId, userName),
      parseVersion(ifMatch),
      body,
      idempotencyKey,
    );
  }

  @Post("/api/agent/sessions/:sessionId/process/retry")
  @HttpCode(202)
  retryProcess(
    @Param("sessionId") sessionId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
    @Headers("idempotency-key") idempotencyKey: string,
  ) {
    return this.workflow.retryProcess(
      sessionId,
      this.identity.resolve(userId, userName),
      parseVersion(ifMatch),
      idempotencyKey,
    );
  }

  @Post("/api/agent/sessions/:sessionId/code-generations")
  @HttpCode(202)
  startGeneration(
    @Param("sessionId") sessionId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
    @Headers("idempotency-key") idempotencyKey: string,
    @Body() body: { targetRoot?: string },
  ) {
    return this.generation.start(
      sessionId,
      this.identity.resolve(userId, userName),
      parseVersion(ifMatch),
      idempotencyKey,
      body?.targetRoot,
    );
  }

  @Get("/api/agent/sessions/:sessionId/code-generations/:generationId")
  getGeneration(
    @Param("sessionId") sessionId: string,
    @Param("generationId") generationId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
  ) {
    return this.generation.get(sessionId, generationId, this.identity.resolve(userId, userName));
  }

  @Get("/api/agent/sessions/:sessionId/code-generations/:generationId/files/*path")
  getGeneratedFile(
    @Param("sessionId") sessionId: string,
    @Param("generationId") generationId: string,
    @Param("path") path: string | string[],
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
  ) {
    return this.generation.readFile(sessionId, generationId, splatPath(path), this.identity.resolve(userId, userName));
  }

  @Put("/api/agent/sessions/:sessionId/code-generations/:generationId/files/*path")
  updateGeneratedFile(
    @Param("sessionId") sessionId: string,
    @Param("generationId") generationId: string,
    @Param("path") path: string | string[],
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Body() body: { content: string; generationRevision: number },
  ) {
    return this.generation.editFile(
      sessionId, generationId, splatPath(path), body?.content, body?.generationRevision,
      this.identity.resolve(userId, userName),
    );
  }

  @Get("/api/agent/sessions/:sessionId/code-generations/:generationId/diff/*path")
  getGeneratedDiff(
    @Param("sessionId") sessionId: string,
    @Param("generationId") generationId: string,
    @Param("path") path: string | string[],
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
  ) {
    return this.generation.readDiff(sessionId, generationId, splatPath(path), this.identity.resolve(userId, userName));
  }

  @Post("/api/agent/sessions/:sessionId/code-generations/:generationId/cancel")
  cancelGeneration(
    @Param("sessionId") sessionId: string,
    @Param("generationId") generationId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
  ) {
    return this.generation.cancel(
      sessionId, generationId, this.identity.resolve(userId, userName), parseVersion(ifMatch),
    );
  }

  @Post("/api/agent/sessions/:sessionId/code-generations/:generationId/regenerate")
  @HttpCode(202)
  regenerate(
    @Param("sessionId") sessionId: string,
    @Param("generationId") generationId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
    @Headers("idempotency-key") idempotencyKey: string,
    @Body() body: { generationRevision: number },
  ) {
    return this.generation.regenerate(
      sessionId, generationId, this.identity.resolve(userId, userName), parseVersion(ifMatch),
      body?.generationRevision, idempotencyKey,
    );
  }
  @Get("/api/agent/sessions/:sessionId/code-generations/:generationId/quality")
  getGenerationQuality(
    @Param("sessionId") sessionId: string,
    @Param("generationId") generationId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
  ) {
    return this.generation.getQuality(sessionId, generationId, this.identity.resolve(userId, userName));
  }

  @Post("/api/agent/sessions/:sessionId/code-generations/:generationId/reverify")
  @HttpCode(202)
  reverifyGeneration(
    @Param("sessionId") sessionId: string,
    @Param("generationId") generationId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
    @Headers("idempotency-key") idempotencyKey: string,
    @Body() body: { generationRevision: number; skipAiReview?: boolean },
  ) {
    return this.generation.reverify(
      sessionId, generationId, this.identity.resolve(userId, userName),
      parseVersion(ifMatch), body?.generationRevision, idempotencyKey, Boolean(body?.skipAiReview),
    );
  }

  @Post("/api/agent/sessions/:sessionId/code-generations/:generationId/quality/start")
  @HttpCode(202)
  startGenerationQuality(
    @Param("sessionId") sessionId: string,
    @Param("generationId") generationId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
    @Headers("idempotency-key") idempotencyKey: string,
    @Body() body: { generationRevision: number; skipAiReview?: boolean },
  ) {
    return this.generation.startQuality(
      sessionId, generationId, this.identity.resolve(userId, userName),
      parseVersion(ifMatch), body?.generationRevision, Boolean(body?.skipAiReview), idempotencyKey,
    );
  }

  @Post("/api/agent/sessions/:sessionId/code-generations/:generationId/quality-override")
  overrideGenerationQuality(
    @Param("sessionId") sessionId: string,
    @Param("generationId") generationId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
    @Headers("idempotency-key") idempotencyKey: string,
    @Body() body: {
      generationRevision: number;
      scopes: Array<"BACKEND_TESTS" | "FRONTEND_TESTS" | "REVIEWER">;
      reason: string;
    },
  ) {
    return this.generation.overrideQuality(
      sessionId, generationId, this.identity.resolve(userId, userName),
      parseVersion(ifMatch), body?.generationRevision, body?.scopes || [], body?.reason || "",
      idempotencyKey,
    );
  }

  @Post("/api/agent/sessions/:sessionId/code-generations/:generationId/confirm-write")
  confirmGenerationWrite(
    @Param("sessionId") sessionId: string,
    @Param("generationId") generationId: string,
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
    @Headers("if-match") ifMatch: string,
    @Headers("idempotency-key") idempotencyKey: string,
    @Body() body: {
      generationRevision: number;
      files: Array<{ relativePath: string; stagedSha256: string }>;
    },
  ) {
    return this.generation.confirmWrite(
      sessionId, generationId, this.identity.resolve(userId, userName),
      parseVersion(ifMatch), body, idempotencyKey,
    );
  }

  @Get("/api/agent/management/process-definitions")
  listManagedDefinitions(
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
  ) {
    return this.generation.listDefinitions(this.identity.resolve(userId, userName));
  }

  @Get("/api/agent/management/code-generations")
  listManagedGenerations(
    @Headers("x-agent-user-id") userId: string,
    @Headers("x-agent-user-name") userName: string,
  ) {
    return this.generation.listGenerations(this.identity.resolve(userId, userName));
  }
}

function parseVersion(value?: string): number {
  const normalized = value?.replace(/^W\//, "").replace(/"/g, "");
  return normalized && /^\d+$/.test(normalized) ? Number(normalized) : Number.NaN;
}

function splatPath(value: string | string[]): string {
  return Array.isArray(value) ? value.join("/") : value;
}

function writeEvent(response: Response, event: string, data: unknown): void {
  response.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
}
