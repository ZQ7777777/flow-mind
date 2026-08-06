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
  const pom = `<project><properties><platform-starter.version>0.1.0-SNAPSHOT</platform-starter.version></properties><dependencies><dependency><groupId>com.flowmind</groupId><artifactId>platform-starter</artifactId><version>\${platform-starter.version}</version></dependency></dependencies></project>\n`;
  const packageJson = `${JSON.stringify({ name: "e2e-business", private: true, scripts: { typecheck: "tsc", test: "vitest run", build: "vite build" } }, null, 2)}\n`;
  const routes = `import type { RouteRecordRaw } from "vue-router";\nexport const generatedRoutes: RouteRecordRaw[] = [];\n`;
  const currentUserProvider = `package com.flowmind.business.security;
public interface CurrentBusinessUserProvider {
  BusinessUser currentUser();
  final class BusinessUser {
    private final String userId;
    private final String departmentId;
    public BusinessUser(String userId, String departmentId) { this.userId = userId; this.departmentId = departmentId; }
    public String getUserId() { return userId; }
    public String getDepartmentId() { return departmentId; }
  }
}
`;
  const platformReference = `# platform-starter 0.1.0-SNAPSHOT

- com.flowmind.platform.api.service.ProcessRuntimeService#startAndSubmit(StartProcessRequest)
- com.flowmind.platform.api.dto.StartProcessRequest: setVariables(Map<String, Object>), setAttachments(List<AttachmentUploadItem>)
- com.flowmind.platform.api.dto.AttachmentUploadItem
- com.flowmind.platform.api.dto.ProcessInstanceDTO: getCreatedTasks()
- com.flowmind.platform.api.dto.TaskDTO: getTaskId(), getNodeCode(), getNodeName()

Map every TaskDTO returned by ProcessInstanceDTO.getCreatedTasks() to taskId, nodeCode and taskName.
`;
  write("backend/pom.xml", pom);
  write("frontend/package.json", packageJson);
  write("frontend/src/router/generated-routes.ts", routes);
  write("backend/src/main/java/com/flowmind/business/security/CurrentBusinessUserProvider.java", currentUserProvider);
  write(".flowmind/references/platform-starter-0.1.0.md", platformReference);
  write(".flowmind/generation-target.json", `${JSON.stringify({
    contractVersion: "1.1", projectId: "flowmind-business-base",
    backend: { rootDir: "backend", javaVersion: "8", springBootVersion: "2.7.18", basePackage: "com.flowmind.business", generatedSourceDir: "src/main/java/com/flowmind/business/generated", generatedTestDir: "src/test/java/com/flowmind/business/generated", starter: { groupId: "com.flowmind", artifactId: "platform-starter", version: "0.1.0-SNAPSHOT", allowedApi: "ProcessRuntimeService#startAndSubmit(StartProcessRequest)" }, trustedUserContext: { accessorType: "com.flowmind.business.security.CurrentBusinessUserProvider", accessorMethod: "currentUser", userIdProperty: "userId", departmentIdProperty: "departmentId" }, apiReferences: { platformRuntime: ".flowmind/references/platform-starter-0.1.0.md", trustedUserContext: "backend/src/main/java/com/flowmind/business/security/CurrentBusinessUserProvider.java" }, verificationProfile: "maven-java8" },
    frontend: { rootDir: "frontend", framework: "vue3", generatedViewDir: "src/modules/generated", generatedApiDir: "src/api/generated", generatedTestDir: "src/modules/generated/__tests__", routeRegistry: "src/router/generated-routes.ts", verificationProfile: "vue3-npm" },
    readableReferenceFiles: ["backend/pom.xml", ".flowmind/references/platform-starter-0.1.0.md", "backend/src/main/java/com/flowmind/business/security/CurrentBusinessUserProvider.java", "frontend/package.json", "frontend/src/router/generated-routes.ts"],
    allowedOutputPatterns: ["backend/src/main/java/com/flowmind/business/generated/**/*.java", "backend/src/test/java/com/flowmind/business/generated/**/*.java", "frontend/src/modules/generated/**/*", "frontend/src/api/generated/**/*", "frontend/src/router/generated-routes.ts"],
    protectedFiles: [{ path: "backend/pom.xml", sha256: hash(pom) }, { path: "frontend/package.json", sha256: hash(packageJson) }, { path: ".flowmind/references/platform-starter-0.1.0.md", sha256: hash(platformReference) }, { path: "backend/src/main/java/com/flowmind/business/security/CurrentBusinessUserProvider.java", sha256: hash(currentUserProvider) }],
  }, null, 2)}\n`);
  function write(relativePath, content) { const path = resolve(root, relativePath); mkdirSync(resolve(path, ".."), { recursive: true }); writeFileSync(path, content, "utf8"); }
}

function hash(value) { return createHash("sha256").update(value).digest("hex"); }
