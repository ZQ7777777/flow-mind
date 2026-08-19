# Quality and boundaries

- Output is limited to generated frontend modules, optional generated frontend APIs, and the generated route registry.
- Never generate Java, backend, database, platform runtime, or business-specific workflow submission code.
- `BusinessForm.vue` and `Apply.vue` must not call `fetch`, create `FormData`, own attachments/idempotency, or implement start/task/approval actions.
- A generated API module is read-only: GET requests to declared business reference-data endpoints only. No `/api/platform/**`, workflow mutation, upload, POST, PUT, PATCH, or DELETE.
- Preserve unrelated routes and form registrations. Keep styles scoped and controls keyboard accessible.

Tests cover observable permissions, numeric normalization, cascading clears, query errors, calculations/checks, API URL/parameters, and shared-shell delegation. Do not assert private Vue state or framework CSS internals.
