import type { BusinessRequirement, GenerationTargetContract } from "@flowmind/agent-contracts";
import type { GenerationSpec } from "../generation/generation-spec.js";

export function buildGenerationPrompt(
  requirement: BusinessRequirement,
  processSnapshot: Record<string, unknown>,
  contract: GenerationTargetContract,
  spec: GenerationSpec,
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
- Map every confirmed form field into process variables using its exact fieldCode.
- Only attachments listed in the apply attachments section below belong on the initiation page. Do not generate controls or payload mappings for attachments assigned only to later nodes.
- Use multipart/form-data with a JSON payload part and file parts named by attachmentCode. Convert files to AttachmentUploadItem values with the exact attachmentCode before startAndSubmit.
- Enforce confirmed required/count/extension/size rules for apply attachments. If there are no apply attachments, do not invent file controls or attachment values.
- Require Idempotency-Key and derive a stable operationId from the exact process code + trusted user ID + idempotency key.
- Return instance ID, instance status, and created next-task summaries.
- Do not generate approvals, rejects, returns, withdrawals, transfers, delegation, countersign, direct-send, todo/done/initiated/read lists, detail/trace, post-apply orchestration, platform HTTP, entities, Mapper, Repository, DAO, SQL, migrations, Java 9+, record, var, Jakarta, or Spring Boot 3.
- Tests must mock ProcessRuntimeService and trusted user access, assert exactly one startAndSubmit call, the literal process code, all variables, applicable attachments (or their absence), stable idempotency, response mapping, frontend validation, idempotency header and success state. Never skip or empty tests.
- Read the existing route registry and preserve unrelated routes while registering this business route.

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
