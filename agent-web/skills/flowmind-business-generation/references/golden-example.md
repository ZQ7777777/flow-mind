# Warehouse pledge golden reference

The immutable context contains the original “仓单、国债（解）质押申请” requirement and the current sample form, apply wrapper, read-only API, and tests.

Use it to learn decomposition, account/exchange/product cascades, autofill, visible calculations/checks, and behavior-oriented tests. It is a soft example: derive every field, query, calculation, check, label, and process code from the current confirmed requirement and generation specification.

Do not copy hidden model fields, cached snapshots, or workflow state helpers from the golden bundle. A generated form may read or write a model key only when that exact `fieldCode` exists in the current confirmed requirement `formFields`.
