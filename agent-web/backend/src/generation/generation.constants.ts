export const MAX_GENERATED_FILE_BYTES = 1024 * 1024;

export const REQUIRED_OUTPUT_PATTERNS = [
  "backend/src/main/java/com/flowmind/business/generated/**/*.java",
  "backend/src/test/java/com/flowmind/business/generated/**/*.java",
  "frontend/src/modules/generated/**/*",
  "frontend/src/api/generated/**/*",
  "frontend/src/router/generated-routes.ts",
] as const;
