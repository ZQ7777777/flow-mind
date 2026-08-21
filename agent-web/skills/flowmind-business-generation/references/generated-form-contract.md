# Generated frontend contract

Every business generates `BusinessForm.vue`, `Apply.vue`, their two tests, and the shared route registry. When the specification declares `hasBusinessApi`, also generate the business API module and adjacent API test.

`BusinessForm.vue` accepts `modelValue`, `fields`, `fieldPermissions`, `mode`, and `disabled`; emits an immutable `update:modelValue`; and exposes `validate(): Promise<boolean> | boolean`. Runtime permissions are authoritative. Number fields emit JavaScript numbers. Read-only business data flows through the generated typed API module.

For multi-select fields, follow the sample form pattern: use an expanded multi-select list control such as native `<select multiple>`, keep all available options visible in the form body, bind the value as `string[]`, and provide a stable minimum height so the form layout does not jump when options load or selection changes.

`Apply.vue` passes the fixed process code and generated form to `WorkflowStartShell`. The shell owns workflow context, attachments, submission, idempotency, and workflow status.

The route registry preserves existing entries, adds an authenticated standalone apply route, and registers the reusable form by exact process code.
