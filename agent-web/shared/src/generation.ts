export interface GenerationTargetContract {
  contractVersion: "1.0" | "1.1" | "2.0" | "2.1";
  generationMode?: "FULL_STACK" | "FRONTEND_FORM_ONLY" | "FRONTEND_ONLY";
  projectId: string;
  backend?: {
    rootDir: string;
    javaVersion: "8";
    springBootVersion: "2.7.18";
    basePackage: string;
    generatedSourceDir: string;
    generatedTestDir: string;
    starter: {
      groupId: "com.flowmind";
      artifactId: "platform-starter";
      version: "0.1.0-SNAPSHOT";
      allowedApi: "ProcessRuntimeService#startAndSubmit(StartProcessRequest)";
    };
    trustedUserContext: {
      accessorType: string;
      accessorMethod: string;
      userIdProperty: string;
      departmentIdProperty: string;
    };
    apiReferences?: {
      platformRuntime: string;
      trustedUserContext: string;
    };
    verificationProfile: "maven-java8";
  };
  frontend: {
    rootDir: string;
    framework: "vue3";
    generatedModuleDir?: string;
    generatedViewDir?: string;
    generatedApiDir?: string;
    generatedTestDir?: string;
    routeRegistry: string;
    sharedStartShell?: string;
    sharedWorkflowTypes?: string;
    exampleReferenceFiles?: string[];
    apiReferences?: {
      businessReferenceData?: string;
    };
    verificationProfile: "vue3-npm";
  };
  readableReferenceFiles: string[];
  allowedOutputPatterns: string[];
  protectedFiles: Array<{ path: string; sha256: string }>;
}

export interface GenerationSkillSnapshot {
  name: string;
  source: "REPOSITORY";
  required: boolean;
  priority: number;
  sha256: string;
  files: Array<{
    relativePath: string;
    sha256: string;
    content: string;
  }>;
}

export interface GenerationReferenceSnapshot {
  source: "REPOSITORY" | "TARGET";
  relativePath: string;
  required: boolean;
  sha256: string;
  content: string;
}

export type GenerationContextCapability =
  | "BASE_FORM"
  | "MULTI_SELECT"
  | "DYNAMIC_REFERENCE"
  | "CASCADE"
  | "DATA_QUERY"
  | "CALCULATION"
  | "BUSINESS_CHECK";

export interface RagBusinessAssertionEvidence {
  assertionId: string;
  status: "PASSED";
}

export interface RagPromotionRequest {
  generationRevision: number;
  businessAssertions: RagBusinessAssertionEvidence[];
}

export interface RagSearchRequest {
  query: string;
  projectId: string;
  contractVersion: string;
  capabilities: GenerationContextCapability[];
  limit?: number;
  generationId?: string;
  mode?: "BM25" | "HYBRID";
  /** Computed and audited, but never returned to the generation model. */
  shadowMode?: "BM25" | "HYBRID";
}

/** Search intentionally returns no source text; callers must explicitly read an audited key. */
export interface RagSearchHit {
  key: string;
  summary: string;
  version: string;
  sha256: string;
  score: number;
  lexicalScore?: number;
  vectorScore?: number;
  capabilities: GenerationContextCapability[];
}

export interface RagSearchResponse {
  retrievalId: string;
  hits: RagSearchHit[];
  snapshot: {
    retrieverVersion: "BM25_V1" | "HYBRID_RRF_V1";
    tokenizerVersion: "CJK_BIGRAM_ASCII_V1";
    embedding?: {
      model: "LOCAL_SEMANTIC_HASH_V1";
      dimensions: 256;
      chunkerVersion: "CODEPOINT_800_OVERLAP_100_V1";
      indexVersion: "LOCAL_RAG_INDEX_V1";
    };
  };
}

export interface RagDocumentContent {
  retrievalId: string;
  key: string;
  version: string;
  sha256: string;
  content: string;
}

export interface GenerationContextRouteItem {
  key: string;
  required: boolean;
  reasons: GenerationContextCapability[];
}

export interface TypeScriptContractDeclaration {
  kind: "FUNCTION" | "INTERFACE" | "TYPE" | "PROPS";
  name: string;
  signature: string;
}

export interface TypeScriptContractSnapshot {
  relativePath: string;
  sha256: string;
  declarations: TypeScriptContractDeclaration[];
}

export interface GenerationContextSnapshot {
  version: "1.0";
  sha256: string;
  skills: GenerationSkillSnapshot[];
  references: GenerationReferenceSnapshot[];
  routing?: {
    version: "1.0";
    capabilities: GenerationContextCapability[];
    items: GenerationContextRouteItem[];
  };
  interfaces?: TypeScriptContractSnapshot[];
}

export interface GenerationContextSummary {
  sha256: string;
  skills: Array<{ name: string; sha256: string }>;
  references: Array<{ source: "REPOSITORY" | "TARGET"; relativePath: string; sha256: string }>;
  routing?: GenerationContextSnapshot["routing"];
  interfaces?: TypeScriptContractSnapshot[];
  reads?: Array<{ key: string; readAt: string }>;
}

export type ArtifactChangeType = "ADD" | "MODIFY";
export type ArtifactValidationStatus = "PENDING" | "VALID" | "INVALID";

export interface ArtifactFile {
  relativePath: string;
  changeType: ArtifactChangeType;
  stagedSha256: string;
  baseSha256?: string;
  sizeBytes: number;
  validationStatus: ArtifactValidationStatus;
  editedByUser: boolean;
}

export interface ArtifactManifest {
  generationId: string;
  targetRoot: string;
  contractVersion: string;
  revision: number;
  files: ArtifactFile[];
}

export const QUALITY_STAGE_NAMES = [
  "STATIC_VALIDATION",
  "BACKEND_COMPILE",
  "BACKEND_TESTS",
  "FRONTEND_TYPECHECK",
  "FRONTEND_TESTS",
  "FRONTEND_BUILD",
] as const;

export type QualityStageName = (typeof QUALITY_STAGE_NAMES)[number];
export type QualityStageStatus =
  | "PENDING"
  | "RUNNING"
  | "PASSED"
  | "FAILED"
  | "SKIPPED"
  | "INFRASTRUCTURE_FAILED"
  | "CANCELLED";
export type QualitySeverity = "ERROR" | "WARNING" | "INFO";
export type DiagnosticRepairability = "CODE_ACTIONABLE" | "INFRASTRUCTURE" | "PROTECTED_FILE" | "UNKNOWN";
export type DiagnosticClassification = "NEW" | "PERSISTING" | "RESOLVED" | "BLOCKED";
export type DiagnosticScope = "CURRENT_GENERATION" | "PRE_EXISTING" | "INTEGRATION_IMPACT";

export interface QualityDiagnostic {
  /** Stable fingerprint; optional while older persisted reports are still readable. */
  diagnosticId?: string;
  /** Content fingerprint used to correlate this finding across verification runs. */
  fingerprint?: string;
  classification?: DiagnosticClassification;
  scope?: DiagnosticScope;
  stage?: QualityStageName;
  code: string;
  message: string;
  severity: QualitySeverity;
  hardGate: boolean;
  relativePath?: string;
  line?: number;
  column?: number;
  actual?: string;
  expected?: string;
  evidence?: string;
  command?: string;
  exitCode?: number;
  repairHint?: string;
  acceptedForms?: string[];
  unsupportedForms?: string[];
  repairability?: DiagnosticRepairability;
  verificationRunId?: string;
  /** Fingerprints of primary diagnostics that already explain this finding. */
  derivedFrom?: string[];
}

export interface QualityStageResult {
  stage: QualityStageName;
  status: QualityStageStatus;
  hardGate: boolean;
  summary: string;
  diagnostics: QualityDiagnostic[];
  startedAt?: string;
  completedAt?: string;
  durationMs?: number;
  command?: string;
  exitCode?: number;
  logPath?: string;
  outputTruncated?: boolean;
  /** Earlier stages that made this stage unsafe or meaningless to execute. */
  blockedBy?: QualityStageName[];
}

export interface CodeReviewIssue {
  diagnosticId?: string;
  code: string;
  title: string;
  message: string;
  severity: "BLOCKING" | "WARNING" | "INFO";
  relativePath?: string;
  line?: number;
  evidence?: string;
  repairHint?: string;
  repairability?: DiagnosticRepairability;
}

export interface RepairResolution {
  diagnosticId: string;
  status: "RESOLVED" | "BLOCKED";
  changedFiles: string[];
  explanation: string;
}

export interface RepairAttemptSummary {
  round: number;
  verificationRunId: string;
  changedFiles: string[];
  resolutions: RepairResolution[];
  diagnosticIds: string[];
  outcome: "CHANGED" | "NO_EFFECT" | "INFRASTRUCTURE_FAILED";
  failureCode?: "REPAIR_NO_EFFECT" | "REPAIR_PROTOCOL_INVALID";
  createdAt: string;
}

export interface CodeReviewReport {
  reviewId: string;
  status: "PENDING" | "RUNNING" | "PASSED" | "FAILED" | "INFRASTRUCTURE_FAILED";
  verdict: "APPROVE" | "CHANGES_REQUESTED" | "UNAVAILABLE";
  summary: string;
  issues: CodeReviewIssue[];
  createdAt: string;
  completedAt?: string;
}

export interface QualityOverrideSummary {
  overrideId: string;
  revision: number;
  scopes: Array<"BACKEND_TESTS" | "FRONTEND_TESTS" | "REVIEWER">;
  reason: string;
  createdBy: string;
  createdAt: string;
}

export interface GenerationQualityReport {
  generationId: string;
  revision: number;
  pipelineState: "VERIFYING" | "REVIEWING" | "REPAIRING" | "PASSED" | "FAILED" | "CANCELLED";
  repairRound: number;
  maxRepairRounds: 3;
  unblockExtensionUsed?: boolean;
  stages: QualityStageResult[];
  /** Findings present in the preceding run and absent from this verified run. */
  resolvedDiagnostics?: QualityDiagnostic[];
  review?: CodeReviewReport;
  repairAttempts?: RepairAttemptSummary[];
  /** Set only when the owner explicitly starts this run without AI review. */
  aiReviewSkipped?: boolean;
  hardGatePassed: boolean;
  overrideRequired: boolean;
  canWrite: boolean;
  override?: QualityOverrideSummary;
  updatedAt: string;
}

export type CodeGenerationStatus =
  | "GENERATING"
  | "VERIFYING"
  | "REVIEWING"
  | "REPAIRING"
  | "REVIEW"
  | "WRITING"
  | "WRITE_FAILED"
  | "CONFIGURING_ENTRY"
  | "ENTRY_CONFIG_FAILED"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED"
  | "SUPERSEDED";

export interface CodeGenerationSummary {
  generationId: string;
  generationStrategy?: "DETERMINISTIC_IR_V1" | "PI_LEGACY";
  retrievalRelease?: {
    policyVersion: string;
    releaseMode: "SHADOW" | "CANARY" | "HYBRID_DEFAULT" | "BM25_ONLY";
    selectedMode: "BM25" | "HYBRID";
    shadowMode?: "BM25" | "HYBRID";
    bucket: number;
    forcedFallback: boolean;
  };
  status: CodeGenerationStatus;
  generationRevision: number;
  targetRoot: string;
  contractVersion: string;
  manifest?: ArtifactManifest;
  quality?: GenerationQualityReport;
  context?: GenerationContextSummary;
  /** Present on backend responses and always false for FRONTEND_ONLY generation. */
  backendRestartRequired: false;
  lastError?: { code: string; message: string };
  createdAt: string;
  updatedAt: string;
}

export interface GeneratedFileContent {
  generationId: string;
  generationRevision: number;
  relativePath: string;
  content: string;
  sha256: string;
}

export interface GeneratedFileDiff {
  generationId: string;
  generationRevision: number;
  relativePath: string;
  changeType: ArtifactChangeType;
  baseSha256?: string;
  stagedSha256: string;
  stale: boolean;
  originalContent: string;
  stagedContent: string;
  unifiedDiff: string;
}

const relativePath = { type: "string", minLength: 1, pattern: "^(?![A-Za-z]:|/|\\\\)(?!.*(?:^|/)\\.\\.(?:/|$)).+$" } as const;

export const generationTargetContractSchema = {
  $id: "GenerationTargetContract",
  type: "object",
  additionalProperties: false,
  required: ["contractVersion", "projectId", "frontend", "readableReferenceFiles", "allowedOutputPatterns", "protectedFiles"],
  properties: {
    contractVersion: { enum: ["1.0", "1.1", "2.0", "2.1"] },
    generationMode: { enum: ["FULL_STACK", "FRONTEND_FORM_ONLY", "FRONTEND_ONLY"] },
    projectId: { type: "string", minLength: 1 },
    backend: {
      type: "object", additionalProperties: false,
      required: ["rootDir", "javaVersion", "springBootVersion", "basePackage", "generatedSourceDir", "generatedTestDir", "starter", "trustedUserContext", "verificationProfile"],
      properties: {
        rootDir: relativePath,
        javaVersion: { const: "8" },
        springBootVersion: { const: "2.7.18" },
        basePackage: { type: "string", pattern: "^[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)+$" },
        generatedSourceDir: relativePath,
        generatedTestDir: relativePath,
        starter: {
          type: "object", additionalProperties: false,
          required: ["groupId", "artifactId", "version", "allowedApi"],
          properties: {
            groupId: { const: "com.flowmind" }, artifactId: { const: "platform-starter" },
            version: { const: "0.1.0-SNAPSHOT" },
            allowedApi: { const: "ProcessRuntimeService#startAndSubmit(StartProcessRequest)" },
          },
        },
        trustedUserContext: {
          type: "object", additionalProperties: false,
          required: ["accessorType", "accessorMethod", "userIdProperty", "departmentIdProperty"],
          properties: {
            accessorType: { type: "string", minLength: 1 }, accessorMethod: { type: "string", minLength: 1 },
            userIdProperty: { type: "string", minLength: 1 }, departmentIdProperty: { type: "string", minLength: 1 },
          },
        },
        apiReferences: {
          type: "object", additionalProperties: false,
          required: ["platformRuntime", "trustedUserContext"],
          properties: { platformRuntime: relativePath, trustedUserContext: relativePath },
        },
        verificationProfile: { const: "maven-java8" },
      },
    },
    frontend: {
      type: "object", additionalProperties: false,
      required: ["rootDir", "framework", "routeRegistry", "verificationProfile"],
      properties: {
        rootDir: relativePath, framework: { const: "vue3" }, generatedViewDir: relativePath,
        generatedModuleDir: relativePath,
        generatedApiDir: relativePath, generatedTestDir: relativePath, routeRegistry: relativePath,
        sharedStartShell: relativePath,
        sharedWorkflowTypes: relativePath,
        exampleReferenceFiles: { type: "array", uniqueItems: true, items: relativePath },
        apiReferences: {
          type: "object", additionalProperties: false,
          properties: { businessReferenceData: relativePath },
        },
        verificationProfile: { const: "vue3-npm" },
      },
    },
    readableReferenceFiles: { type: "array", minItems: 1, uniqueItems: true, items: relativePath },
    allowedOutputPatterns: { type: "array", minItems: 1, uniqueItems: true, items: relativePath },
    protectedFiles: {
      type: "array", minItems: 1,
      items: {
        type: "object", additionalProperties: false, required: ["path", "sha256"],
        properties: { path: relativePath, sha256: { type: "string", pattern: "^[a-fA-F0-9]{64}$" } },
      },
    },
  },
  allOf: [{
    if: { properties: { generationMode: { const: "FRONTEND_ONLY" } }, required: ["generationMode"] },
    then: {
      properties: {
        contractVersion: { const: "2.1" },
        frontend: { required: ["generatedModuleDir", "generatedApiDir", "sharedStartShell", "sharedWorkflowTypes", "exampleReferenceFiles"] },
      },
      not: { required: ["backend"] },
    },
  }, {
    if: { properties: { generationMode: { const: "FRONTEND_FORM_ONLY" } }, required: ["generationMode"] },
    then: {
      properties: {
        contractVersion: { const: "2.0" },
        frontend: { required: ["generatedModuleDir", "sharedStartShell", "sharedWorkflowTypes"] },
      },
    },
  }, {
    if: { properties: { contractVersion: { const: "1.1" } }, required: ["contractVersion"] },
    then: { required: ["backend"], properties: { backend: { required: ["apiReferences"] } } },
  }],
} as const;
