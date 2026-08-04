export interface GenerationTargetContract {
  contractVersion: "1.0";
  projectId: string;
  backend: {
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
    verificationProfile: "maven-java8";
  };
  frontend: {
    rootDir: string;
    framework: "vue3";
    generatedViewDir: string;
    generatedApiDir: string;
    generatedTestDir: string;
    routeRegistry: string;
    verificationProfile: "vue3-npm";
  };
  readableReferenceFiles: string[];
  allowedOutputPatterns: string[];
  protectedFiles: Array<{ path: string; sha256: string }>;
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

export type CodeGenerationStatus = "GENERATING" | "REVIEW" | "FAILED" | "CANCELLED" | "SUPERSEDED";

export interface CodeGenerationSummary {
  generationId: string;
  status: CodeGenerationStatus;
  generationRevision: number;
  targetRoot: string;
  contractVersion: string;
  manifest?: ArtifactManifest;
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
  required: ["contractVersion", "projectId", "backend", "frontend", "readableReferenceFiles", "allowedOutputPatterns", "protectedFiles"],
  properties: {
    contractVersion: { const: "1.0" },
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
        verificationProfile: { const: "maven-java8" },
      },
    },
    frontend: {
      type: "object", additionalProperties: false,
      required: ["rootDir", "framework", "generatedViewDir", "generatedApiDir", "generatedTestDir", "routeRegistry", "verificationProfile"],
      properties: {
        rootDir: relativePath, framework: { const: "vue3" }, generatedViewDir: relativePath,
        generatedApiDir: relativePath, generatedTestDir: relativePath, routeRegistry: relativePath,
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
} as const;
