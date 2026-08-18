# Quality and boundaries

## Allowed output

Only write under:

- `business-base/frontend/src/modules/generated/**`
- `business-base/frontend/src/router/generated-routes.ts`

Generate the exact five-file specification. Do not create backend files, API clients, stores, global styles, application-level layouts, or shared workflow components. The generated `Apply.vue` may include its own scoped standalone page layout.

## Forbidden behavior

- Java, Spring, Controller, Service, DTO, Repository, or database code.
- Direct `/api/platform/**` access.
- `/api/generated/**/submit` or any business-specific submit endpoint.
- Calls to `fetch`, an HTTP client, start-submit, task submit, approve, reject, return, or attachment APIs from `BusinessForm.vue` or `Apply.vue`.
- Submit/reset buttons, opinions, task actions, attachments, workflow trace, business-base navigation shell, sidebar, or global CSS.
- `v-html`, unscoped styles, `body`, `html`, `#app`, or unrestricted `:global()` selectors.

## Required checks

- Preserve all existing route and registry entries.
- Render every confirmed field code exactly once and no unknown business fields.
- Exercise visible, editable, required, disabled, and readonly behavior in tests.
- Verify `BusinessForm.vue` emits immutable value updates and exposes validation.
- Verify number fields emit JavaScript numbers for non-empty values, not DOM input strings.
- Verify `Apply.vue` renders a standalone page, delegates workflow runtime behavior to the shared shell, and contains no submit implementation.
- Verify the generated apply route includes `meta.public: true` so direct visits render outside the `business-base` application shell.
- Keep styles scoped and content responsive as a direct-access standalone page.
