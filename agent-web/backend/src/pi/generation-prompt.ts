import type { BusinessRequirement, GenerationTargetContract } from "@flowmind/agent-contracts";
import type { GenerationSpec } from "../generation/generation-spec.js";

export interface GenerationApiReferences {
  platformRuntime: string;
  trustedUserContext: string;
  businessReferenceData?: string;
}

const FRONTEND_VISUAL_CONTRACT = [
  "Frontend visual contract - match the platform flow-test UI:",
  '- The generated Vue view owns only the routed content area. Its root must be <section class="page-surface generated-entry-page">. Do not generate another application shell, top bar, status bar, or side navigation; the target business-base App.vue already owns them.',
  '- Start with a compact .section-heading containing an uppercase teal eyebrow such as "Generated Entry" and an h1 using the confirmed business display name. Keep the h1 at 24px; do not create a hero section or explanatory marketing copy.',
  "- Use Element Plus form controls already available in the target. Put fields in a top-aligned .entry-form-grid using grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)) with 12px gaps. Textareas, uploads, and other content that needs horizontal room may span the full grid. Collapse to one column on narrow screens.",
  "- Keep the page dense and operational: 16px outer panel padding, 14px section spacing, 8px maximum border radius, 6px control/button radius, 34-36px control height, and no decorative shadow. Do not center the form in a narrow floating card; use the available content width.",
  "- Use the platform palette exactly: ink #17202a, muted text #5d6978, border #d8dee8, panel #ffffff, subtle surface #f8fafc, primary blue #2563eb, eyebrow teal #0f766e, error #be123c, success #15803d, and focus outline #f59e0b. Define scoped custom properties or scoped declarations so the view is stable without modifying global styles.",
  "- Place submit/reset commands in a left-aligned or right-aligned .form-actions row separated from the fields by a top border. The submit command is the single primary blue button; secondary commands stay white with a neutral border. Preserve clear disabled and loading states without changing layout dimensions.",
  "- Render validation and request feedback as compact inline status panels with role=alert or role=status: pale red for errors and pale green for success. Long messages and file names must wrap without overflowing.",
  "- Include a complete <style scoped> block for the generated page and its own semantic classes. Do not style body, #app, the shared shell, or generic global Element Plus selectors. No gradients, decorative blobs, oversized hero typography, or nested cards.",
  "- Keep labels visible, mark required fields accessibly, preserve keyboard focus, and ensure every field and action remains usable at desktop and mobile widths.",
].join("\n");

export function buildGenerationPrompt(
  requirement: BusinessRequirement,
  processSnapshot: Record<string, unknown>,
  contract: GenerationTargetContract,
  spec: GenerationSpec,
  apiReferences: GenerationApiReferences,
): string {
  return `You are the Flow Mind business code generator. Generate source files directly from the confirmed requirement; do not use templates or copy values from examples.

Use only the registered tools. You have no shell, Git, platform mutation, target write, or general filesystem access.
Read target references only when needed, write every required file to staging, then call report_generation_complete with the exact file list.

Confirmed generated identity:
- Business display name: ${spec.businessName}
- Process code: ${spec.processCode}
- Java class prefix: ${spec.classPrefix}
- Java package: ${spec.javaPackage}
- Frontend/API slug: ${spec.kebabCode}
- Submit endpoint: POST ${spec.apiPath}
- Frontend route: ${spec.routePath}, route name ${spec.routeName}

Hard runtime boundary:
- Java 8, Spring Boot 2.7.18, base package ${contract.backend.basePackage}.
- Inject ${contract.backend.trustedUserContext.accessorType} and ProcessRuntimeService.
- The only platform runtime call is exactly ${contract.backend.starter.allowedApi}; invoke it once in the service submission path.
- starterUserId and starterDeptId come only from ${contract.backend.trustedUserContext.accessorMethod}().
- The processCode is the literal confirmed value ${JSON.stringify(spec.processCode)}. Never accept processCode, starterUserId, or starterDeptId from the client.
- Set a non-blank instanceTitle before startAndSubmit. If the confirmed form has a required field whose exact fieldCode is applicationNo, use ${JSON.stringify(spec.businessName + " - ")} + that field value; otherwise use the literal business name ${JSON.stringify(spec.businessName)}. Never accept instanceTitle from the client.
- Map every confirmed form field into process variables using its exact fieldCode.
- Only attachments listed in the apply attachments section below belong on the initiation page. Do not generate controls or payload mappings for attachments assigned only to later nodes.
- Use multipart/form-data with a JSON payload part and file parts named by attachmentCode. Convert files to AttachmentUploadItem values with the exact attachmentCode before startAndSubmit.
- Enforce confirmed required/count/extension/size rules for apply attachments in the backend service before the runtime call. Explicitly reject a missing required attachment before startAndSubmit, reject counts outside minCount/maxCount, and reject every file whose extension or size violates the confirmed contract. The controller must translate these client attachment violations to HTTP 400, while direct service calls with null or empty collections must reject them without calling startAndSubmit. If there are no apply attachments, do not invent file controls or attachment values.
- When a controller returns a non-ASCII plain-text error body, set the production response Content-Type to text/plain;charset=UTF-8. A CharacterEncodingFilter added only in a test is not a production encoding fix. Controller tests must assert the HTTP status, advertised UTF-8 charset, and decoded error body.
- Require Idempotency-Key and derive a stable operationId from the exact process code + trusted user ID + idempotency key.
- Return instance ID, instance status, and created next-task summaries.
- Import TaskDTO and map every element returned by ProcessInstanceDTO.getCreatedTasks() to a non-empty response summary using getTaskId(), getNodeCode(), and getNodeName(); map nodeName to the response taskName. Do not use List<?> or create blank task objects.
- Do not generate approvals, rejects, returns, withdrawals, transfers, delegation, countersign, direct-send, todo/done/initiated/read lists, detail/trace, post-apply orchestration, platform HTTP, entities, Mapper, Repository, DAO, SQL, migrations, Java 9+, record, var, Jakarta, or Spring Boot 3.
- Tests must mock ProcessRuntimeService and trusted user access, assert exactly one startAndSubmit call, the literal process code, the derived non-blank instance title, all variables, applicable attachments (or their absence), stable idempotency, response mapping, frontend validation, idempotency header and success state. For every apply attachment, cover missing required/null/empty input, count below minCount and above maxCount, disallowed extension, maximum-size overflow, and a legal submission; invalid service cases must verify startAndSubmit was never called, and the controller test must assert HTTP 400. Never weaken a failing test by adding an attachment to a scenario whose purpose is to verify a missing required attachment. Never skip or empty tests.
- The target backend has no Spring Boot application class. Build controller tests with MockMvcBuilders.standaloneSetup and an explicitly constructed controller. Do not use @WebMvcTest or depend on @SpringBootConfiguration.
- Keep Mockito strict. Stub trusted-user access only inside tests or helpers used by tests that reach the runtime call. Do not put trusted-user stubbing in @BeforeEach, and do not use lenient stubs to hide unused setup.
- Do not mock enum types such as InstanceStatusEnum; use a real enum constant or valueOf in tests. When an optional collection may be null, use an explicit nullable matcher instead of anyList().
- In Element Plus component tests, drive ElInput, ElInputNumber, and ElSelect through Vue Test Utils findComponent plus their public update:modelValue events, and drive ElUpload through update:fileList. Assert business-visible behavior such as request suppression, request payload, error text, and public disabled/loading props. Do not read or mutate the root wrapper.vm, query native option elements, or inspect Element Plus CSS classes.
- When testing an in-flight submission, wait until validation has completed and the fetch mock has been called (for example with vi.waitFor) before asserting disabled/loading state.
- Read the existing route registry and preserve unrelated routes while registering this business route.
- The authoritative references below are part of the generation contract. Never guess or substitute Java packages, return types, nested user types, getters, or setters.
- Generated Vitest tests run in the target's configured jsdom environment: do not assume Blob.text() exists, and assert form behavior rather than Element Plus internal CSS classes.
- Generated file-size fixtures must allocate the requested bytes, for example new File([new Uint8Array(size)], name, ...); never accept a size argument and ignore it. Use Uint8Array(size) so size-limit tests exercise the real boundary.
- When a form field declares referenceDataSource, use the registered business reference-data API below. Never replace it with hardcoded options. Implement parameterBindings, cascading clears, loading/error states, multiple values, and autofillBindings exactly as confirmed.

${FRONTEND_VISUAL_CONTRACT}

Authoritative platform runtime API reference:
${apiReferences.platformRuntime}

Authoritative trusted user context source:
${apiReferences.trustedUserContext}

${apiReferences.businessReferenceData ? `Authoritative business reference-data API:\n${apiReferences.businessReferenceData}\n` : ""}

Required exact paths:
${spec.files.map((path) => `- ${path}`).join("\n")}

Apply attachments only:
${JSON.stringify(spec.applyAttachments, null, 2)}

Confirmed requirement:
${JSON.stringify(requirement, null, 2)}

Activated process snapshot:
${JSON.stringify(processSnapshot, null, 2)}

Generation target contract:
${JSON.stringify(contract, null, 2)}`;
}
