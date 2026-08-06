import { createHash } from "node:crypto";
import type {
  DiagnosticRepairability,
  QualityDiagnostic,
  QualitySeverity,
  QualityStageName,
} from "@flowmind/agent-contracts";

const MAX_EVIDENCE_CHARS = 4_000;

export interface DiagnosticDetails {
  code: string;
  message: string;
  severity?: QualitySeverity;
  hardGate: boolean;
  relativePath?: string;
  line?: number;
  column?: number;
  actual?: string;
  expected?: string;
  evidence?: string;
  repairHint?: string;
  acceptedForms?: string[];
  unsupportedForms?: string[];
  repairability?: DiagnosticRepairability;
  verificationRunId?: string;
}

export function qualityDiagnostic(stage: QualityStageName, details: DiagnosticDetails): QualityDiagnostic {
  const evidence = details.evidence ? sanitizeDiagnosticEvidence(details.evidence) : undefined;
  const relativePath = details.relativePath?.replace(/\\/g, "/");
  const fingerprintEvidence = normalizeFingerprintEvidence(evidence || details.message);
  const diagnosticId = `diag_${createHash("sha256")
    .update([stage, details.code, relativePath || "", fingerprintEvidence].join("\u0000"))
    .digest("hex")
    .slice(0, 24)}`;
  return {
    diagnosticId,
    stage,
    code: details.code,
    message: details.message,
    severity: details.severity || "ERROR",
    hardGate: details.hardGate,
    relativePath,
    line: details.line,
    column: details.column,
    actual: details.actual,
    expected: details.expected,
    evidence,
    repairHint: details.repairHint,
    acceptedForms: details.acceptedForms,
    unsupportedForms: details.unsupportedForms,
    repairability: details.repairability || "CODE_ACTIONABLE",
    verificationRunId: details.verificationRunId,
  };
}

export function sanitizeDiagnosticEvidence(value: string): string {
  const redacted = value
    .replace(/(authorization\s*:\s*bearer\s+)[^\s]+/gi, "$1[REDACTED]")
    .replace(/((?:api[_-]?key|token|password|secret)\s*[=:]\s*)[^\s]+/gi, "$1[REDACTED]")
    .replace(/[A-Za-z]:\\(?:[^\r\n:]+\\)+/g, "[workspace]/")
    .trim();
  if (redacted.length <= MAX_EVIDENCE_CHARS) return redacted;
  return `[earlier output omitted]\n${redacted.slice(-MAX_EVIDENCE_CHARS)}`;
}

function normalizeFingerprintEvidence(value: string): string {
  return value
    .replace(/\bverify_[a-f0-9-]+\b/gi, "<run>")
    .replace(/\b\d+(?:\.\d+)?\s*(?:ms|s)\b/gi, "<duration>")
    .replace(/\r\n/g, "\n")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 2_000);
}
