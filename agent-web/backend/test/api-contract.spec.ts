import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { INestApplication } from "@nestjs/common";
import { NestFactory } from "@nestjs/core";
import request from "supertest";
import { mkdtempSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { AppModule } from "../src/app.module.js";
import { AgentExceptionFilter } from "../src/common/agent-exception.filter.js";

describe("Agent API contract", () => {
  let app: INestApplication;
  let root: string;

  beforeEach(async () => {
    root = mkdtempSync(join(tmpdir(), "flowmind-api-"));
    process.env.AGENT_DATA_DIR = root;
    process.env.AGENT_DB_PATH = join(root, "agent.db");
    process.env.AGENT_FAKE_PI = "true";
    app = await NestFactory.create(AppModule, { logger: false });
    app.useGlobalFilters(new AgentExceptionFilter());
    await app.init();
  });

  afterEach(async () => {
    await app.close();
    rmSync(root, { recursive: true, force: true });
    delete process.env.AGENT_DB_PATH;
    delete process.env.AGENT_DATA_DIR;
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
});
