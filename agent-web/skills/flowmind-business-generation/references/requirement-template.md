# Requirement document template

Render the confirmed structured requirement deterministically in this order:

1. Business overview: business code, name, goal, entry display name, and page title.
2. Participants and responsibilities.
3. Form fields: code, name, type, control, required flag, default, options, validation, and order.
4. Attachments: code, name, extensions, size, counts, applicable nodes, and order.
5. Process nodes and edges.
6. Node field permissions: node code, field code, visible, editable, and required.
7. Business rules.
8. Runtime APIs and generated file list.
9. Acceptance criteria.

Sort list items by their explicit sort order and then stable code. Serialize validation and configuration objects with stable key ordering. Do not infer extra fields or approvals.
