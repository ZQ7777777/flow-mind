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
      },
    );
    expect(prompt).toContain("com.flowmind.platform.api.service.ProcessRuntimeService");
    expect(prompt).toContain("CurrentBusinessUserProvider.BusinessUser");
    expect(prompt).toContain("do not assume Blob.text() exists");
    expect(prompt).toContain("MockMvcBuilders.standaloneSetup");
    expect(prompt).toContain("Do not use @WebMvcTest");
    expect(prompt).toContain("getTaskId(), getNodeCode(), and getNodeName()");
    expect(prompt).toContain("Do not put trusted-user stubbing in @BeforeEach");
    expect(prompt).toContain("public props and emitted update:modelValue events");
  });
});
