import AjvModule, { type ErrorObject } from "ajv";
import {
  evaluationCatalogSchema,
  type EvaluationCatalog,
  type EvaluationTask,
} from "@flowmind/agent-contracts";

export interface CatalogValidationResult {
  valid: boolean;
  schemaErrors: ErrorObject[];
  semanticErrors: string[];
  counts: {
    total: number;
    development: number;
    hiddenRegression: number;
    hiddenAdversarial: number;
    basic: number;
    composite: number;
    extension: number;
    adversarial: number;
  };
}

export type AssertionRunStatus = "PASSED" | "FAILED" | "NOT_RUN";

export interface EvaluationTaskRun {
  taskId: string;
  attempt: number;
  generationSucceeded: boolean;
  technicalGatesPassed: boolean;
  businessAssertions: Array<{ assertionId: string; status: AssertionRunStatus }>;
  repairRounds: number;
  durationMs: number;
  inputTokens?: number;
  outputTokens?: number;
  failureCategory?: string;
}

export interface EvaluationRun {
  runVersion: "1.0";
  catalogVersion: "1.0";
  strategyVersion: string;
  model: string;
  promptHash: string;
  contextHash: string;
  startedAt: string;
  tasks: EvaluationTaskRun[];
}

export interface EvaluationMetrics {
  taskAttempts: number;
  uniqueTasks: number;
  generationSuccessRate: number;
  technicalGatePassRate: number;
  criticalAssertionPassRate: number;
  nonCriticalAssertionPassRate: number;
  averageRepairRounds: number;
  p95DurationMs: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  failuresByCategory: Record<string, number>;
}

const ajv = new (AjvModule as any)({ allErrors: true, strict: false });
const validateSchema = ajv.compile(evaluationCatalogSchema);

export function validateEvaluationCatalog(input: unknown): CatalogValidationResult {
  const structurallyValid = validateSchema(input);
  const catalog = input as EvaluationCatalog;
  const counts = countTasks(structurallyValid ? catalog.tasks : []);
  const semanticErrors = structurallyValid ? validateSemantics(catalog) : [];
  return {
    valid: Boolean(structurallyValid) && semanticErrors.length === 0,
    schemaErrors: [...(validateSchema.errors ?? [])],
    semanticErrors,
    counts,
  };
}

export function scoreEvaluationRun(catalog: EvaluationCatalog, run: EvaluationRun): EvaluationMetrics {
  if (run.catalogVersion !== catalog.catalogVersion) {
    throw new Error(`Run catalog ${run.catalogVersion} does not match ${catalog.catalogVersion}.`);
  }
  if (!run.tasks.length) throw new Error("Evaluation run contains no task attempts.");

  const taskById = new Map(catalog.tasks.map((task) => [task.taskId, task]));
  let criticalPassed = 0;
  let criticalTotal = 0;
  let nonCriticalPassed = 0;
  let nonCriticalTotal = 0;
  const failuresByCategory: Record<string, number> = {};

  for (const attempt of run.tasks) {
    const task = taskById.get(attempt.taskId);
    if (!task) throw new Error(`Unknown evaluation task: ${attempt.taskId}.`);
    const expectedAssertions = new Map(task.assertions.map((item) => [item.assertionId, item]));
    const seen = new Set<string>();
    for (const result of attempt.businessAssertions) {
      const expected = expectedAssertions.get(result.assertionId);
      if (!expected) throw new Error(`Unknown assertion ${attempt.taskId}.${result.assertionId}.`);
      if (seen.has(result.assertionId)) throw new Error(`Duplicate assertion result ${attempt.taskId}.${result.assertionId}.`);
      seen.add(result.assertionId);
      if (expected.severity === "CRITICAL") {
        criticalTotal += 1;
        if (result.status === "PASSED") criticalPassed += 1;
      } else {
        nonCriticalTotal += 1;
        if (result.status === "PASSED") nonCriticalPassed += 1;
      }
    }
    for (const expected of task.assertions) {
      if (seen.has(expected.assertionId)) continue;
      if (expected.severity === "CRITICAL") criticalTotal += 1;
      else nonCriticalTotal += 1;
    }
    if (attempt.failureCategory) {
      failuresByCategory[attempt.failureCategory] = (failuresByCategory[attempt.failureCategory] ?? 0) + 1;
    }
  }

  const durations = run.tasks.map(({ durationMs }) => durationMs).sort((left, right) => left - right);
  const p95Index = Math.max(0, Math.ceil(durations.length * 0.95) - 1);
  return {
    taskAttempts: run.tasks.length,
    uniqueTasks: new Set(run.tasks.map(({ taskId }) => taskId)).size,
    generationSuccessRate: ratio(run.tasks.filter(({ generationSucceeded }) => generationSucceeded).length, run.tasks.length),
    technicalGatePassRate: ratio(run.tasks.filter(({ technicalGatesPassed }) => technicalGatesPassed).length, run.tasks.length),
    criticalAssertionPassRate: ratio(criticalPassed, criticalTotal),
    nonCriticalAssertionPassRate: ratio(nonCriticalPassed, nonCriticalTotal),
    averageRepairRounds: average(run.tasks.map(({ repairRounds }) => repairRounds)),
    p95DurationMs: durations[p95Index] ?? 0,
    totalInputTokens: sum(run.tasks.map(({ inputTokens }) => inputTokens ?? 0)),
    totalOutputTokens: sum(run.tasks.map(({ outputTokens }) => outputTokens ?? 0)),
    failuresByCategory,
  };
}

function validateSemantics(catalog: EvaluationCatalog): string[] {
  const errors: string[] = [];
  const taskIds = new Set<string>();
  for (const task of catalog.tasks) {
    if (taskIds.has(task.taskId)) errors.push(`Duplicate taskId: ${task.taskId}.`);
    taskIds.add(task.taskId);
    validateTask(task, errors);
  }

  const counts = countTasks(catalog.tasks);
  const expected: Record<keyof typeof counts, number> = {
    total: 30, development: 20, hiddenRegression: 5, hiddenAdversarial: 5,
    basic: 10, composite: 10, extension: 5, adversarial: 5,
  };
  for (const [name, expectedCount] of Object.entries(expected)) {
    const actual = counts[name as keyof typeof counts];
    if (actual !== expectedCount) errors.push(`Expected ${expectedCount} ${name} tasks, found ${actual}.`);
  }
  return errors;
}

function validateTask(task: EvaluationTask, errors: string[]): void {
  const prefix = task.taskId;
  const fieldCodes = uniqueCodes(task.requirementIr.fields.map(({ fieldCode }) => fieldCode), `${prefix}.fields`, errors);
  const queryCodes = uniqueCodes(task.requirementIr.dataQueries.map(({ queryCode }) => queryCode), `${prefix}.dataQueries`, errors);
  uniqueCodes(task.requirementIr.calculations.map(({ calculationCode }) => calculationCode), `${prefix}.calculations`, errors);
  uniqueCodes(task.requirementIr.checks.map(({ checkCode }) => checkCode), `${prefix}.checks`, errors);
  uniqueCodes(task.assertions.map(({ assertionId }) => assertionId), `${prefix}.assertions`, errors);

  for (const section of task.requirementIr.sections) {
    for (const fieldCode of section.fieldCodes) assertReference(fieldCodes, fieldCode, `${prefix}.sections.${section.sectionCode}`, errors);
  }
  for (const fieldCode of task.requirementIr.submission.payloadFieldCodes) {
    assertReference(fieldCodes, fieldCode, `${prefix}.submission`, errors);
  }
  for (const field of task.requirementIr.fields) {
    if (field.multiple && field.control !== "SELECT") errors.push(`${prefix}.${field.fieldCode} is multiple but not SELECT.`);
    for (const dependency of Object.values(field.referenceData?.parameterBindings ?? {})) {
      assertReference(fieldCodes, dependency, `${prefix}.fields.${field.fieldCode}.referenceData`, errors);
      if (dependency === field.fieldCode) errors.push(`${prefix}.${field.fieldCode} reference data depends on itself.`);
    }
    for (const target of Object.values(field.referenceData?.autofillBindings ?? {})) {
      assertReference(fieldCodes, target, `${prefix}.fields.${field.fieldCode}.referenceData`, errors);
    }
  }
  for (const query of task.requirementIr.dataQueries) {
    for (const fieldCode of Object.values(query.parameterBindings)) assertReference(fieldCodes, fieldCode, `${prefix}.dataQueries.${query.queryCode}`, errors);
  }
  for (const calculation of task.requirementIr.calculations) {
    assertReference(fieldCodes, calculation.targetFieldCode, `${prefix}.calculations.${calculation.calculationCode}`, errors);
    for (const fieldCode of calculation.dependencyFieldCodes) assertReference(fieldCodes, fieldCode, `${prefix}.calculations.${calculation.calculationCode}`, errors);
    if (calculation.dependencyFieldCodes.includes(calculation.targetFieldCode)) errors.push(`${prefix}.${calculation.calculationCode} directly depends on its target.`);
  }
  for (const check of task.requirementIr.checks) {
    for (const fieldCode of check.dependencyFieldCodes) assertReference(fieldCodes, fieldCode, `${prefix}.checks.${check.checkCode}`, errors);
    for (const queryCode of check.dataQueryCodes) assertReference(queryCodes, queryCode, `${prefix}.checks.${check.checkCode}`, errors);
  }

  const openBlocking = task.requirementIr.ambiguities.some(({ impact, status }) => impact === "BLOCKING" && status === "OPEN");
  if (task.expectedOutcome === "GENERATION_READY" && openBlocking) errors.push(`${prefix} is ready but has an open blocking ambiguity.`);
  if (task.expectedOutcome === "BLOCKED_REQUIREMENT" && !openBlocking) errors.push(`${prefix} is blocked without an open blocking ambiguity.`);
  if (task.requirementIr.generationMode === "MODIFY_EXISTING_MODULE" && !task.input.existingModule) errors.push(`${prefix} modifies a module but has no existingModule.`);
  if (task.requirementIr.generationMode === "CREATE_STANDARD_MODULE" && task.input.existingModule) errors.push(`${prefix} creates a module but declares existingModule.`);
}

function uniqueCodes(values: string[], label: string, errors: string[]): Set<string> {
  const result = new Set<string>();
  for (const value of values) {
    if (result.has(value)) errors.push(`Duplicate code in ${label}: ${value}.`);
    result.add(value);
  }
  return result;
}

function assertReference(allowed: Set<string>, value: string, label: string, errors: string[]): void {
  if (!allowed.has(value)) errors.push(`${label} references unknown code ${value}.`);
}

function countTasks(tasks: EvaluationTask[]): CatalogValidationResult["counts"] {
  return {
    total: tasks.length,
    development: tasks.filter(({ split }) => split === "DEVELOPMENT").length,
    hiddenRegression: tasks.filter(({ split }) => split === "HIDDEN_REGRESSION").length,
    hiddenAdversarial: tasks.filter(({ split }) => split === "HIDDEN_ADVERSARIAL").length,
    basic: tasks.filter(({ difficulty }) => difficulty === "BASIC").length,
    composite: tasks.filter(({ difficulty }) => difficulty === "COMPOSITE").length,
    extension: tasks.filter(({ difficulty }) => difficulty === "EXTENSION").length,
    adversarial: tasks.filter(({ difficulty }) => difficulty === "ADVERSARIAL").length,
  };
}

function ratio(numerator: number, denominator: number): number {
  return denominator ? numerator / denominator : 1;
}

function sum(values: number[]): number {
  return values.reduce((total, value) => total + value, 0);
}

function average(values: number[]): number {
  return values.length ? sum(values) / values.length : 0;
}
