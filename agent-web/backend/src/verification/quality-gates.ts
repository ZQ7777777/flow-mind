import type {
  CodeReviewReport,
  QualityOverrideSummary,
  QualityStageResult,
} from "@flowmind/agent-contracts";

export type SoftGateScope = "BACKEND_TESTS" | "FRONTEND_TESTS" | "REVIEWER";

export interface QualityGateDecision {
  hardGatePassed: boolean;
  softFailures: SoftGateScope[];
  overrideRequired: boolean;
  canWrite: boolean;
}

export function evaluateQualityGates(
  stages: QualityStageResult[],
  review: CodeReviewReport | undefined,
  overriddenScopes: SoftGateScope[] | QualityOverrideSummary["scopes"],
  _aiReviewSkipped = false,
): QualityGateDecision {
  const hardGatePassed = stages
    .filter(({ hardGate }) => hardGate)
    .every(({ status }) => status === "PASSED");
  const softFailures: SoftGateScope[] = [];
  if (stages.some(({ stage, status, blockedBy }) => stage === "BACKEND_TESTS"
    && status !== "PASSED" && !(status === "SKIPPED" && !blockedBy?.length))) {
    softFailures.push("BACKEND_TESTS");
  }
  if (stages.some(({ stage, status, blockedBy }) => stage === "FRONTEND_TESTS"
    && status !== "PASSED" && !(status === "SKIPPED" && !blockedBy?.length))) {
    softFailures.push("FRONTEND_TESTS");
  }
  if (review?.issues.some(({ severity }) => severity === "BLOCKING")) {
    softFailures.push("REVIEWER");
  }
  const overridden = new Set(overriddenScopes);
  const allSoftFailuresOverridden = softFailures.every((scope) => overridden.has(scope));
  return {
    hardGatePassed,
    softFailures,
    overrideRequired: hardGatePassed && softFailures.length > 0 && !allSoftFailuresOverridden,
    canWrite: hardGatePassed && (softFailures.length === 0 || allSoftFailuresOverridden),
  };
}

export function nextRepairDecision(
  currentRound: number,
  needsRepair: boolean,
  infrastructureFailure: boolean,
  _firstUnblockedFailure = false,
): { repair: boolean; nextRound: number } {
  if (infrastructureFailure || !needsRepair || currentRound >= 3) {
    return { repair: false, nextRound: currentRound };
  }
  return { repair: true, nextRound: currentRound + 1 };
}
