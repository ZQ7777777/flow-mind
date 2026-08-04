import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { INestApplication } from "@nestjs/common";
import { NestFactory } from "@nestjs/core";
import request from "supertest";
import { mkdtempSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { AppModule } from "../src/app.module.js";
import { AgentExceptionFilter } from "../src/common/agent-exception.filter.js";
import { DatabaseService } from "../src/persistence/database.service.js";
import { createGenerationTarget, seedActiveWorkflow } from "./generation-fixture.js";

describe("Agent API contract", () => {
  let app: INestApplication;
  let root: string;

  beforeEach(async () => {
    root = mkdtempSync(join(tmpdir(), "flowmind-api-"));
    process.env.AGENT_DATA_DIR = root;
    process.env.AGENT_DB_PATH = join(root, "agent.db");
    process.env.AGENT_FAKE_PI = "true";
    process.env.AGENT_ALLOWED_TARGET_ROOTS = root;
    app = await NestFactory.create(AppModule, { logger: false });
    app.useGlobalFilters(new AgentExceptionFilter());
    await app.init();
  });

  afterEach(async () => {
    await app.close();
    rmSync(root, { recursive: true, force: true });
    delete process.env.AGENT_DB_PATH;
    delete process.env.AGENT_DATA_DIR;
    delete process.env.AGENT_ALLOWED_TARGET_ROOTS;
  });

  it("enforces owner isolation, row versions and absence of a session list", async () => {
    const created = await request(app.getHttpServer())
      .post("/api/agent/sessions")
      .set("X-Agent-User-Id", "user_sales")
      .send({});
    expect(created.status, JSON.stringify(created.body)).toBe(201);
    expect(created.body.state).toBe("COLLECTING");

    await request(app.getHttpServer())
      .get(`/api/agent/sessions/${created.body.sessionId}`)
      .set("X-Agent-User-Id", "user_manager")
      .expect(403)
      .expect((response) => expect(response.body.code).toBe("AGENT_SESSION_FORBIDDEN"));

    await request(app.getHttpServer())
      .post(`/api/agent/sessions/${created.body.sessionId}/messages`)
      .set("X-Agent-User-Id", "user_sales")
      .set("If-Match", "99")
      .send({ content: "hello" })
      .expect(409)
      .expect((response) => expect(response.body.code).toBe("AGENT_ROW_VERSION_CONFLICT"));

    await request(app.getHttpServer())
      .get("/api/agent/sessions")
      .set("X-Agent-User-Id", "user_sales")
      .expect(404);
  });

  it("starts M3 and serves staged files through the public API", async () => {
    const target = createGenerationTarget(root);
    seedActiveWorkflow(app.get(DatabaseService), "session-api-m3", null);
    const started = await request(app.getHttpServer())
      .post("/api/agent/sessions/session-api-m3/code-generations")
      .set("X-Agent-User-Id", "user_sales")
      .set("If-Match", "0")
      .set("Idempotency-Key", "api-m3")
      .send({ targetRoot: target })
      .expect(202);
    const generationId = started.body.generationId;
    let detail: { body: any } | undefined;
    for (let index = 0; index < 50; index += 1) {
      detail = await request(app.getHttpServer())
        .get(`/api/agent/sessions/session-api-m3/code-generations/${generationId}`)
        .set("X-Agent-User-Id", "user_sales");
      if (detail.body.status === "REVIEW") break;
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    expect(detail?.body.status).toBe("REVIEW");
    const fileResponse = await request(app.getHttpServer())
      .get(`/api/agent/sessions/session-api-m3/code-generations/${generationId}/files/frontend/src/router/generated-routes.ts`)
      .set("X-Agent-User-Id", "user_sales");
    expect(fileResponse.status, JSON.stringify(fileResponse.body)).toBe(200);
    expect(fileResponse.body.content).toContain("entry-application-apply");
  });
});
