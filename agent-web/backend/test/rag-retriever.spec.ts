import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createHash } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { ENTRY_APPLICATION_REQUIREMENT, WAREHOUSE_PLEDGE_REQUIREMENT, type BusinessRequirement, type GenerationTargetContract } from "@flowmind/agent-contracts";
import { evaluationCatalogV1 } from "../../evals/v1/catalog.js";
import { DatabaseService } from "../src/persistence/database.service.js";
import { RagRetrieverService } from "../src/retrieval/rag-retriever.service.js";
import { detectCapabilities } from "../src/generation/generation-context-router.js";
import { createGenerationTarget, seedActiveWorkflow, write } from "./generation-fixture.js";

describe("M4 governed local RAG", () => {
  let root: string;
  let database: DatabaseService;
  let rag: RagRetrieverService;
  const user = { userId: "user_sales", userName: "Sales User" };

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), "flowmind-rag-"));
    process.env.AGENT_DATA_DIR = join(root, "data");
    process.env.AGENT_DB_PATH = join(root, "agent.db");
    database = new DatabaseService();
    rag = new RagRetrieverService(database);
  });

  afterEach(() => {
    database.onModuleDestroy();
    rmSync(root, { recursive: true, force: true });
    delete process.env.AGENT_DATA_DIR;
    delete process.env.AGENT_DB_PATH;
  });

  it("fails promotion closed unless technical, business and human evidence all pass", () => {
    seedCompletedGeneration("eligible", ENTRY_APPLICATION_REQUIREMENT);
    expect(() => rag.promote("session-eligible", "generation-eligible", user, {
      generationRevision: 1,
      businessAssertions: [],
    })).toThrow(/technical gates, explicit passed business assertions, and confirmed human approval/);

    database.db.prepare("UPDATE agent_code_generation SET confirmed_by = NULL WHERE id = ?").run("generation-eligible");
    expect(() => rag.promote("session-eligible", "generation-eligible", user, {
      generationRevision: 1,
      businessAssertions: [{ assertionId: "business-code", status: "PASSED" }],
    })).toThrow(/technical gates, explicit passed business assertions, and confirmed human approval/);
  });

  it("filters metadata before BM25 and audits search plus key-scoped reads", () => {
    const content = seedCompletedGeneration("approved", calculationRequirement());
    const promotion = rag.promote("session-approved", "generation-approved", user, {
      generationRevision: 1,
      businessAssertions: [{ assertionId: "signed-amount", status: "PASSED" }],
    });
    insertIncompatibleDocument();

    const result = rag.search({
      query: "signed amount decimal calculation 金额计算",
      projectId: "flowmind-business-base",
      contractVersion: "2.1",
      capabilities: ["BASE_FORM", "CALCULATION"],
      limit: 3,
      generationId: "generation-approved",
    });
    expect(result.hits).toHaveLength(1);
    expect(result.hits[0]).toMatchObject({ key: promotion.documentKeys[0], version: "2.1" });
    expect(result.hits[0]).not.toHaveProperty("content");
    expect(result.hits[0].score).toBeGreaterThan(0);

    expect(() => rag.read(result.retrievalId, "rag-incompatible")).toThrow(/not returned/);
    const read = rag.read(result.retrievalId, result.hits[0].key);
    expect(read.content).toBe(content);
    expect(read.sha256).toBe(result.hits[0].sha256);

    const audit = database.db.prepare("SELECT * FROM agent_rag_retrieval WHERE id = ?").get(result.retrievalId) as any;
    expect(JSON.parse(audit.candidate_keys_json)).toEqual([promotion.documentKeys[0]]);
    expect(JSON.parse(audit.filters_json).capabilities).toEqual(["BASE_FORM", "CALCULATION"]);
    expect(database.db.prepare("SELECT COUNT(*) AS count FROM agent_rag_read WHERE retrieval_id = ?").get(result.retrievalId)).toEqual({ count: 1 });
  });

  it("improves the declared approved-context-availability proxy on the frozen task set", () => {
    seedCompletedGeneration("broad-pattern", WAREHOUSE_PLEDGE_REQUIREMENT);
    rag.promote("session-broad-pattern", "generation-broad-pattern", user, {
      generationRevision: 1,
      businessAssertions: [{ assertionId: "generic-pattern-reviewed", status: "PASSED" }],
    });
    const ready = evaluationCatalogV1.tasks.filter(({ expectedOutcome }) => expectedOutcome === "GENERATION_READY");
    const retrievalHits = ready.filter((task) => rag.search({
      query: task.input.userRequest,
      projectId: "flowmind-business-base",
      contractVersion: "2.1",
      capabilities: detectCapabilities(task.requirementIr),
      limit: 1,
    }).hits.length > 0).length;

    expect(ready).toHaveLength(25);
    expect({ withoutRag: 0, withRag: retrievalHits }).toEqual({ withoutRag: 0, withRag: 18 });
  });

  function seedCompletedGeneration(suffix: string, requirement: BusinessRequirement): string {
    const target = createGenerationTarget(root, `target-${suffix}`);
    const contract = JSON.parse(readFileSync(join(target, ".flowmind", "generation-target.json"), "utf8")) as GenerationTargetContract;
    const relativePath = `frontend/src/modules/generated/${suffix}/BusinessForm.vue`;
    const content = `<script setup lang="ts">const approvedPattern = "生成申请表单 多选 动态参考数据 级联 查询 计算 核查 signed amount decimal calculation 金额计算";</script>\n<template><div>${requirement.businessName}</div></template>\n`;
    write(target, relativePath, content);
    seedActiveWorkflow(database, `session-${suffix}`, target, user.userId, requirement);
    const now = new Date().toISOString();
    const digest = sha256(content);
    const manifest = { generationId: `generation-${suffix}`, targetRoot: target, contractVersion: "2.1", revision: 1, files: [{ relativePath, changeType: "ADD", stagedSha256: digest, sizeBytes: Buffer.byteLength(content), validationStatus: "VALID", editedByUser: false }] };
    const quality = { generationId: `generation-${suffix}`, revision: 1, pipelineState: "PASSED", repairRound: 0, maxRepairRounds: 3, stages: [{ stage: "FRONTEND_BUILD", status: "PASSED", hardGate: true, summary: "passed", diagnostics: [] }], hardGatePassed: true, overrideRequired: false, canWrite: true, updatedAt: now };
    database.db.prepare(`INSERT INTO agent_code_generation (
      id, session_id, process_definition_record_id, requirement_revision, requirement_snapshot_json,
      process_snapshot_json, business_code, business_name, status, target_root, target_contract_version,
      target_contract_json, staging_dir, artifact_manifest_json, generation_revision, quality_revision,
      quality_report_json, hard_gate_passed, can_write, write_status, created_by, confirmed_by,
      confirmed_at, written_at, created_at, updated_at
    ) VALUES (?, ?, ?, 1, ?, '{}', ?, ?, 'COMPLETED', ?, '2.1', ?, ?, ?, 1, 1, ?, 1, 1,
      'COMPLETED', ?, ?, ?, ?, ?, ?)`)
      .run(`generation-${suffix}`, `session-${suffix}`, `process_session-${suffix}`, JSON.stringify(requirement), requirement.businessCode,
        requirement.businessName, target, JSON.stringify(contract), join(root, "staging", suffix), JSON.stringify(manifest), JSON.stringify(quality),
        user.userId, user.userId, now, now, now, now);
    return content;
  }

  function insertIncompatibleDocument(): void {
    const now = new Date().toISOString();
    const content = "signed amount decimal calculation 金额计算";
    database.db.prepare(`INSERT INTO agent_rag_document (
      document_key, source_generation_id, source_revision, project_id, contract_version,
      business_code, capabilities_json, relative_path, summary, content, sha256,
      assertion_evidence_json, promoted_by, promoted_at
    ) VALUES ('rag-incompatible', 'generation-approved', 2, 'flowmind-business-base', '1.0',
      'wrong-version', '["BASE_FORM","CALCULATION"]', 'wrong.ts', 'wrong version', ?, ?, '[]', ?, ?)`)
      .run(content, sha256(content), user.userId, now);
  }
});

function calculationRequirement(): BusinessRequirement {
  const requirement = structuredClone(ENTRY_APPLICATION_REQUIREMENT);
  requirement.businessCode = "calculation_sample";
  requirement.businessName = "金额计算样例";
  requirement.frontendBehavior = {
    sections: [], dataQueries: [], checks: [],
    calculations: [{ calculationCode: "signedAmount", targetFieldCode: "amount", expression: "quantity * price", dependencyFieldCodes: ["quantity", "price"], decimalPlaces: 2, sortOrder: 1 }],
  };
  return requirement;
}

function sha256(value: string): string {
  return createHash("sha256").update(value).digest("hex");
}
