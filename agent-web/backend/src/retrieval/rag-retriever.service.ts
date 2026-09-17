import { HttpStatus, Inject, Injectable } from "@nestjs/common";
import { createHash, randomUUID } from "node:crypto";
import { existsSync, lstatSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { performance } from "node:perf_hooks";
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
  RagRecipeSelection,
  RagEvidenceSearchRequest,
  RagEvidenceSearchResponse,
  RagQueryIntent,
  RequirementIrDraft,
} from "@flowmind/agent-contracts";
import { AgentError } from "../common/agent-error.js";
import { DatabaseService, type GenerationRow } from "../persistence/database.service.js";
import { deriveAcceptanceAssertions, deriveRequirementIr } from "../requirement/requirement-ir.js";
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

interface PromotedDocument {
  key: string;
  relativePath: string;
  summary: string;
  content: string;
  sha256: string;
  embeddings: number[][];
}

interface RagChunkRow {
  id: string;
  case_id: string;
  node_id: string | null;
  chunk_type: string;
  artifact_path: string | null;
  symbol_name: string | null;
  start_line: number | null;
  end_line: number | null;
  summary: string;
  content: string;
  sha256: string;
  embedding_json: string;
  capabilities_json: string;
  contract_version: string;
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
    let caseId = "";
    let recipeId = "";
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
      ({ caseId, recipeId } = this.promoteCaseV2(
        generation, user, contract, requirement, deriveRequirementIr(requirement), capabilities,
        documents, request, promotedAt,
      ));
    });
    return {
      promoted: true as const,
      generationId,
      revision: generation.generation_revision,
      caseId,
      recipeId,
      documentKeys: documents.map(({ key }) => key),
    };
  }

  selectRecipe(request: {
    generationId: string;
    ownerUserId: string;
    projectId: string;
    contractVersion: string;
    capabilities: GenerationContextCapability[];
  }): RagRecipeSelection | undefined {
    const rows = this.database.db.prepare(`
      SELECT r.id AS recipe_id, r.case_id, r.recipe_key, r.recipe_version,
        r.capabilities_json, c.promoted_at
      FROM agent_rag_recipe r
      JOIN agent_rag_case c ON c.id = r.case_id
      WHERE r.status = 'ACTIVE' AND c.status = 'ACTIVE'
        AND c.project_id = ? AND c.contract_version = ?
        AND (c.owner_user_id = ? OR c.visibility = 'SHARED')
      ORDER BY c.promoted_at DESC, r.id
    `).all(request.projectId, request.contractVersion, request.ownerUserId) as Array<{
      recipe_id: string;
      case_id: string;
      recipe_key: string;
      recipe_version: string;
      capabilities_json: string;
      promoted_at: string;
    }>;
    const requested = [...new Set(request.capabilities)].sort();
    const selected = rows
      .map((row) => ({ row, capabilities: parseCapabilities(row.capabilities_json) }))
      .filter(({ capabilities }) => requested.every((capability) => capabilities.includes(capability)))
      .sort((left, right) => left.capabilities.length - right.capabilities.length
        || right.row.promoted_at.localeCompare(left.row.promoted_at))[0];
    const evidenceNodeIds = selected
      ? (this.database.db.prepare(`
          SELECT id FROM agent_rag_node WHERE case_id = ? AND validation_status = 'VERIFIED'
          ORDER BY CASE node_type WHEN 'RECIPE' THEN 0 WHEN 'REQUIREMENT_ELEMENT' THEN 1 ELSE 2 END, stable_key
          LIMIT 20
        `).all(selected.row.case_id) as Array<{ id: string }>).map(({ id }) => id)
      : [];
    this.database.db.prepare(`
      INSERT INTO agent_generation_knowledge_use (
        id, generation_id, case_id, recipe_id, intent, query_json, evidence_json, selected_at
      ) VALUES (?, ?, ?, ?, 'RECIPE_SELECTION', ?, ?, ?)
    `).run(
      `gku_${randomUUID()}`, request.generationId, selected?.row.case_id || null,
      selected?.row.recipe_id || null,
      JSON.stringify({
        projectId: request.projectId,
        contractVersion: request.contractVersion,
        capabilities: requested,
      }),
      JSON.stringify({ evidenceNodeIds, fallback: selected ? null : "BUILTIN_DETERMINISTIC_IR_V1" }),
      new Date().toISOString(),
    );
    if (!selected) return undefined;
    return {
      caseId: selected.row.case_id,
      recipeId: selected.row.recipe_id,
      recipeKey: selected.row.recipe_key,
      recipeVersion: selected.row.recipe_version,
      capabilities: selected.capabilities,
      evidenceNodeIds,
    };
  }

  searchEvidence(
    request: RagEvidenceSearchRequest,
    user: MockUser,
  ): RagEvidenceSearchResponse {
    const query = request.query?.trim();
    if (!query || !request.projectId || !request.contractVersion) {
      throw new AgentError(HttpStatus.BAD_REQUEST, "AGENT_RAG_QUERY_INVALID", "RAG query, project and contract version are required.");
    }
    const requestedCapabilities = [...new Set(request.capabilities || [])].sort();
    const intent = request.intent || inferIntent(query);
    const ftsTerms = [...new Set(tokenize(query))].slice(0, 24);
    const ftsIds = ftsTerms.length
      ? (this.database.db.prepare(`
          SELECT chunk_id FROM agent_rag_chunk_fts
          WHERE agent_rag_chunk_fts MATCH ? LIMIT 2000
        `).all(ftsTerms.map((term) => `"${term.replaceAll('"', '""')}"`).join(" OR ")) as Array<{ chunk_id: string }>).map(({ chunk_id }) => chunk_id)
      : [];
    const graphNodeIds = ["TRACEABILITY", "IMPACT"].includes(intent)
      ? this.relatedNodeIds(user.userId, request.projectId, request.contractVersion, query)
      : [];
    // Phase 1 deliberately scans the bounded, hard-filtered local corpus for the
    // vector leg. Restricting rows to FTS hits would silently turn "hybrid" into
    // lexical-only retrieval whenever a synonym has no shared token.
    const rows = this.database.db.prepare(`
      SELECT ch.*, c.capabilities_json, c.contract_version
      FROM agent_rag_chunk ch JOIN agent_rag_case c ON c.id = ch.case_id
      WHERE ch.index_status = 'ACTIVE' AND c.status = 'ACTIVE'
        AND c.project_id = ? AND c.contract_version = ?
        AND (c.owner_user_id = ? OR c.visibility = 'SHARED')
      ORDER BY c.promoted_at DESC, ch.id
      LIMIT 2000
    `).all(request.projectId, request.contractVersion, user.userId) as RagChunkRow[];
    const candidates = rows.filter((row) => {
      const capabilities = parseCapabilities(row.capabilities_json);
      return requestedCapabilities.every((capability) => capabilities.includes(capability));
    });
    const graphNodes = new Set(graphNodeIds);
    const ranked = rankHybrid(query, requestedCapabilities, candidates.map(toDocumentRow));
    const rowById = new Map(candidates.map((row) => [row.id, row]));
    const limit = Math.min(Math.max(Math.trunc(request.limit || 8), 1), 20);
    const hits = ranked.map((hit) => {
      const row = rowById.get(hit.key)!;
      const graphBoost = row.node_id && graphNodes.has(row.node_id) ? 1 : 0;
      return {
        key: row.id,
        caseId: row.case_id,
        nodeId: row.node_id || undefined,
        chunkType: row.chunk_type,
        summary: row.summary,
        sha256: row.sha256,
        score: Number((hit.score + graphBoost).toFixed(6)),
        artifactPath: row.artifact_path || undefined,
        symbolName: row.symbol_name || undefined,
        startLine: row.start_line || undefined,
        endLine: row.end_line || undefined,
      };
    }).sort((left, right) => right.score - left.score || left.key.localeCompare(right.key)).slice(0, limit);
    const retrievalId = `retr2_${randomUUID()}`;
    this.database.db.prepare(`
      INSERT INTO agent_rag_v2_retrieval (
        id, owner_user_id, query_text, intent, filters_json, candidate_chunk_ids_json,
        result_chunk_ids_json, ranking_snapshot_json, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      retrievalId, user.userId, query, intent,
      JSON.stringify({
        projectId: request.projectId,
        contractVersion: request.contractVersion,
        capabilities: requestedCapabilities,
      }), JSON.stringify(candidates.map(({ id }) => id)), JSON.stringify(hits.map(({ key }) => key)),
      JSON.stringify({ retrieverVersion: "TYPED_GRAPH_HYBRID_V2", ftsCandidateCount: ftsIds.length, graphNodeCount: graphNodeIds.length }),
      new Date().toISOString(),
    );
    return { retrievalId, intent, hits, retrieverVersion: "TYPED_GRAPH_HYBRID_V2" };
  }

  readEvidence(retrievalId: string, key: string, user: MockUser) {
    const retrieval = this.database.db.prepare(`
      SELECT owner_user_id, result_chunk_ids_json FROM agent_rag_v2_retrieval WHERE id = ?
    `).get(retrievalId) as { owner_user_id: string; result_chunk_ids_json: string } | undefined;
    if (!retrieval || retrieval.owner_user_id !== user.userId || !safeStringArray(retrieval.result_chunk_ids_json).includes(key)) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_RAG_READ_FORBIDDEN", "The key was not returned by this owned retrieval.");
    }
    const row = this.database.db.prepare(`
      SELECT ch.id, ch.case_id, ch.node_id, ch.chunk_type, ch.artifact_path, ch.symbol_name,
        ch.start_line, ch.end_line, ch.content, ch.sha256
      FROM agent_rag_chunk ch JOIN agent_rag_case c ON c.id = ch.case_id
      WHERE ch.id = ? AND ch.index_status = 'ACTIVE' AND c.status = 'ACTIVE'
        AND (c.owner_user_id = ? OR c.visibility = 'SHARED')
    `).get(key, user.userId) as Record<string, unknown> & { content?: string; sha256?: string } | undefined;
    if (!row || !row.content || hash(row.content) !== row.sha256) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_RAG_DOCUMENT_CORRUPT", "Frozen RAG evidence failed its hash check.");
    }
    this.database.db.prepare(`
      INSERT INTO agent_rag_v2_read (id, retrieval_id, chunk_id, chunk_sha256, read_at)
      VALUES (?, ?, ?, ?, ?)
    `).run(`read2_${randomUUID()}`, retrievalId, key, row.sha256, new Date().toISOString());
    return { retrievalId, key, ...row };
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
    const shadowMode = request.shadowMode && request.shadowMode !== mode ? request.shadowMode : undefined;
    if (mode === "HYBRID" || shadowMode === "HYBRID") this.ensureLocalIndex(candidates);
    const snapshot = mode === "BM25" ? bm25Snapshot() : hybridSnapshot();
    const selectedStartedAt = performance.now();
    const hits = rankByMode(mode, query, requestedCapabilities, candidates).slice(0, limit);
    const selectedDurationMs = Number((performance.now() - selectedStartedAt).toFixed(3));
    const shadowStartedAt = performance.now();
    const shadowHits = shadowMode
      ? rankByMode(shadowMode, query, requestedCapabilities, candidates).slice(0, limit)
      : [];
    const shadowDurationMs = shadowMode ? Number((performance.now() - shadowStartedAt).toFixed(3)) : 0;
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
        durationMs: selectedDurationMs,
        scores: hits.map(({ key, score, lexicalScore, vectorScore }) => ({ key, score, lexicalScore, vectorScore })),
        shadow: shadowMode ? {
          mode: shadowMode,
          snapshot: shadowMode === "BM25" ? bm25Snapshot() : hybridSnapshot(),
          resultKeys: shadowHits.map(({ key }) => key),
          durationMs: shadowDurationMs,
          scores: shadowHits.map(({ key, score, lexicalScore, vectorScore }) => ({ key, score, lexicalScore, vectorScore })),
        } : undefined,
      }),
    );
    if (shadowMode) {
      const selectedKeys = hits.map(({ key }) => key);
      const shadowKeys = shadowHits.map(({ key }) => key);
      const overlap = selectedKeys.filter((key) => shadowKeys.includes(key)).length;
      this.database.db.prepare(`
        INSERT INTO agent_rag_shadow_observation (
          id, retrieval_id, selected_mode, shadow_mode, selected_keys_json, shadow_keys_json,
          overlap_count, selected_zero_result, shadow_zero_result, selected_duration_ms,
          shadow_duration_ms, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `).run(
        `shadow_${randomUUID()}`, retrievalId, mode, shadowMode, JSON.stringify(selectedKeys),
        JSON.stringify(shadowKeys), overlap, selectedKeys.length ? 0 : 1, shadowKeys.length ? 0 : 1,
        selectedDurationMs, shadowDurationMs, new Date().toISOString(),
      );
    }
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

  shadowMetrics() {
    const rows = this.database.db.prepare(`
      SELECT COALESCE(g.retrieval_policy_version, 'UNSCOPED') AS policyVersion,
        COALESCE(g.retrieval_release_mode, 'MANUAL') AS releaseMode,
        o.selected_mode AS selectedMode, o.shadow_mode AS shadowMode, COUNT(*) AS observations,
        SUM(CASE WHEN selected_zero_result = 1 AND shadow_zero_result = 0 THEN 1 ELSE 0 END) AS shadowImprovements,
        SUM(CASE WHEN selected_zero_result = 0 AND shadow_zero_result = 1 THEN 1 ELSE 0 END) AS shadowRegressions,
        AVG(overlap_count) AS averageTopKOverlap,
        AVG(selected_duration_ms) AS averageSelectedDurationMs,
        AVG(shadow_duration_ms) AS averageShadowDurationMs
      FROM agent_rag_shadow_observation o
      JOIN agent_rag_retrieval r ON r.id = o.retrieval_id
      LEFT JOIN agent_code_generation g ON g.id = r.generation_id
      GROUP BY COALESCE(g.retrieval_policy_version, 'UNSCOPED'),
        COALESCE(g.retrieval_release_mode, 'MANUAL'), o.selected_mode, o.shadow_mode
      ORDER BY policyVersion, releaseMode, selectedMode, shadowMode
    `).all() as Array<{
      policyVersion: string;
      releaseMode: string;
      selectedMode: string;
      shadowMode: string;
      observations: number;
      shadowImprovements: number;
      shadowRegressions: number;
      averageTopKOverlap: number;
      averageSelectedDurationMs: number;
      averageShadowDurationMs: number;
    }>;
    return {
      generatedAt: new Date().toISOString(),
      groups: rows.map((row) => ({
        ...row,
        averageTopKOverlap: Number((row.averageTopKOverlap || 0).toFixed(3)),
        averageSelectedDurationMs: Number((row.averageSelectedDurationMs || 0).toFixed(3)),
        averageShadowDurationMs: Number((row.averageShadowDurationMs || 0).toFixed(3)),
      })),
    };
  }

  private ownedGeneration(sessionId: string, generationId: string, user: MockUser): GenerationRow {
    const generation = this.database.getGeneration(generationId);
    if (!generation || generation.session_id !== sessionId || generation.created_by !== user.userId) {
      throw new AgentError(HttpStatus.NOT_FOUND, "AGENT_GENERATION_NOT_FOUND", "Generation was not found.", sessionId);
    }
    return generation;
  }

  private relatedNodeIds(ownerUserId: string, projectId: string, contractVersion: string, query: string): string[] {
    const rows = this.database.db.prepare(`
      SELECT n.id, n.case_id, n.stable_key, n.label, n.content_json
      FROM agent_rag_node n JOIN agent_rag_case c ON c.id = n.case_id
      WHERE c.project_id = ? AND c.contract_version = ? AND c.status = 'ACTIVE'
        AND n.validation_status = 'VERIFIED'
        AND (c.owner_user_id = ? OR c.visibility = 'SHARED')
      LIMIT 5000
    `).all(projectId, contractVersion, ownerUserId) as Array<{
      id: string;
      case_id: string;
      stable_key: string;
      label: string;
      content_json: string;
    }>;
    const terms = [...new Set(tokenize(query))];
    const seeds = rows.filter((row) => {
      const searchable = tokenize(`${row.stable_key} ${row.label} ${row.content_json}`);
      return terms.some((term) => searchable.includes(term));
    }).map(({ id }) => id);
    if (!seeds.length) return [];
    const related = this.database.db.prepare(`
      SELECT from_node_id, to_node_id FROM agent_rag_edge
      WHERE validation_status = 'VERIFIED'
        AND (from_node_id IN (${seeds.map(() => "?").join(",")})
          OR to_node_id IN (${seeds.map(() => "?").join(",")}))
      LIMIT 5000
    `).all(...seeds, ...seeds) as Array<{ from_node_id: string; to_node_id: string }>;
    return [...new Set([...seeds, ...related.flatMap(({ from_node_id, to_node_id }) => [from_node_id, to_node_id])])];
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
    const requirement = JSON.parse(generation.requirement_snapshot_json) as BusinessRequirement;
    const requiredAssertionIds = deriveAcceptanceAssertions(deriveRequirementIr(requirement))
      .map(({ assertionId }) => assertionId);
    const providedAssertionIds = new Set(assertions
      .filter(({ assertionId, status }) => Boolean(assertionId?.trim()) && status === "PASSED")
      .map(({ assertionId }) => assertionId));
    const businessPassed = requiredAssertionIds.length > 0
      && requiredAssertionIds.every((assertionId) => providedAssertionIds.has(assertionId));
    const humanApproved = Boolean(generation.confirmed_by && generation.confirmed_at && generation.written_at);
    if (!technicalPassed || !businessPassed || !humanApproved) {
      throw new AgentError(HttpStatus.CONFLICT, "AGENT_RAG_PROMOTION_INELIGIBLE", "RAG promotion requires passed technical gates, explicit passed business assertions, and confirmed human approval.", generation.session_id, {
        technicalPassed, businessPassed, humanApproved,
      });
    }
  }

  private promoteCaseV2(
    generation: GenerationRow,
    user: MockUser,
    contract: GenerationTargetContract,
    requirement: BusinessRequirement,
    ir: RequirementIrDraft,
    capabilities: GenerationContextCapability[],
    documents: PromotedDocument[],
    request: RagPromotionRequest,
    promotedAt: string,
  ): { caseId: string; recipeId: string } {
    const caseId = `case_${hash(`${generation.id}:${generation.generation_revision}`).slice(0, 24)}`;
    const recipeId = `recipe_${hash(`${caseId}:DETERMINISTIC_IR_V1`).slice(0, 24)}`;
    const previous = this.database.db.prepare(`
      SELECT id FROM agent_rag_case
      WHERE owner_user_id = ? AND project_id = ? AND contract_version = ?
        AND business_code = ? AND status = 'ACTIVE'
      ORDER BY promoted_at DESC LIMIT 1
    `).get(user.userId, contract.projectId, contract.contractVersion, generation.business_code) as { id: string } | undefined;
    if (previous?.id === caseId) return { caseId, recipeId };

    this.database.db.prepare(`
      INSERT INTO agent_rag_case (
        id, source_generation_id, source_revision, owner_user_id, project_id, contract_version,
        business_code, visibility, status, requirement_json, requirement_ir_json,
        capabilities_json, validation_evidence_json, supersedes_case_id, promoted_by, promoted_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PRIVATE', 'PENDING', ?, ?, ?, ?, ?, ?, ?)
    `).run(
      caseId, generation.id, generation.generation_revision, user.userId, contract.projectId,
      contract.contractVersion, generation.business_code, JSON.stringify(requirement), JSON.stringify(ir),
      JSON.stringify(capabilities), JSON.stringify({
        generationRevision: request.generationRevision,
        businessAssertions: request.businessAssertions,
        qualityRevision: generation.quality_revision,
        hardGatePassed: Boolean(generation.hard_gate_passed),
        confirmedBy: generation.confirmed_by,
        writtenAt: generation.written_at,
      }), previous?.id || null, user.userId, promotedAt,
    );

    const nodes: Array<{
      id: string;
      type: string;
      key: string;
      label: string;
      content: unknown;
      origin: "EXPLICIT" | "DERIVED";
    }> = [];
    const nodeByKey = new Map<string, string>();
    const addNode = (type: string, key: string, label: string, content: unknown, origin: "EXPLICIT" | "DERIVED") => {
      const id = `node_${hash(`${caseId}:${type}:${key}`).slice(0, 24)}`;
      nodes.push({ id, type, key, label, content, origin });
      nodeByKey.set(`${type}:${key}`, id);
      return id;
    };
    const requirementRoot = addNode("REQUIREMENT_ELEMENT", "requirement", requirement.businessName, ir, "EXPLICIT");
    const requirementElements: Array<{ key: string; label: string; value: unknown; token: string }> = [
      ...ir.fields.map((value) => ({ key: `field:${value.fieldCode}`, label: value.fieldName, value, token: value.fieldCode })),
      ...ir.attachments.map((value) => ({ key: `attachment:${value.attachmentCode}`, label: value.attachmentName, value, token: value.attachmentCode })),
      ...ir.dataQueries.map((value) => ({ key: `query:${value.queryCode}`, label: value.queryCode, value, token: value.queryCode })),
      ...ir.calculations.map((value) => ({ key: `calculation:${value.calculationCode}`, label: value.calculationCode, value, token: value.calculationCode })),
      ...ir.checks.map((value) => ({ key: `check:${value.checkCode}`, label: value.name || value.description, value, token: value.checkCode })),
    ];
    const elementIds = new Map<string, string>();
    for (const element of requirementElements) {
      elementIds.set(element.token, addNode("REQUIREMENT_ELEMENT", element.key, element.label, element.value, "EXPLICIT"));
    }
    const capabilityIds = new Map(capabilities.map((capability) => [
      capability,
      addNode("CAPABILITY", capability, capability, { capability }, "DERIVED"),
    ]));
    const assertions = deriveAcceptanceAssertions(ir);
    const assertionIds = new Map(assertions.map((assertion) => [
      assertion.assertionId,
      addNode("ACCEPTANCE_CRITERION", assertion.assertionId, assertion.description, assertion, "DERIVED"),
    ]));
    const recipeNodeId = addNode("RECIPE", "DETERMINISTIC_IR_V1@1.0", "Deterministic IR recipe", {
      renderer: "DETERMINISTIC_IR_V1",
      recipeVersion: "1.0",
      capabilities,
    }, "DERIVED");

    const artifactIds = new Map<string, string>();
    const testIds: string[] = [];
    const symbolChunks: Array<{
      nodeId: string;
      type: string;
      path: string;
      symbol?: string;
      startLine: number;
      endLine: number;
      summary: string;
      content: string;
    }> = [];
    for (const document of documents) {
      const isTest = /(?:\.spec\.|\.test\.|__tests__)/i.test(document.relativePath);
      const artifactId = addNode(isTest ? "TEST_CASE" : "ARTIFACT", document.relativePath, document.relativePath, {
        relativePath: document.relativePath,
        sha256: document.sha256,
      }, "DERIVED");
      artifactIds.set(document.relativePath, artifactId);
      if (isTest) testIds.push(artifactId);
      for (const symbol of extractSymbolChunks(document.content)) {
        const symbolId = addNode("SYMBOL", `${document.relativePath}#${symbol.name}`, symbol.name, {
          relativePath: document.relativePath,
          symbolName: symbol.name,
          startLine: symbol.startLine,
          endLine: symbol.endLine,
          sha256: hash(symbol.content),
        }, "DERIVED");
        symbolChunks.push({
          nodeId: symbolId,
          type: isTest ? "TEST" : "CODE_SYMBOL",
          path: document.relativePath,
          symbol: symbol.name,
          startLine: symbol.startLine,
          endLine: symbol.endLine,
          summary: `${document.relativePath}#${symbol.name}`,
          content: symbol.content,
        });
      }
    }

    const insertNode = this.database.db.prepare(`
      INSERT INTO agent_rag_node (
        id, case_id, node_type, stable_key, label, content_json, sha256,
        origin, validation_status
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'VERIFIED')
    `);
    for (const node of nodes) {
      const content = JSON.stringify(node.content);
      insertNode.run(node.id, caseId, node.type, node.key, node.label, content, hash(content), node.origin);
    }

    const insertEdge = this.database.db.prepare(`
      INSERT OR IGNORE INTO agent_rag_edge (
        id, case_id, from_node_id, to_node_id, edge_type, source_kind,
        confidence, validation_status, evidence_json
      ) VALUES (?, ?, ?, ?, ?, 'DERIVED', 1, 'VERIFIED', ?)
    `);
    const addEdge = (from: string, to: string, type: string, evidence: unknown) => {
      insertEdge.run(`edge_${hash(`${caseId}:${from}:${to}:${type}`).slice(0, 24)}`, caseId, from, to, type, JSON.stringify(evidence));
    };
    for (const elementId of elementIds.values()) addEdge(requirementRoot, elementId, "decomposes_to", { source: "RequirementIrDraft" });
    for (const capabilityId of capabilityIds.values()) addEdge(requirementRoot, capabilityId, "requires", { source: "detectCapabilities" });
    for (const assertionId of assertionIds.values()) addEdge(requirementRoot, assertionId, "decomposes_to", { source: "deriveAcceptanceAssertions" });
    addEdge(recipeNodeId, requirementRoot, "supported_by", { source: "reviewed case" });
    for (const [path, artifactId] of artifactIds) {
      addEdge(artifactId, recipeNodeId, "generated_from", { path });
      const document = documents.find(({ relativePath }) => relativePath === path)!;
      for (const element of requirementElements) {
        if (document.content.includes(element.token)) addEdge(elementIds.get(element.token)!, artifactId, "implemented_by", { token: element.token, path });
      }
    }
    for (const testId of testIds) addEdge(requirementRoot, testId, "verified_by", { source: "test artifact" });

    const insertChunk = this.database.db.prepare(`
      INSERT INTO agent_rag_chunk (
        id, case_id, node_id, chunk_type, artifact_path, symbol_name,
        start_line, end_line, summary, content, sha256, embedding_json,
        index_version, index_status
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RAG_V2_LOCAL_1', 'PENDING')
    `);
    for (const node of nodes.filter(({ type }) => ["REQUIREMENT_ELEMENT", "ACCEPTANCE_CRITERION", "DECISION"].includes(type))) {
      const content = JSON.stringify(node.content);
      insertChunk.run(
        `chunk_${hash(`${node.id}:${content}`).slice(0, 24)}`, caseId, node.id, "STRUCTURED_REQUIREMENT",
        null, node.key, null, null, node.label, content, hash(content), JSON.stringify([embedLocal(`${node.label}\n${content}`, capabilities)]),
      );
    }
    for (const chunk of symbolChunks) {
      insertChunk.run(
        `chunk_${hash(`${chunk.nodeId}:${chunk.content}`).slice(0, 24)}`, caseId, chunk.nodeId, chunk.type,
        chunk.path, chunk.symbol || null, chunk.startLine, chunk.endLine, chunk.summary, chunk.content,
        hash(chunk.content), JSON.stringify([embedLocal(`${chunk.summary}\n${chunk.content}`, capabilities)]),
      );
    }
    const indexedChunks = this.database.db.prepare(`
      SELECT id, summary, content FROM agent_rag_chunk WHERE case_id = ?
    `).all(caseId) as Array<{ id: string; summary: string; content: string }>;
    const insertFts = this.database.db.prepare(`
      INSERT INTO agent_rag_chunk_fts (chunk_id, search_text) VALUES (?, ?)
    `);
    for (const chunk of indexedChunks) {
      insertFts.run(chunk.id, tokenize(`${chunk.summary}\n${chunk.content}`).join(" "));
    }

    this.database.db.prepare(`
      INSERT INTO agent_rag_recipe (
        id, case_id, recipe_key, recipe_version, capabilities_json,
        selector_json, payload_json, status, created_at
      ) VALUES (?, ?, 'DETERMINISTIC_IR_V1', '1.0', ?, ?, ?, 'ACTIVE', ?)
    `).run(
      recipeId, caseId, JSON.stringify(capabilities),
      JSON.stringify({ projectId: contract.projectId, contractVersion: contract.contractVersion, capabilities }),
      JSON.stringify({ renderer: "DETERMINISTIC_IR_V1", templateVersion: "1.0" }), promotedAt,
    );

    const activeCases = (this.database.db.prepare(`
      SELECT id FROM agent_rag_case
      WHERE owner_user_id = ? AND project_id = ? AND contract_version = ?
        AND status = 'ACTIVE' AND id <> ? ORDER BY id
    `).all(user.userId, contract.projectId, contract.contractVersion, caseId) as Array<{ id: string }>).map(({ id }) => id);
    const caseIds = [...activeCases.filter((id) => id !== previous?.id), caseId].sort();
    const indexId = `idx_${hash(`${user.userId}:${contract.projectId}:${contract.contractVersion}:${caseIds.join(",")}`).slice(0, 24)}`;
    this.database.db.prepare(`
      INSERT INTO agent_rag_index_version (
        id, owner_user_id, project_id, contract_version, version, status,
        case_ids_json, content_sha256, created_at
      ) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)
    `).run(indexId, user.userId, contract.projectId, contract.contractVersion, indexId, JSON.stringify(caseIds), hash(JSON.stringify(caseIds)), promotedAt);
    this.database.db.prepare(`
      UPDATE agent_rag_index_version SET status = 'RETIRED'
      WHERE owner_user_id = ? AND project_id = ? AND contract_version = ? AND status = 'ACTIVE'
    `).run(user.userId, contract.projectId, contract.contractVersion);
    if (previous) {
      this.database.db.prepare("UPDATE agent_rag_case SET status = 'SUPERSEDED' WHERE id = ?").run(previous.id);
      this.database.db.prepare("UPDATE agent_rag_recipe SET status = 'RETIRED' WHERE case_id = ?").run(previous.id);
    }
    this.database.db.prepare("UPDATE agent_rag_chunk SET index_status = 'ACTIVE' WHERE case_id = ?").run(caseId);
    this.database.db.prepare("UPDATE agent_rag_case SET status = 'ACTIVE', activated_at = ? WHERE id = ?").run(promotedAt, caseId);
    this.database.db.prepare("UPDATE agent_rag_index_version SET status = 'ACTIVE', activated_at = ? WHERE id = ?").run(promotedAt, indexId);
    return { caseId, recipeId };
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

function rankByMode(
  mode: "BM25" | "HYBRID",
  query: string,
  capabilities: GenerationContextCapability[],
  rows: RagDocumentRow[],
): RagSearchHit[] {
  return mode === "BM25" ? rankBm25(query, rows) : rankHybrid(query, capabilities, rows);
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

function inferIntent(query: string): RagQueryIntent {
  if (/(影响|变更|依赖|impact|change)/i.test(query)) return "IMPACT";
  if (/(实现|对应|追踪|验收|测试|trace|requirement)/i.test(query)) return "TRACEABILITY";
  if (/^[A-Za-z_$][\w$.:/#-]*$/.test(query)) return "IDENTIFIER";
  return "SIMILAR_CASE";
}

function toDocumentRow(row: RagChunkRow): RagDocumentRow {
  return {
    document_key: row.id,
    project_id: "",
    contract_version: row.contract_version,
    capabilities_json: row.capabilities_json,
    relative_path: row.artifact_path || row.symbol_name || row.id,
    summary: row.summary,
    content: row.content,
    sha256: row.sha256,
    embedding_model: LOCAL_EMBEDDING_MODEL,
    chunker_version: LOCAL_CHUNKER_VERSION,
    index_version: "RAG_V2_LOCAL_1",
    indexed_sha256: row.sha256,
    embedding_json: row.embedding_json,
  };
}

function extractSymbolChunks(content: string): Array<{
  name: string;
  startLine: number;
  endLine: number;
  content: string;
}> {
  const lines = content.split(/\r?\n/);
  const starts: Array<{ name: string; line: number }> = [];
  const symbolPattern = /^\s*(?:export\s+)?(?:default\s+)?(?:async\s+)?(?:function|class|interface|type|const|let)\s+([A-Za-z_$][\w$]*)/;
  for (let index = 0; index < lines.length; index += 1) {
    const match = lines[index].match(symbolPattern);
    if (match) starts.push({ name: match[1], line: index });
  }
  if (!starts.length) {
    return [{ name: "<file>", startLine: 1, endLine: Math.max(lines.length, 1), content }];
  }
  return starts.map((start, index) => {
    const endLineIndex = index + 1 < starts.length ? starts[index + 1].line - 1 : lines.length - 1;
    return {
      name: start.name,
      startLine: start.line + 1,
      endLine: endLineIndex + 1,
      content: lines.slice(start.line, endLineIndex + 1).join("\n"),
    };
  });
}

function hash(value: string | Uint8Array): string {
  return createHash("sha256").update(value).digest("hex");
}
