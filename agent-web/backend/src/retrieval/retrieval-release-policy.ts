import { createHash } from "node:crypto";
import type { AgentConfig } from "../config.js";

export interface RetrievalReleaseDecision {
  policyVersion: string;
  releaseMode: AgentConfig["ragReleaseMode"];
  selectedMode: "BM25" | "HYBRID";
  shadowMode?: "BM25" | "HYBRID";
  bucket: number;
  forcedFallback: boolean;
}

export function selectRetrievalRelease(
  subjectId: string,
  config: Pick<AgentConfig, "ragReleaseMode" | "ragCanaryPercent" | "ragPolicyVersion" | "ragForceBm25">,
): RetrievalReleaseDecision {
  const bucket = stableBucket(`${config.ragPolicyVersion}:${subjectId}`);
  if (config.ragForceBm25 || config.ragReleaseMode === "BM25_ONLY") {
    return {
      policyVersion: config.ragPolicyVersion,
      releaseMode: config.ragReleaseMode,
      selectedMode: "BM25",
      bucket,
      forcedFallback: true,
    };
  }
  if (config.ragReleaseMode === "SHADOW") {
    return {
      policyVersion: config.ragPolicyVersion,
      releaseMode: "SHADOW",
      selectedMode: "BM25",
      shadowMode: "HYBRID",
      bucket,
      forcedFallback: false,
    };
  }
  const selectedMode = config.ragReleaseMode === "HYBRID_DEFAULT"
    || bucket < config.ragCanaryPercent ? "HYBRID" : "BM25";
  return {
    policyVersion: config.ragPolicyVersion,
    releaseMode: config.ragReleaseMode,
    selectedMode,
    shadowMode: selectedMode === "HYBRID" ? "BM25" : "HYBRID",
    bucket,
    forcedFallback: false,
  };
}

function stableBucket(value: string): number {
  return Number.parseInt(createHash("sha256").update(value).digest("hex").slice(0, 8), 16) % 100;
}
