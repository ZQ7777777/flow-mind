import { resolve } from "node:path";
import type { MockUser } from "@flowmind/agent-contracts";

export interface AgentConfig {
  bindHost: string;
  port: number;
  dataDir: string;
  platformBaseUrl: string;
  businessFrontendBaseUrl: string;
  platformAuthMode: "session" | "trusted-header";
  piModel: string;
  thinkingLevel: string;
  fakePi: boolean;
  generationStrategy: "DETERMINISTIC_IR_V1" | "PI_LEGACY";
  ragReleaseMode: "SHADOW" | "CANARY" | "HYBRID_DEFAULT" | "BM25_ONLY";
  ragCanaryPercent: number;
  ragPolicyVersion: string;
  ragForceBm25: boolean;
  ragV2Mode: "OFF" | "SHADOW" | "DEFAULT";
  modelBudgetTotalCny: number;
  modelBudgetLocked: boolean;
  modelPriceInputCnyPerMillion: number;
  modelPriceOutputCnyPerMillion: number;
  modelPriceVersion: string;
  modelMaxOutputTokens: number;
  platformTimeoutMs: number;
  allowedTargetRoots: string[];
  compactionModel: string;
  mockUsers: MockUser[];
}

export function loadConfig(): AgentConfig {
  const dataDir = resolve(process.cwd(), process.env.AGENT_DATA_DIR || "../data/agent-web");
  const platformAuthMode = resolvePlatformAuthMode();
  const fakePi = process.env.NODE_ENV === "test" || process.env.AGENT_FAKE_PI === "true";
  return {
    bindHost: process.env.AGENT_BIND_HOST || "127.0.0.1",
    port: Number(process.env.AGENT_PORT || 3100),
    dataDir,
    platformBaseUrl: (process.env.FLOW_PLATFORM_BASE_URL || "http://127.0.0.1:8080").replace(/\/$/, ""),
    businessFrontendBaseUrl: (process.env.BUSINESS_BASE_FRONTEND_URL || "http://127.0.0.1:5174").replace(/\/$/, ""),
    platformAuthMode,
    piModel: process.env.PI_MODEL || "",
    thinkingLevel: process.env.PI_THINKING_LEVEL || "medium",
    fakePi,
    generationStrategy: resolveGenerationStrategy(fakePi),
    ragReleaseMode: resolveRagReleaseMode(),
    ragCanaryPercent: resolvePercentage("AGENT_RAG_CANARY_PERCENT", 10),
    ragPolicyVersion: process.env.AGENT_RAG_POLICY_VERSION?.trim() || "RAG_RELEASE_V1",
    ragForceBm25: process.env.AGENT_RAG_FORCE_BM25 === "true",
    ragV2Mode: resolveRagV2Mode(),
    modelBudgetTotalCny: resolveNonNegativeNumber("AGENT_MODEL_BUDGET_CNY", 10),
    modelBudgetLocked: process.env.AGENT_MODEL_BUDGET_LOCKED !== "false",
    modelPriceInputCnyPerMillion: resolveNonNegativeNumber("AGENT_MODEL_INPUT_CNY_PER_MILLION", 0),
    modelPriceOutputCnyPerMillion: resolveNonNegativeNumber("AGENT_MODEL_OUTPUT_CNY_PER_MILLION", 0),
    modelPriceVersion: process.env.AGENT_MODEL_PRICE_VERSION?.trim() || "UNCONFIGURED",
    modelMaxOutputTokens: resolvePositiveInteger("AGENT_MODEL_MAX_OUTPUT_TOKENS", 1024),
    platformTimeoutMs: Number(process.env.FLOW_PLATFORM_TIMEOUT_MS || 15000),
    allowedTargetRoots: (process.env.AGENT_ALLOWED_TARGET_ROOTS || "")
      .split(process.platform === "win32" ? ";" : ":")
      .map((value) => value.trim())
      .filter(Boolean)
      .map((value) => resolve(value)),
    compactionModel: process.env.PI_COMPACTION_MODEL || process.env.PI_MODEL || "",
    mockUsers: [
      {
        userId: "user_sales",
        userName: "Sales User",
        departmentId: "sales_dept",
        departmentName: "Sales Department",
      },
      {
        userId: "user_manager",
        userName: "Department Manager",
        departmentId: "sales_dept",
        departmentName: "Sales Department",
      },
      {
        userId: "user_finance",
        userName: "Finance User",
        departmentId: "finance_dept",
        departmentName: "Finance Department",
      },
      {
        userId: "user_tester",
        userName: "Quality Tester",
        departmentId: "quality_dept",
        departmentName: "Quality Engineering",
      },
    ],
  };
}

function resolveRagV2Mode(): AgentConfig["ragV2Mode"] {
  const configured = process.env.AGENT_RAG_V2_MODE?.trim() || "SHADOW";
  if (["OFF", "SHADOW", "DEFAULT"].includes(configured)) return configured as AgentConfig["ragV2Mode"];
  throw new Error("AGENT_RAG_V2_MODE must be OFF, SHADOW or DEFAULT");
}

function resolveNonNegativeNumber(name: string, fallback: number): number {
  const raw = process.env[name]?.trim();
  const value = raw ? Number(raw) : fallback;
  if (!Number.isFinite(value) || value < 0) throw new Error(`${name} must be a non-negative number`);
  return value;
}

function resolvePositiveInteger(name: string, fallback: number): number {
  const raw = process.env[name]?.trim();
  const value = raw ? Number(raw) : fallback;
  if (!Number.isInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer`);
  return value;
}

function resolveRagReleaseMode(): AgentConfig["ragReleaseMode"] {
  const configured = process.env.AGENT_RAG_RELEASE_MODE?.trim() || "SHADOW";
  if (["SHADOW", "CANARY", "HYBRID_DEFAULT", "BM25_ONLY"].includes(configured)) {
    return configured as AgentConfig["ragReleaseMode"];
  }
  throw new Error("AGENT_RAG_RELEASE_MODE must be SHADOW, CANARY, HYBRID_DEFAULT or BM25_ONLY");
}

function resolvePercentage(name: string, fallback: number): number {
  const raw = process.env[name]?.trim();
  const value = raw ? Number(raw) : fallback;
  if (!Number.isInteger(value) || value < 0 || value > 100) throw new Error(`${name} must be an integer from 0 to 100`);
  return value;
}

function resolveGenerationStrategy(fakePi: boolean): "DETERMINISTIC_IR_V1" | "PI_LEGACY" {
  const configured = process.env.AGENT_GENERATION_STRATEGY?.trim();
  // Existing Fake Pi integration tests continue to exercise the full session
  // protocol unless they explicitly select the deterministic strategy.
  if (!configured) return fakePi ? "PI_LEGACY" : "DETERMINISTIC_IR_V1";
  if (configured === "DETERMINISTIC_IR_V1" || configured === "PI_LEGACY") return configured;
  throw new Error("AGENT_GENERATION_STRATEGY must be DETERMINISTIC_IR_V1 or PI_LEGACY");
}

function resolvePlatformAuthMode(): "session" | "trusted-header" {
  const configured = process.env.FLOW_PLATFORM_AUTH_MODE?.trim();
  if (!configured) return process.env.NODE_ENV === "test" ? "trusted-header" : "session";
  if (configured === "session" || configured === "trusted-header") return configured;
  throw new Error("FLOW_PLATFORM_AUTH_MODE must be session or trusted-header");
}
