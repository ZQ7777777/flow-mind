---
name: flowmind-business-generation
description: Generate or repair Flow Mind business-specific standalone Vue 3 entry pages from a confirmed BusinessRequirement while preserving the shared workflow start shell, generic start and task submit APIs, file allowlist, and validation gates. Use for Agent Web code generation targeting business-base generated frontend modules, standalone routes, form tests, or generation reviews.
---

# Flow Mind Business Generation

## Generate the standalone business entry page

1. Read the confirmed requirement, active process snapshot, generation target contract, and generation specification.
2. Read [backend-api-contract.md](references/backend-api-contract.md) and [generated-form-contract.md](references/generated-form-contract.md) completely.
3. Read [quality-and-boundaries.md](references/quality-and-boundaries.md) before staging files.
4. Use [requirement-template.md](references/requirement-template.md) when a deterministic requirement document is requested; use [entry-application-example.md](references/entry-application-example.md) as the baseline example.
5. Generate only the exact frontend files named in the generation specification.
6. Render every confirmed business field by its exact `fieldCode`. Apply runtime `fieldPermissions` for visibility, editability, and required validation.
7. Normalize emitted `modelValue` values to the workflow field type: number controls must emit JavaScript numbers for non-empty values, not DOM input strings.
8. Keep `BusinessForm.vue` presentation-only: bind values, render controls, validate, and expose `validate()`. Do not submit, upload attachments, render workflow actions, or own loading/success/error state.
9. Make `Apply.vue` a complete standalone entry page for the business. It may own page-level layout, title, intro copy, and scoped visual styling, but it must delegate runtime workflow behavior to the shared `WorkflowStartShell` by passing the fixed `processCode` and generated `BusinessForm`.
10. Register the generated entry route as a standalone page route so it renders outside the `business-base` application shell. For the current target application this means adding `meta.public: true` to the generated route, because `App.vue` renders public routes directly through the top-level `RouterView`.
11. Merge the new route and form registry entry without deleting existing generated businesses.
12. Stage the complete file set and report completion only after all deterministic checks pass.

## Resolve rule conflicts

Apply rules in this order:

1. Security restrictions, output allowlist, and `GenerationTargetContract`.
2. Confirmed requirement and active process snapshot.
3. This skill and its references.
4. Explicit user-confirmed visual preferences.
5. Optional frontend design guidance.

Never let visual guidance introduce global styles, business-base navigation, duplicated workflow actions, or an API outside the shared Flow Mind contract.
