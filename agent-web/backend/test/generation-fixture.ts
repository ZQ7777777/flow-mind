import { createHash } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { ENTRY_APPLICATION_REQUIREMENT, type GenerationTargetContract } from "@flowmind/agent-contracts";

export function createGenerationTarget(parent: string, name = "business-base"): string {
  const root = join(parent, name);
  const pom = `<project><properties><platform-starter.version>0.1.0-SNAPSHOT</platform-starter.version></properties><dependencies><dependency><groupId>com.flowmind</groupId><artifactId>platform-starter</artifactId><version>\${platform-starter.version}</version></dependency></dependencies></project>\n`;
  const packageJson = `${JSON.stringify({ name: "fixture", private: true, scripts: { typecheck: "tsc", test: "vitest run", build: "vite build" } }, null, 2)}\n`;
  const route = `import type { RouteRecordRaw } from "vue-router";\nexport const generatedRoutes: RouteRecordRaw[] = [];\n`;
  write(root, "backend/pom.xml", pom);
  write(root, "frontend/package.json", packageJson);
  write(root, "frontend/src/router/generated-routes.ts", route);
  write(root, "backend/src/main/java/com/flowmind/business/security/CurrentBusinessUserProvider.java", "package com.flowmind.business.security; public interface CurrentBusinessUserProvider {}\n");
  const contract: GenerationTargetContract = {
    contractVersion: "1.0",
    projectId: "flowmind-business-base",
    backend: {
      rootDir: "backend", javaVersion: "8", springBootVersion: "2.7.18", basePackage: "com.flowmind.business",
      generatedSourceDir: "src/main/java/com/flowmind/business/generated",
      generatedTestDir: "src/test/java/com/flowmind/business/generated",
      starter: { groupId: "com.flowmind", artifactId: "platform-starter", version: "0.1.0-SNAPSHOT", allowedApi: "ProcessRuntimeService#startAndSubmit(StartProcessRequest)" },
      trustedUserContext: { accessorType: "com.flowmind.business.security.CurrentBusinessUserProvider", accessorMethod: "currentUser", userIdProperty: "userId", departmentIdProperty: "departmentId" },
      verificationProfile: "maven-java8",
    },
    frontend: {
      rootDir: "frontend", framework: "vue3", generatedViewDir: "src/modules/generated", generatedApiDir: "src/api/generated",
      generatedTestDir: "src/modules/generated/__tests__", routeRegistry: "src/router/generated-routes.ts", verificationProfile: "vue3-npm",
    },
    readableReferenceFiles: ["backend/pom.xml", "frontend/package.json", "frontend/src/router/generated-routes.ts"],
    allowedOutputPatterns: [
      "backend/src/main/java/com/flowmind/business/generated/**/*.java",
      "backend/src/test/java/com/flowmind/business/generated/**/*.java",
      "frontend/src/modules/generated/**/*",
      "frontend/src/api/generated/**/*",
      "frontend/src/router/generated-routes.ts",
    ],
    protectedFiles: [
      { path: "backend/pom.xml", sha256: hash(pom) },
      { path: "frontend/package.json", sha256: hash(packageJson) },
    ],
  };
  write(root, ".flowmind/generation-target.json", `${JSON.stringify(contract, null, 2)}\n`);
  return root;
}

export function seedActiveWorkflow(database: { db: any }, sessionId: string, targetRoot: string | null, owner = "user_sales"): void {
  const now = new Date().toISOString();
  database.db.prepare(`INSERT INTO agent_session (
    id, owner_user_id, owner_user_name, target_root, state, row_version,
    requirement_revision, requirement_json, requirement_confirmed_at, created_at, updated_at
  ) VALUES (?, ?, 'Sales User', ?, 'PROCESS_ACTIVE', 0, 1, ?, ?, ?, ?)`)
    .run(sessionId, owner, targetRoot, JSON.stringify(ENTRY_APPLICATION_REQUIREMENT), now, now, now);
  database.db.prepare(`INSERT INTO agent_process_definition (
    id, session_id, requirement_revision, platform_definition_id, process_code, process_name,
    status, saga_step, requirement_snapshot_json, platform_snapshot_json,
    create_operation_id, save_operation_id, publish_operation_id, activate_operation_id,
    created_by, created_at, activated_at, updated_at
  ) VALUES (?, ?, 1, 'definition-entry', 'entry_application', '入金申请', 'ACTIVE', 'ACTIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)
    .run(`process_${sessionId}`, sessionId, JSON.stringify(ENTRY_APPLICATION_REQUIREMENT), JSON.stringify({ id: "definition-entry", nodes: ENTRY_APPLICATION_REQUIREMENT.nodes }),
      `create_${sessionId}`, `save_${sessionId}`, `publish_${sessionId}`, `activate_${sessionId}`, owner, now, now, now);
}

export function write(root: string, relativePath: string, content: string): void {
  const path = join(root, ...relativePath.split("/"));
  mkdirSync(join(path, ".."), { recursive: true });
  writeFileSync(path, content, "utf8");
}

function hash(content: string): string { return createHash("sha256").update(content).digest("hex"); }
