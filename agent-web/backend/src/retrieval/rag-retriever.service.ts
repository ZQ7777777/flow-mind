import { HttpStatus, Inject, Injectable } from "@nestjs/common";
import { createHash, randomUUID } from "node:crypto";
import { existsSync, lstatSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import type {
  BusinessRequirement,
  GenerationContextCapability,
  GenerationQualityReport,
  GenerationTargetContract,
  MockUser,
  RagDocumentContent,
  RagPromotionRequest,
  RagSearchHit,
  RagSearchRequest,
  RagSearchResponse,
} from "@flowmind/agent-contracts";
import { AgentError } from "../common/agent-error.js";
import { DatabaseService, type GenerationRow } from "../persistence/database.service.js";
import { deriveRequirementIr } from "../requirement/requirement-ir.js";
import { detectCapabilities } from "../generation/generation-context-router.js";
import { assertInside, assertNoLinkInExistingPath } from "../generation/path-safety.js";
import { parseManifest } from "../generation/staging.service.js";
import {
  LOCAL_CHUNKER_VERSION,
  LOCAL_EMBEDDING_DIMENSIONS,
  LOCAL_EMBEDDING_MODEL,
  LOCAL_INDEX_VERSION,
  chunkForLocalEmbedding,
  cosineSimilarity,
  embedLocal,
} from "./local-semantic-embedding.js";

interface RagDocumentRow {
  document_key: string;
  project_id: string;
  contract_version: string;
  capabilities_json: string;
  relative_path: string;
  summary: string;
  content: string;
  sha256: string;
  embedding_model: string;
  chunker_version: string;
  index_version: string;
  indexed_sha256: string;
  embedding_json: string;
}

@Injectable()
export class RagRetrieverService {
  constructor(@Inject(DatabaseService) private readonly database: DatabaseService) {}

  promote(sessionId: string, generationId: string, user: MockUser, request: RagPromotionRequest) {
    const generation = this.ownedGeneration(sessionId, generationId, user);
    this.assertEligible(generation, request);
    const contract = JSON.parse(generation.target_contract_json) as GenerationTargetContract;
    const requirement = JSON.parse(generation.requirement_snapshot_json) as BusinessRequirement;
    const capabilities = detectCapabilities(deriveRequirementIr(requirement));
    const manifest = parseManifest(generation);
    const promotedAt = new Date().toISOString();
    const documents = manifest.files.map((file) => {
      const path = resolve(generation.target_root, file.relativePath);
      assertInside(generation.target_root, path, sessionId);
      assertNoLinkInExistingPath(generation.target_root, path, sessionId);
      if (!existsSync(path) || !lstatSync(path).isFile()) {
        throw new AgentError(HttpStatus.CONFLICT, "AGENT_RAG_SOURCE_MISSING", `Promoted source is missing: ${file.relativePath}`, sessionId);
      }
      const bytes = readFileSync(path);
      const content = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
      const sha256 = hash(bytes);
      if (sha256 !== file.stagedSha256) {
        throw new AgentError(HttpStatus.CONFLICT, "AGENT_RAG_SOURCE_CHANGED", `Promoted source changed after approval: ${file.relativePath}`, sessionId);
      }
      const key = `rag_${hash(`${generation.id}:${generation.generation_revision}:${file.relativePath}`).slice(0, 24)}`;
      const summary = `${generation.business_name} (${generation.business_code}) · ${file.relativePath} · ${capabilities.join(",")}`;
      return {
        key,
        relativePath: file.relativePath,
        summary,
        content,
        sha256,
        embeddings: buildDocumentEmbeddings(`${summary}\n${file.relativePath}\n${content}`, capabilities),
      };
    });
    this.database.transaction(() => {
      const insert = this.database.db.prepare(`
        INSERT OR IGNORE INTO agent_rag_document (
          document_key, source_generation_id, source_revision, project_id, contract_version,
          business_code, capabilities_json, relative_path, summary, content, sha256,
          assertion_evidence_json, promoted_by, promoted_at, embedding_model, chunker_version,
          index_version, indexed_sha256, embedding_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const document of documents) {
        insert.run(
          document.key, generation.id, generation.generation_revision, contract.projectId,
          contract.contractVersion, generation.business_code, JSON.stringify(capabilities),
          document.relativePath, document.summary, document.content, document.sha256,
          JSON.stringify(request.businessAssertions), user.userId, promotedAt,
          LOCAL_EMBEDDING_MODEL, LOCAL_CHUNKER_VERSION, LOCAL_INDEX_VERSION, document.sha256, JSON.stringify(document.embeddings),
        );
      }
    });
    return { promoted: true as const, generationId, revision: generation.generation_revision, documentKeys: documents.map(({ key }) => key) };
  }

  search(request: RagSearchRequest): RagSearchResponse {
    const query = request.query?.trim();
    if (!query || !request.projectId || !request.contractVersion) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_RAG_QUERY_INVALID", "RAG query, project and contract version are required.");
    }
    const requestedCapabilities = [...new Set(request.capabilities || [])].sort();
    const rows = this.database.db.prepare(`
      SELECT document_key, project_id, contract_version, capabilities_json, relative_path,
        summary, content, sha256, embedding_model, chunker_version, index_version, indexed_sha256, embedding_json
      FROM agent_rag_document
      WHERE project_id = ? AND contract_version = ?
      ORDER BY document_key
    `).all(request.projectId, request.contractVersion) as RagDocumentRow[];
    // Capability filtering is deliberately complete before any source text is tokenized or ranked.
    const candidates = rows.filter((row) => {
      const capabilities = parseCapabilities(row.capabilities_json);
      return requestedCapabilities.every((capability) => capabilities.includes(capability));
    });
    const limit = Math.min(Math.max(Math.trunc(request.limit || 5), 1), 10);
    const mode = request.mode || "HYBRID";
    if (mode === "HYBRID") this.ensureLocalIndex(candidates);
    const snapshot = mode === "BM25" ? bm25Snapshot() : hybridSnapshot();
    const hits = (mode === "BM25"
      ? rankBm25(query, candidates)
      : rankHybrid(query, requestedCapabilities, candidates)).slice(0, limit);
    const retrievalId = `retr_${randomUUID()}`;
    this.database.db.prepare(`
      INSERT INTO agent_rag_retrieval (
        id, generation_id, query_text, filters_json, candidate_keys_json, result_keys_json,
        created_at, ranking_snapshot_json
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      retrievalId, request.generationId || null, query,
      JSON.stringify({ projectId: request.projectId, contractVersion: request.contractVersion, capabilities: requestedCapabilities }),
      JSON.stringify(candidates.map(({ document_key }) => document_key)),
      JSON.stringify(hits.map(({ key }) => key)), new Date().toISOString(), JSON.stringify({
        ...snapshot,
        scores: hits.map(({ key, score, lexicalScore, vectorScore }) => ({ key, score, lexicalScore, vectorScore })),
      }),
    );
    return { retrievalId, hits, snapshot };
  }

  read(retrievalId: string, key: string): RagDocumentContent {
    const retrieval = this.database.db.prepare(
      "SELECT result_keys_json FROM agent_rag_retrieval WHERE id = ?",
    ).get(retrievalId) as { result_keys_json: string } | undefined;
    const allowed = retrieval ? safeStringArray(retrieval.result_keys_json) : [];
    if (!retrieval || !allowed.includes(key)) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_RAG_READ_FORBIDDEN", "The key was not returned by this retrieval.");
    }
    const row = this.database.db.prepare(`
      SELECT document_key, contract_version, content, sha256 FROM agent_rag_document WHERE document_key = ?
    `).get(key) as Pick<RagDocumentRow, "document_key" | "contract_version" | "content" | "sha256"> | undefined;
    if (!row || hash(row.content) !== row.sha256) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_RAG_DOCUMENT_CORRUPT", "Frozen RAG document failed its hash check.");
    }
    this.database.db.prepare(`
      INSERT INTO agent_rag_read (id, retrieval_id, document_key, document_sha256, read_at)
      VALUES (?, ?, ?, ?, ?)
    `).run(`read_${randomUUID()}`, retrievalId, key, row.sha256, new Date().toISOString());
    return { retrievalId, key, version: row.contract_version, sha256: row.sha256, content: row.content };
  }

  private ownedGeneration(sessionId: string, generationId: string, user: MockUser): GenerationRow {
    const generation = this.database.getGeneration(generationId);
    if (!generation || generation.session_id !== sessionId || generation.created_by !== user.userId) {
      throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_GENERATION_NOT_FOUND", "Generation was not found.", sessionId);
    }
    return generation;
  }

  private assertEligible(generation: GenerationRow, request: RagPromotionRequest): void {
    const assertions = request?.businessAssertions || [];
    let quality: GenerationQualityReport | undefined;
    try { quality = JSON.parse(generation.quality_report_json || "null") as GenerationQualityReport | undefined; } catch { /* fail closed below */ }
    const hardStages = quality?.stages.filter(({ hardGate }) => hardGate) || [];
    const technicalPassed = generation.status === "COMPLETED"
      && generation.generation_revision === request?.generationRevision
      && generation.quality_revision === request?.generationRevision
      && generation.hard_gate_passed === 1
      && generation.can_write === 1
      && quality?.hardGatePassed === true
      && hardStages.length > 0
      && hardStages.every(({ status }) => status === "PASSED");
    const businessPassed = assertions.length > 0
      && assertions.every(({ assertionId, status }) => Boolean(assertionId?.trim()) && status === "PASSED");
    const humanApproved = Boolean(generation.confirmed_by && generation.confirmed_at && generation.written_at);
    if (!technicalPassed || !businessPassed || !humanApproved) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_RAG_PROMOTION_INELIGIBLE", "RAG promotion requires passed technical gates, explicit passed business assertions, and confirmed human approval.", generation.session_id, {
        technicalPassed, businessPassed, humanApproved,
      });
    }
  }

  private ensureLocalIndex(rows: RagDocumentRow[]): void {
    const update = this.database.db.prepare(`
      UPDATE agent_rag_document SET embedding_model = ?, chunker_version = ?, index_version = ?, indexed_sha256 = ?, embedding_json = ?
      WHERE document_key = ?
    `);
    for (const row of rows) {
      if (row.embedding_model === LOCAL_EMBEDDING_MODEL && row.chunker_version === LOCAL_CHUNKER_VERSION
        && row.index_version === LOCAL_INDEX_VERSION && row.indexed_sha256 === row.sha256
        && parseEmbeddings(row.embedding_json).length > 0) continue;
      const embeddings = buildDocumentEmbeddings(`${row.summary}\n${row.relative_path}\n${row.content}`, parseCapabilities(row.capabilities_json));
      row.embedding_model = LOCAL_EMBEDDING_MODEL;
      row.chunker_version = LOCAL_CHUNKER_VERSION;
      row.index_version = LOCAL_INDEX_VERSION;
      row.indexed_sha256 = row.sha256;
      row.embedding_json = JSON.stringify(embeddings);
      update.run(LOCAL_EMBEDDING_MODEL, LOCAL_CHUNKER_VERSION, LOCAL_INDEX_VERSION, row.sha256, row.embedding_json, row.document_key);
    }
  }
}

function rankHybrid(
  query: string,
  capabilities: GenerationContextCapability[],
  rows: RagDocumentRow[],
): RagSearchHit[] {
  const lexical = rankBm25(query, rows);
  const lexicalRanks = new Map(lexical.map((hit, index) => [hit.key, index + 1]));
  const lexicalScores = new Map(lexical.map((hit) => [hit.key, hit.score]));
  const queryEmbedding = embedLocal(query, capabilities);
  const vector = rows.map((row) => ({
    row,
    score: Math.max(0, ...parseEmbeddings(row.embedding_json).map((embedding) => cosineSimilarity(queryEmbedding, embedding))),
  })).filter(({ score }) => score > 0)
    .sort((left, right) => right.score - left.score || left.row.document_key.localeCompare(right.row.document_key));
  const vectorRanks = new Map(vector.map((item, index) => [item.row.document_key, index + 1]));
  const vectorScores = new Map(vector.map((item) => [item.row.document_key, item.score]));
  return rows.map((row) => {
    const lexicalRank = lexicalRanks.get(row.document_key);
    const vectorRank = vectorRanks.get(row.document_key);
    const fused = (lexicalRank ? 1 / (60 + lexicalRank) : 0) + (vectorRank ? 1 / (60 + vectorRank) : 0);
    return {
      key: row.document_key,
      summary: row.summary,
      version: row.contract_version,
      sha256: row.sha256,
      score: Number(fused.toFixed(6)),
      lexicalScore: lexicalScores.get(row.document_key) || 0,
      vectorScore: vectorScores.get(row.document_key) || 0,
      capabilities: parseCapabilities(row.capabilities_json),
    };
  }).filter(({ score }) => score > 0).sort((left, right) => right.score - left.score || left.key.localeCompare(right.key));
}

function rankBm25(query: string, rows: RagDocumentRow[]): RagSearchHit[] {
  if (!rows.length) return [];
  const queryTerms = [...new Set(tokenize(query))];
  const documents = rows.map((row) => tokenize(`${row.summary}\n${row.relative_path}\n${row.content}`));
  const averageLength = documents.reduce((sum, terms) => sum + terms.length, 0) / documents.length || 1;
  const k1 = 1.2;
  const b = 0.75;
  return rows.map((row, index) => {
    const terms = documents[index];
    const frequencies = new Map<string, number>();
    for (const term of terms) frequencies.set(term, (frequencies.get(term) || 0) + 1);
    let score = 0;
    for (const term of queryTerms) {
      const frequency = frequencies.get(term) || 0;
      if (!frequency) continue;
      const containing = documents.filter((document) => document.includes(term)).length;
      const idf = Math.log(1 + (documents.length - containing + 0.5) / (containing + 0.5));
      score += idf * (frequency * (k1 + 1)) / (frequency + k1 * (1 - b + b * terms.length / averageLength));
    }
    return {
      key: row.document_key,
      summary: row.summary,
      version: row.contract_version,
      sha256: row.sha256,
      score: Number(score.toFixed(6)),
      capabilities: parseCapabilities(row.capabilities_json),
    };
  }).filter(({ score }) => score > 0).sort((left, right) => right.score - left.score || left.key.localeCompare(right.key));
}

function tokenize(value: string): string[] {
  const normalized = value.toLowerCase();
  const ascii = normalized.match(/[a-z0-9]+/g) || [];
  const hanRuns = normalized.match(/[\u3400-\u9fff]+/g) || [];
  const han = hanRuns.flatMap((run) => run.length === 1
    ? [run]
    : [...run].slice(0, -1).map((character, index) => `${character}${run[index + 1]}`));
  return [...ascii, ...han];
}

function parseCapabilities(value: string): GenerationContextCapability[] {
  try { return JSON.parse(value) as GenerationContextCapability[]; } catch { return []; }
}

function buildDocumentEmbeddings(value: string, capabilities: GenerationContextCapability[]): number[][] {
  return chunkForLocalEmbedding(value).map((chunk) => embedLocal(chunk, capabilities));
}

function parseEmbeddings(value: string): number[][] {
  try {
    const parsed = JSON.parse(value) as unknown;
    return Array.isArray(parsed) && parsed.every((vector) => Array.isArray(vector) && vector.length === LOCAL_EMBEDDING_DIMENSIONS
      && vector.every((item) => typeof item === "number" && Number.isFinite(item))) ? parsed as number[][] : [];
  } catch { return []; }
}

function bm25Snapshot(): RagSearchResponse["snapshot"] {
  return { retrieverVersion: "BM25_V1", tokenizerVersion: "CJK_BIGRAM_ASCII_V1" };
}

function hybridSnapshot(): RagSearchResponse["snapshot"] {
  return {
    retrieverVersion: "HYBRID_RRF_V1",
    tokenizerVersion: "CJK_BIGRAM_ASCII_V1",
    embedding: {
      model: LOCAL_EMBEDDING_MODEL,
      dimensions: LOCAL_EMBEDDING_DIMENSIONS,
      chunkerVersion: LOCAL_CHUNKER_VERSION,
      indexVersion: LOCAL_INDEX_VERSION,
    },
  };
}

function safeStringArray(value: string): string[] {
  try {
    const parsed = JSON.parse(value) as unknown;
    return Array.isArray(parsed) && parsed.every((item) => typeof item === "string") ? parsed : [];
  } catch { return []; }
}

function hash(value: string | Uint8Array): string {
  return createHash("sha256").update(value).digest("hex");
}
