export const MAX_GENERATED_FILE_BYTES = 1024 * 1024;

export const ENTRY_APPLICATION_FILES = [
  "backend/src/main/java/com/flowmind/business/generated/entryapplication/EntryApplicationController.java",
  "backend/src/main/java/com/flowmind/business/generated/entryapplication/EntryApplicationService.java",
  "backend/src/main/java/com/flowmind/business/generated/entryapplication/dto/EntryApplicationSubmitRequest.java",
  "backend/src/main/java/com/flowmind/business/generated/entryapplication/dto/EntryApplicationSubmitResponse.java",
  "backend/src/test/java/com/flowmind/business/generated/entryapplication/EntryApplicationControllerTest.java",
  "backend/src/test/java/com/flowmind/business/generated/entryapplication/EntryApplicationServiceTest.java",
  "frontend/src/modules/generated/entry-application/EntryApplicationApply.vue",
  "frontend/src/modules/generated/__tests__/EntryApplicationApply.spec.ts",
  "frontend/src/api/generated/entry-application.ts",
  "frontend/src/api/generated/entry-application.spec.ts",
  "frontend/src/router/generated-routes.ts",
] as const;

export const REQUIRED_OUTPUT_PATTERNS = [
  "backend/src/main/java/com/flowmind/business/generated/**/*.java",
  "backend/src/test/java/com/flowmind/business/generated/**/*.java",
  "frontend/src/modules/generated/**/*",
  "frontend/src/api/generated/**/*",
  "frontend/src/router/generated-routes.ts",
] as const;
