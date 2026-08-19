import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { createHash } from "node:crypto";
import { startMockPlatform } from "./mock-platform.mjs";

export default async function globalSetup() {
  const dataDir = resolve(".e2e-data");
  const targetRoot = resolve(".e2e-target", "business-base");
  rmSync(dataDir, { recursive: true, force: true });
  rmSync(resolve(".e2e-target"), { recursive: true, force: true });
  createTarget(targetRoot);
  process.env.AGENT_FAKE_PI = "true";
  process.env.AGENT_BIND_HOST = "127.0.0.1";
  process.env.AGENT_PORT = process.env.AGENT_E2E_PORT || "3199";
  process.env.AGENT_DATA_DIR = dataDir;
  process.env.AGENT_ALLOWED_TARGET_ROOTS = targetRoot;
  process.env.FLOW_PLATFORM_BASE_URL = "http://127.0.0.1:18080";
  process.env.FLOW_PLATFORM_AUTH_MODE = "session";

  const platform = startMockPlatform(18080);
  await new Promise((resolveReady) => platform.once("listening", resolveReady));
  const { bootstrap } = await import("../backend/dist/main.js");
  const app = await bootstrap();

  return async () => {
    await app.close();
    await new Promise((resolveClosed) => platform.close(resolveClosed));
    rmSync(dataDir, { recursive: true, force: true });
    rmSync(resolve(".e2e-target"), { recursive: true, force: true });
  };
}

function createTarget(root) {
  const packageJson = `${JSON.stringify({ name: "e2e-business", private: true, scripts: { typecheck: "tsc", test: "vitest run", build: "vite build" } }, null, 2)}\n`;
  const routes = `import type { RouteRecordRaw } from "vue-router";\nexport const generatedRoutes: RouteRecordRaw[] = [];\nexport const generatedBusinessFormRegistry: Record<string, () => Promise<unknown>> = {};\n`;
  const startShell = `<script setup lang="ts">defineProps<{ processCode: string; businessForm?: unknown }>();</script><template><main><slot /></main></template>\n`;
  const workflowTypes = `export interface WorkflowFormField { fieldCode: string; fieldName: string; required: boolean; visible: boolean; editable: boolean }\nexport interface WorkflowFieldPermission { fieldCode: string; required?: boolean; visible?: boolean; editable?: boolean }\n`;
  const businessReferenceData = `# Business Reference Data API v1

- GET /api/reference-data/futures-accounts?keyword=
- GET /api/reference-data/futures-accounts/{accountNo}/funds?currency=CNY
- GET /api/reference-data/futures-accounts/{accountNo}/trading-codes?exchangeCode=
- GET /api/reference-data/exchanges
- GET /api/reference-data/futures-products?exchangeCode=&keyword=&productType=FUTURES

Use same-origin requests. FUTURES_PRODUCTS returns contractMultiplier, pledgeUnitQuantity, previousSettlementPrice and dataSource.
`;
  const examples = {
    "frontend/src/modules/generated/sample/BusinessForm.vue": `<script setup lang="ts">defineProps<{ modelValue: Record<string, unknown> }>();</script><template><section>sample form</section></template>\n`,
    "frontend/src/modules/generated/sample/Apply.vue": `<script setup lang="ts">import WorkflowStartShell from "../../../components/workflow/WorkflowStartShell.vue";</script><template><WorkflowStartShell process-code="warehouse_pledge" /></template>\n`,
    "frontend/src/modules/generated/sample/__tests__/BusinessForm.spec.ts": `import { describe, it } from "vitest"; describe("sample", () => { it("renders", () => undefined); });\n`,
    "frontend/src/modules/generated/sample/__tests__/Apply.spec.ts": `import { describe, it } from "vitest"; describe("sample", () => { it("delegates", () => undefined); });\n`,
    "frontend/src/api/generated/sample/sample.ts": `export async function getReference(): Promise<unknown> { return {}; }\n`,
    "frontend/src/api/generated/sample/sample.spec.ts": `import { describe, it } from "vitest"; describe("sample api", () => { it("is read only", () => undefined); });\n`,
  };
  write("frontend/package.json", packageJson);
  write("frontend/src/router/generated-routes.ts", routes);
  write("frontend/src/components/workflow/WorkflowStartShell.vue", startShell);
  write("frontend/src/types/workflow.ts", workflowTypes);
  for (const [path, content] of Object.entries(examples)) write(path, content);
  write(".flowmind/references/business-reference-data-v1.md", businessReferenceData);
  const exampleReferenceFiles = Object.keys(examples);
  write(".flowmind/generation-target.json", `${JSON.stringify({
    contractVersion: "2.1", generationMode: "FRONTEND_ONLY", projectId: "flowmind-business-base",
    frontend: { rootDir: "frontend", framework: "vue3", generatedModuleDir: "src/modules/generated", generatedApiDir: "src/api/generated", routeRegistry: "src/router/generated-routes.ts", sharedStartShell: "src/components/workflow/WorkflowStartShell.vue", sharedWorkflowTypes: "src/types/workflow.ts", exampleReferenceFiles, apiReferences: { businessReferenceData: ".flowmind/references/business-reference-data-v1.md" }, verificationProfile: "vue3-npm" },
    readableReferenceFiles: ["frontend/package.json", "frontend/src/router/generated-routes.ts", "frontend/src/components/workflow/WorkflowStartShell.vue", "frontend/src/types/workflow.ts", ".flowmind/references/business-reference-data-v1.md", ...exampleReferenceFiles],
    allowedOutputPatterns: ["frontend/src/modules/generated/**/*", "frontend/src/api/generated/**/*", "frontend/src/router/generated-routes.ts"],
    protectedFiles: [{ path: "frontend/package.json", sha256: hash(packageJson) }, { path: ".flowmind/references/business-reference-data-v1.md", sha256: hash(businessReferenceData) }],
  }, null, 2)}\n`);
  function write(relativePath, content) { const path = resolve(root, relativePath); mkdirSync(resolve(path, ".."), { recursive: true }); writeFileSync(path, content, "utf8"); }
}

function hash(value) { return createHash("sha256").update(value).digest("hex"); }
