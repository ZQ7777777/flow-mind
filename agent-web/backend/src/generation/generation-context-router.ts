import type {
  GenerationContextCapability,
  GenerationContextRouteItem,
  GenerationContextSnapshot,
  RequirementIrDraft,
} from "@flowmind/agent-contracts";

const SKILL_PREFIX = "skill:flowmind-business-generation:";

export function routeGenerationContext(
  ir: RequirementIrDraft,
  skills: GenerationContextSnapshot["skills"],
  references: GenerationContextSnapshot["references"],
): NonNullable<GenerationContextSnapshot["routing"]> {
  const capabilities = detectCapabilities(ir);
  const available = new Set([
    ...skills.flatMap((skill) => skill.files.map((file) => `${SKILL_PREFIX}${file.relativePath}`)),
    ...references.map((reference) => `reference:${reference.source}:${reference.relativePath}`),
  ]);
  const reasons = new Map<string, Set<GenerationContextCapability>>();
  const require = (key: string, capability: GenerationContextCapability) => {
    if (!available.has(key)) return;
    const current = reasons.get(key) || new Set<GenerationContextCapability>();
    current.add(capability);
    reasons.set(key, current);
  };

  require(`${SKILL_PREFIX}SKILL.md`, "BASE_FORM");
  require(`${SKILL_PREFIX}references/generated-form-contract.md`, "BASE_FORM");
  require(`${SKILL_PREFIX}references/quality-and-boundaries.md`, "BASE_FORM");
  for (const reference of references.filter(({ source, relativePath }) => source === "TARGET"
    && (/WorkflowStartShell\.vue$/.test(relativePath) || /\/types\/.*\.ts$/.test(relativePath)))) {
    require(`reference:TARGET:${reference.relativePath}`, "BASE_FORM");
  }
  if (capabilities.includes("MULTI_SELECT")) require(`${SKILL_PREFIX}references/frontend-design.md`, "MULTI_SELECT");
  for (const capability of ["DYNAMIC_REFERENCE", "CASCADE", "DATA_QUERY"] as const) {
    if (!capabilities.includes(capability)) continue;
    require(`${SKILL_PREFIX}references/backend-api-contract.md`, capability);
    for (const reference of references.filter(({ source, relativePath }) => source === "TARGET"
      && ((/\/api\/.*\.ts$/.test(relativePath) && !relativePath.endsWith(".spec.ts")) || /reference-data/i.test(relativePath)))) {
      require(`reference:TARGET:${reference.relativePath}`, capability);
    }
  }
  for (const capability of ["CALCULATION", "BUSINESS_CHECK"] as const) {
    if (!capabilities.includes(capability)) continue;
    require(`${SKILL_PREFIX}references/golden-example.md`, capability);
    for (const reference of references.filter(({ source, relativePath }) => source === "TARGET" && /BusinessForm\.vue$/.test(relativePath))) {
      require(`reference:TARGET:${reference.relativePath}`, capability);
    }
  }

  const items: GenerationContextRouteItem[] = [...available].sort().map((key) => ({
    key,
    required: reasons.has(key),
    reasons: [...(reasons.get(key) || [])].sort(),
  }));
  return { version: "1.0", capabilities, items };
}

export function detectCapabilities(ir: RequirementIrDraft): GenerationContextCapability[] {
  const capabilities = new Set<GenerationContextCapability>(["BASE_FORM"]);
  if (ir.fields.some(({ multiple }) => multiple)) capabilities.add("MULTI_SELECT");
  if (ir.fields.some(({ referenceData }) => referenceData)) capabilities.add("DYNAMIC_REFERENCE");
  if (ir.fields.some(({ referenceData }) => Object.keys(referenceData?.parameterBindings || {}).length > 0)) capabilities.add("CASCADE");
  if (ir.dataQueries.length) capabilities.add("DATA_QUERY");
  if (ir.calculations.length) capabilities.add("CALCULATION");
  if (ir.checks.length) capabilities.add("BUSINESS_CHECK");
  return [...capabilities];
}
