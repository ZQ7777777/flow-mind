import { resolve } from "node:path";
import type { MockUser } from "@flowmind/agent-contracts";

export interface AgentConfig {
  bindHost: string;
  port: number;
  dataDir: string;
  platformBaseUrl: string;
  piModel: string;
  thinkingLevel: string;
  fakePi: boolean;
  platformTimeoutMs: number;
  allowedTargetRoots: string[];
  compactionModel: string;
  mockUsers: MockUser[];
}

export function loadConfig(): AgentConfig {
  const dataDir = resolve(process.cwd(), process.env.AGENT_DATA_DIR || "../data/agent-web");
  return {
    bindHost: process.env.AGENT_BIND_HOST || "127.0.0.1",
    port: Number(process.env.AGENT_PORT || 3100),
    dataDir,
    platformBaseUrl: (process.env.FLOW_PLATFORM_BASE_URL || "http://127.0.0.1:8080").replace(/\/$/, ""),
    piModel: process.env.PI_MODEL || "",
    thinkingLevel: process.env.PI_THINKING_LEVEL || "medium",
    fakePi: process.env.NODE_ENV === "test" || process.env.AGENT_FAKE_PI === "true",
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
