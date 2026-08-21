import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
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
    vi.unstubAllGlobals();
    rmSync(root, { recursive: true, force: true });
    delete process.env.AGENT_DB_PATH;
    delete process.env.AGENT_DATA_DIR;
    delete process.env.AGENT_ALLOWED_TARGET_ROOTS;
    delete process.env.BUSINESS_BASE_FRONTEND_URL;
  });

  it("enforces owner isolation, row versions and absence of a session list", async () => {
    await request(app.getHttpServer())
      .get("/api/agent/config")
      .expect(200)
      .expect({
        defaultTargetRoot: root,
        businessFrontendBaseUrl: "http://127.0.0.1:5174",
      });

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

  it("writes artifacts and registers the generated business entry through confirm-write", async () => {
    const target = createGenerationTarget(root);
    const database = app.get(DatabaseService);
    seedActiveWorkflow(database, "session-api-write", null);
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: "entry-api" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);
    const started = await request(app.getHttpServer())
      .post("/api/agent/sessions/session-api-write/code-generations")
      .set("X-Agent-User-Id", "user_sales")
      .set("If-Match", "0")
      .set("Idempotency-Key", "api-write-generate")
      .send({ targetRoot: target })
      .expect(202);
    let detail: { body: any } | undefined;
    for (let index = 0; index < 50; index += 1) {
      detail = await request(app.getHttpServer())
        .get(`/api/agent/sessions/session-api-write/code-generations/${started.body.generationId}`)
        .set("X-Agent-User-Id", "user_sales");
      if (detail.body.status === "REVIEW") break;
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    expect(detail?.body.status).toBe("REVIEW");
    database.db.prepare(`
      UPDATE agent_code_generation SET quality_revision = generation_revision,
        hard_gate_passed = 1, override_required = 0, can_write = 1 WHERE id = ?
    `).run(started.body.generationId);
    const session = database.getSession("session-api-write")!;

    await request(app.getHttpServer())
      .post(`/api/agent/sessions/session-api-write/code-generations/${started.body.generationId}/confirm-write`)
      .set("X-Agent-User-Id", "user_sales")
      .set("If-Match", String(session.row_version))
      .set("Idempotency-Key", "api-write-confirm")
      .send({
        generationRevision: detail!.body.generationRevision,
        files: detail!.body.manifest.files.map(({ relativePath, stagedSha256 }: any) => ({
          relativePath,
          stagedSha256,
        })),
      })
      .expect(201)
      .expect((response) => expect(response.body.completed).toBe(true));

    expect(database.getGeneration(started.body.generationId)?.status).toBe("COMPLETED");
    expect(database.getSession("session-api-write")?.state).toBe("COMPLETED");
    expect(fetchMock).toHaveBeenCalledWith(
      "http://127.0.0.1:8080/api/admin/business-entry-configs/by-definition/definition_session-api-write",
      expect.objectContaining({
        method: "PUT",
        body: expect.stringContaining('"entryPageUrl":"/generated/entry-application/apply"'),
      }),
    );
  });
});
