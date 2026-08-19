# Read-only business API contract

Generated business APIs may wrap only GET operations declared by the target's authoritative business reference-data document. Encode path and query values, send same-origin credentials, surface backend text errors, and return typed JSON.

The shared business-base workflow API owns `start-context` and `start-submit`. Generated code never creates a business-specific submit API and never calls task actions, attachment upload, or `/api/platform/**`.
