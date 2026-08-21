# Generated frontend contract

Every business generates `BusinessForm.vue`, `Apply.vue`, their two tests, and the shared route registry. When the specification declares `hasBusinessApi`, also generate the business API module and adjacent API test.

`BusinessForm.vue` accepts `modelValue`, `fields`, `fieldPermissions`, `mode`, and `disabled`; emits an immutable `update:modelValue`; and exposes `validate(): Promise<boolean> | boolean`. Runtime permissions are authoritative. Number fields emit JavaScript numbers. Read-only business data flows through the generated typed API module.

For multi-select fields, use a compact, single-line collapsed selector with the same width and height as ordinary single-select controls. Open a vertical dropdown panel on click; each option must show a square checkbox on the left so selected and unselected states are explicit. Keep the value bound as `string[]`, and never leave all options expanded in the form body. Do not use native `<select multiple>` or any other expanded multi-select list. In Vue 3 with Element Plus, prefer `<el-select multiple>`; if its default option rendering does not show checkboxes, use an option slot for the checkbox visual while preserving `el-select` selection state, keyboard behavior, and multi-select logic. Do not add an expanded-list rule such as `select[multiple] { min-height: 84px; }`.

`Apply.vue` passes the fixed process code and generated form to `WorkflowStartShell`. The shell owns workflow context, attachments, submission, idempotency, and workflow status.

The route registry preserves existing entries, adds an authenticated standalone apply route, and registers the reusable form by exact process code.
