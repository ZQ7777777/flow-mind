import type { BusinessRequirement, GenerationTargetContract } from "@flowmind/agent-contracts";
import type { GenerationSpec } from "../generation/generation-spec.js";

export interface GenerationApiReferences {
  businessReferenceData?: string;
  businessReferencePath?: string;
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

Generate the exact staged files listed below. Use the registered immutable context tools for the project skill, requirements, target shell/types, and references. Read every required context item before calling report_generation_complete, then report exactly the files listed below.

Identity:
- Business name: ${spec.businessName}
- Process code: ${spec.processCode}
- Module slug: ${spec.kebabCode}
- Route: ${spec.routePath}
- Read-only business API generated: ${spec.hasBusinessApi}

Required exact paths:
${spec.files.map((path) => `- ${path}`).join("\n")}

Field whitelist:
- BusinessForm.vue must not read or write modelValue keys outside confirmed formFields.
- Derive every value(...), update(...), updateMany(...), visible(...), readonly(...), required(...), and direct model field access from confirmed requirement.formFields only.
- Do not copy hidden fields or persistence snapshots from sample/golden references unless the same fieldCode is present in the confirmed requirement.

Authoritative business reference-data API:
${references.businessReferenceData || "No business API is required for this generation."}

Available immutable generation context:
${references.contextSummary || "Use list_generation_context to inspect it."}

Confirmed requirement:
${JSON.stringify(requirement, null, 2)}

Activated process snapshot:
${JSON.stringify(processSnapshot, null, 2)}

Generation target contract:
${JSON.stringify(contract, null, 2)}`;
}
