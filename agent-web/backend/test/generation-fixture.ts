import { createHash } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { ENTRY_APPLICATION_REQUIREMENT, type BusinessRequirement, type GenerationTargetContract } from "@flowmind/agent-contracts";

export function createGenerationTarget(parent: string, name = "business-base"): string {
  const root = join(parent, name);
  const packageJson = `${JSON.stringify({ name: "fixture", private: true, scripts: { typecheck: "node -e \"\"", test: "node -e \"\"", build: "node -e \"\"" } }, null, 2)}\n`;
  const route = `import type { RouteRecordRaw } from "vue-router";\nexport const generatedRoutes: RouteRecordRaw[] = [{ path: "/existing", name: "existing-route", component: () => import("../modules/Existing.vue"), meta: { standalone: true } }];\nexport const generatedBusinessFormRegistry: Record<string, () => Promise<unknown>> = {};\n`;
  const startShell = `<script setup lang="ts">defineProps<{ processCode: string }>();</script><template><main><slot /></main></template>\n`;
  const workflowTypes = `export interface WorkflowFormField { fieldCode: string; fieldName: string; required: boolean; visible: boolean; editable: boolean }\nexport interface WorkflowFieldPermission { fieldCode: string; required?: boolean; visible?: boolean; editable?: boolean }\n`;
  const examples: Record<string, string> = {
    "frontend/src/modules/generated/sample/BusinessForm.vue": `<script setup lang="ts">defineProps<{ modelValue: Record<string, unknown> }>();</script><template><section>sample form</section></template>\n`,
    "frontend/src/modules/generated/sample/Apply.vue": `<script setup lang="ts">import WorkflowStartShell from "../../../components/workflow/WorkflowStartShell.vue";</script><template><WorkflowStartShell process-code="warehouse_pledge" /></template>\n`,
    "frontend/src/modules/generated/sample/__tests__/BusinessForm.spec.ts": `import { describe, it } from "vitest"; describe("sample", () => { it("renders", () => undefined); });\n`,
    "frontend/src/modules/generated/sample/__tests__/Apply.spec.ts": `import { describe, it } from "vitest"; describe("sample", () => { it("delegates", () => undefined); });\n`,
    "frontend/src/api/generated/sample/sample.ts": `export async function getReference(): Promise<unknown> { return {}; }\n`,
    "frontend/src/api/generated/sample/sample.spec.ts": `import { describe, it } from "vitest"; describe("sample api", () => { it("is read only", () => undefined); });\n`,
  };
  write(root, "frontend/package.json", packageJson);
  write(root, "frontend/src/router/generated-routes.ts", route);
  write(root, "frontend/src/components/workflow/WorkflowStartShell.vue", startShell);
  write(root, "frontend/src/types/workflow.ts", workflowTypes);
  for (const [path, content] of Object.entries(examples)) write(root, path, content);
  const exampleReferenceFiles = Object.keys(examples);
  const contract: GenerationTargetContract = {
    contractVersion: "2.1",
    generationMode: "FRONTEND_ONLY",
    projectId: "flowmind-business-base",
    frontend: {
      rootDir: "frontend", framework: "vue3", generatedModuleDir: "src/modules/generated", generatedApiDir: "src/api/generated",
      routeRegistry: "src/router/generated-routes.ts", sharedStartShell: "src/components/workflow/WorkflowStartShell.vue",
      sharedWorkflowTypes: "src/types/workflow.ts", exampleReferenceFiles, verificationProfile: "vue3-npm",
    },
    readableReferenceFiles: ["frontend/package.json", "frontend/src/router/generated-routes.ts", "frontend/src/components/workflow/WorkflowStartShell.vue", "frontend/src/types/workflow.ts", ...exampleReferenceFiles],
    allowedOutputPatterns: [
      "frontend/src/modules/generated/**/*",
      "frontend/src/api/generated/**/*",
      "frontend/src/router/generated-routes.ts",
    ],
    protectedFiles: [{ path: "frontend/package.json", sha256: hash(packageJson) }],
  };
  write(root, ".flowmind/generation-target.json", `${JSON.stringify(contract, null, 2)}\n`);
  return root;
}

export function seedActiveWorkflow(
  database: { db: any },
  sessionId: string,
  targetRoot: string | null,
  owner = "user_sales",
  requirement: BusinessRequirement = structuredClone(ENTRY_APPLICATION_REQUIREMENT),
): void {
  const now = new Date().toISOString();
  database.db.prepare(`INSERT INTO agent_session (
    id, owner_user_id, owner_user_name, target_root, state, row_version,
    requirement_revision, requirement_json, requirement_confirmed_at, created_at, updated_at
  ) VALUES (?, ?, 'Sales User', ?, 'PROCESS_ACTIVE', 0, 1, ?, ?, ?, ?)`)
    .run(sessionId, owner, targetRoot, JSON.stringify(requirement), now, now, now);
  database.db.prepare(`INSERT INTO agent_process_definition (
    id, session_id, requirement_revision, platform_definition_id, process_code, process_name,
    status, saga_step, requirement_snapshot_json, platform_snapshot_json,
    create_operation_id, save_operation_id, publish_operation_id, activate_operation_id,
    created_by, created_at, activated_at, updated_at
  ) VALUES (?, ?, 1, ?, ?, ?, 'ACTIVE', 'ACTIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)
    .run(`process_${sessionId}`, sessionId, `definition_${sessionId}`, requirement.businessCode, requirement.businessName, JSON.stringify(requirement), JSON.stringify({ id: `definition_${sessionId}`, processCode: requirement.businessCode, nodes: requirement.nodes }),
      `create_${sessionId}`, `save_${sessionId}`, `publish_${sessionId}`, `activate_${sessionId}`, owner, now, now, now);
}

export function write(root: string, relativePath: string, content: string): void {
  const path = join(root, ...relativePath.split("/"));
  mkdirSync(join(path, ".."), { recursive: true });
  writeFileSync(path, content, "utf8");
}

function hash(content: string): string { return createHash("sha256").update(content).digest("hex"); }
