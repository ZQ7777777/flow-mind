export const MAX_GENERATED_FILE_BYTES = 1024 * 1024;

export const REQUIRED_OUTPUT_PATTERNS = [
  "frontend/src/modules/generated/**/*",
  "frontend/src/api/generated/**/*",
  "frontend/src/router/generated-routes.ts",
] as const;

export const FRONTEND_FORM_ONLY_OUTPUT_PATTERNS = [
  "frontend/src/modules/generated/**/*",
  "frontend/src/router/generated-routes.ts",
] as const;
