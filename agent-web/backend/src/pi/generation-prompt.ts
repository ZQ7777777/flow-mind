import type { BusinessRequirement, GenerationTargetContract } from "@flowmind/agent-contracts";
import type { GenerationSpec } from "../generation/generation-spec.js";

export interface GenerationApiReferences {
  businessReferenceData?: string;
  contextSummary?: string;
}

export function buildGenerationPrompt(
  requirement: BusinessRequirement,
  processSnapshot: Record<string, unknown>,
  contract: GenerationTargetContract,
  spec: GenerationSpec,
  references: GenerationApiReferences,
): string {
  return `You are the Flow Mind frontend business-form generator.

Generate only the exact staged frontend files listed below. Use registered read-only context tools for the project skill, golden requirement, target shell/types, and sample implementation. The golden sample is guidance, not a template: never copy its process code, field values, labels, attachment defaults, or unrelated behavior.

Hard boundaries:
- The target contract is frontend-only. Never generate Java, backend, database, Controller, Service, DTO, Repository, build configuration, or server code.
- BusinessForm.vue renders confirmed business fields, applies runtime visibility/editability/required permissions, emits immutable model updates, normalizes number values, implements confirmed read-only business queries/calculations/checks, and exposes validate().
- BusinessForm.vue must not call fetch directly. When the specification includes a generated API file, import typed read-only helpers from it.
- Apply.vue only composes a standalone page, the fixed processCode ${JSON.stringify(spec.processCode)}, BusinessForm, and the shared WorkflowStartShell. It must not submit, upload attachments, generate idempotency keys, or implement task actions.
- The generated API module may use GET only and only endpoints declared by the authoritative business reference. It must not call /api/platform/**, workflow start-submit, task submit, approve, reject, return, upload, or any mutation endpoint.
- The shared WorkflowStartShell owns start-context/start-submit, attachments, idempotency, loading, and workflow success/error state.
- Merge the route and business-form registry without deleting unrelated entries. Use meta.standalone: true, never meta.public: true.
- Tests assert user-visible behavior, request parameters, cascading clears, numeric values, permissions, calculations/checks, and delegation. Do not test private component state or Element Plus CSS internals.
- Keep styles scoped, responsive, dense, accessible, and compatible with the target application. Visual choices may follow the golden sample but are not hard-coded layout requirements.
- Read every required context item before calling report_generation_complete. Report exactly the files listed below.

Identity:
- Business name: ${spec.businessName}
- Process code: ${spec.processCode}
- Module slug: ${spec.kebabCode}
- Route: ${spec.routePath}
- Read-only business API generated: ${spec.hasBusinessApi}

Required exact paths:
${spec.files.map((path) => `- ${path}`).join("\n")}

Authoritative business reference-data API:
${references.businessReferenceData || "No business API is required for this generation."}

Available immutable generation context:
${references.contextSummary || "Use list_generation_context to inspect it."}

Confirmed requirement:
${JSON.stringify(requirement, null, 2)}

Activated process snapshot (identity and field permission authority only; do not generate later-node task UIs):
${JSON.stringify(processSnapshot, null, 2)}

Generation target contract:
${JSON.stringify(contract, null, 2)}`;
}
