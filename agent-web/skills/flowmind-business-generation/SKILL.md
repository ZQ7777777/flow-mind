---
name: flowmind-business-generation
description: Generate or repair Flow Mind frontend-only Vue 3 business forms, standalone apply pages, optional read-only business API clients, tests, and route registrations from a confirmed requirement. Use for Agent Web generation targeting business-base; do not use for backend or workflow-runtime implementation.
---

# Flow Mind frontend business generation

Read the confirmed requirement, generation specification, target contract, and immutable golden-reference index before editing staged files.

- Generate exactly the files in the specification: five baseline frontend files and, only when requested by the specification, a read-only business API module plus its test.
- Keep `BusinessForm.vue` responsible for business fields, runtime field permissions, value normalization, confirmed read-only queries, calculations, checks, and `validate()`.
- `BusinessForm.vue` must only read or write form model keys declared in the confirmed requirement `formFields`; do not introduce hidden snapshot/cache/status fields from examples.
- Keep `Apply.vue` responsible only for standalone layout and composing the fixed process code, `BusinessForm`, and the shared `WorkflowStartShell`.
- Preserve unrelated generated routes and form-registry entries. Generated apply routes use authenticated `meta.standalone: true`, never anonymous `meta.public`.
- Treat sample code as guidance for decomposition, interaction quality, and tests. Never copy its business identity, fields, values, or fallback data into another business.

Read [generated-form-contract.md](references/generated-form-contract.md) for component and output contracts. Read [backend-api-contract.md](references/backend-api-contract.md) when the requirement uses reference data. Read [quality-and-boundaries.md](references/quality-and-boundaries.md) before reporting completion. Use [golden-example.md](references/golden-example.md) to interpret the warehouse-pledge reference bundle.
