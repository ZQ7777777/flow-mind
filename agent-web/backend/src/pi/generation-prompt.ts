import type { BusinessRequirement, GenerationTargetContract } from "@flowmind/agent-contracts";
import { ENTRY_APPLICATION_FILES } from "../generation/generation.constants.js";

export function buildGenerationPrompt(
  requirement: BusinessRequirement,
  processSnapshot: Record<string, unknown>,
  contract: GenerationTargetContract,
): string {
  return `You are the Flow Mind entry-application code generator. Generate source files directly; do not use templates.

Use only the registered tools. You have no shell, Git, platform mutation, target write, or general filesystem access.
Read target references only when needed, write every required file to staging, then call report_generation_complete with the exact file list.

Hard runtime boundary:
- Java 8, Spring Boot 2.7.18, base package ${contract.backend.basePackage}.
- Inject ${contract.backend.trustedUserContext.accessorType} and ProcessRuntimeService.
- The only platform runtime call is exactly ${contract.backend.starter.allowedApi}; invoke it once in the service submission path.
- starterUserId and starterDeptId come only from ${contract.backend.trustedUserContext.accessorMethod}().
- Map confirmed fields into process variables and bankReceipt into attachments.
- Require Idempotency-Key and derive a stable operationId from entry_application + trusted user ID + idempotency key.
- Return instance ID, instance status, and created next-task summaries.
- Do not generate approvals, rejects, returns, withdrawals, transfers, delegation, countersign, direct-send, todo/done/initiated/read lists, detail/trace, post-apply orchestration, platform HTTP, entities, Mapper, Repository, DAO, SQL, migrations, Java 9+, record, var, Jakarta, or Spring Boot 3.
- Tests must mock ProcessRuntimeService and trusted user access, assert exactly one startAndSubmit call, variables, bank receipt, stable idempotency, response mapping, frontend validation, attachment payload, idempotency header and success state. Never skip or empty tests.

Required exact paths:
${ENTRY_APPLICATION_FILES.map((path) => `- ${path}`).join("\n")}

Confirmed requirement:
${JSON.stringify(requirement, null, 2)}

Activated process snapshot:
${JSON.stringify(processSnapshot, null, 2)}

Generation target contract:
${JSON.stringify(contract, null, 2)}`;
}
