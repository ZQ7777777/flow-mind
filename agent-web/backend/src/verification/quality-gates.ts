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
  aiReviewSkipped = false,
): QualityGateDecision {
  const hardGatePassed = stages
    .filter(({ hardGate }) => hardGate)
    .every(({ status }) => status === "PASSED");
  const softFailures: SoftGateScope[] = [];
  if (stages.some(({ stage, status }) => stage === "BACKEND_TESTS" && status !== "PASSED")) {
    softFailures.push("BACKEND_TESTS");
  }
  if (stages.some(({ stage, status }) => stage === "FRONTEND_TESTS" && status !== "PASSED")) {
    softFailures.push("FRONTEND_TESTS");
  }
  if (!aiReviewSkipped && (!review || review.status !== "PASSED" || review.verdict !== "APPROVE")) {
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
  hardFailure: boolean,
  infrastructureFailure: boolean,
  firstUnblockedFailure = false,
): { repair: boolean; nextRound: number; unblockExtension?: true } {
  if (infrastructureFailure || !hardFailure || currentRound >= 4) {
    return { repair: false, nextRound: currentRound };
  }
  if (currentRound === 3) {
    return firstUnblockedFailure
      ? { repair: true, nextRound: 4, unblockExtension: true }
      : { repair: false, nextRound: currentRound };
  }
  return { repair: true, nextRound: currentRound + 1 };
}
