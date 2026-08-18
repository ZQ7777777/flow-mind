import { describe, expect, it } from "vitest";
import type { BusinessRequirement, GenerationTargetContract } from "@flowmind/agent-contracts";
import { buildGenerationPrompt } from "../src/pi/generation-prompt.js";
import type { GenerationSpec } from "../src/generation/generation-spec.js";

describe("generation prompt", () => {
  it("embeds authoritative runtime and trusted-user references", () => {
    const prompt = buildGenerationPrompt(
      { formFields: [] } as unknown as BusinessRequirement,
      {},
      { backend: { basePackage: "com.flowmind.business", starter: { allowedApi: "ProcessRuntimeService#startAndSubmit(StartProcessRequest)" }, trustedUserContext: { accessorType: "com.flowmind.business.security.CurrentBusinessUserProvider", accessorMethod: "currentUser" } } } as unknown as GenerationTargetContract,
      { businessName: "Test", processCode: "test", classPrefix: "Test", javaPackage: "com.flowmind.business.generated.test", kebabCode: "test", apiPath: "/api/generated/test/submit", routePath: "/generated/test/apply", routeName: "generated-test-apply", files: [], applyAttachments: [] } as unknown as GenerationSpec,
      {
        platformRuntime: "com.flowmind.platform.api.service.ProcessRuntimeService com.flowmind.platform.api.dto.TaskDTO setVariables setAttachments getCreatedTasks getTaskId getNodeCode getNodeName",
        trustedUserContext: "CurrentBusinessUserProvider.BusinessUser",
        businessReferenceData: "GET /api/reference-data/futures-products FUTURES_PRODUCTS parameterBindings autofillBindings",
      },
    );
    expect(prompt).toContain("com.flowmind.platform.api.service.ProcessRuntimeService");
    expect(prompt).toContain("CurrentBusinessUserProvider.BusinessUser");
    expect(prompt).toContain("do not assume Blob.text() exists");
    expect(prompt).toContain("MockMvcBuilders.standaloneSetup");
    expect(prompt).toContain("Do not use @WebMvcTest");
    expect(prompt).toContain("getTaskId(), getNodeCode(), and getNodeName()");
    expect(prompt).toContain("Do not put trusted-user stubbing in @BeforeEach");
    expect(prompt).toContain("public update:modelValue events");
    expect(prompt).toContain("text/plain;charset=UTF-8");
    expect(prompt).toContain("CharacterEncodingFilter added only in a test");
    expect(prompt).toContain("drive ElUpload through update:fileList");
    expect(prompt).toContain("Do not read or mutate the root wrapper.vm");
    expect(prompt).toContain("reject a missing required attachment before startAndSubmit");
    expect(prompt).toContain("Set a non-blank instanceTitle before startAndSubmit");
    expect(prompt).toContain("required field whose exact fieldCode is applicationNo");
    expect(prompt).toContain("otherwise use the literal business name");
    expect(prompt).toContain("Do not mock enum types");
    expect(prompt).toContain("Uint8Array(size)");
    expect(prompt).toContain("Frontend visual contract - match the platform flow-test UI");
    expect(prompt).toContain('class="page-surface generated-entry-page"');
    expect(prompt).toContain("Do not generate another application shell, top bar, status bar, or side navigation");
    expect(prompt).toContain("#17202a");
    expect(prompt).toContain("#d8dee8");
    expect(prompt).toContain("#2563eb");
    expect(prompt).toContain("<style scoped>");
    expect(prompt).toContain("repeat(auto-fit, minmax(240px, 1fr))");
    expect(prompt).toContain("No gradients, decorative blobs, oversized hero typography, or nested cards");
    expect(prompt).toContain("GET /api/reference-data/futures-products");
    expect(prompt).toContain("Never replace it with hardcoded options");
  });
});
