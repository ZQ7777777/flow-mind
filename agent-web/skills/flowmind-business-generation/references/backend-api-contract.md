# Backend API contract

Generated form components do not call HTTP APIs. The shared `business-base` workflow shell owns all runtime requests.

## Start flow

- Load context with `GET /api/workflow/processes/{processCode}/start-context`.
- Submit with `POST /api/workflow/processes/{processCode}/start-submit`.
- Send multipart form data with a JSON `payload` part containing `definitionId`, `definitionVersion`, and `variables`.
- Add start attachments as file parts whose names equal their `attachmentCode`.
- Send `Idempotency-Key` for every state-changing request.

The start endpoint is shared by every generated business. Never generate `/api/generated/{businessCode}/submit` or a business-specific API client.

## Task actions

- Reload an actionable task with `GET /api/workflow/tasks/{taskId}`.
- Reload read-only instance data with `GET /api/workflow/instances/{instanceId}`.
- Resubmit a returned starter task with `POST /api/workflow/tasks/{taskId}/submit`.
- Approve with `POST /api/workflow/tasks/{taskId}/approve`.

The shared detail page owns these calls, buttons, comments, task versions, attachments, and action state. Generated form regions only display or edit the supplied variables.

## Runtime authority

Treat server-provided field and attachment permissions as authoritative. Client validation improves feedback but never replaces backend authorization or validation.
