import { evaluationCatalogV1 } from "./catalog.js";
import { validateEvaluationCatalog } from "./runner.js";

const result = validateEvaluationCatalog(evaluationCatalogV1);
if (!result.valid) {
  console.error(JSON.stringify({ schemaErrors: result.schemaErrors, semanticErrors: result.semanticErrors }, null, 2));
  process.exitCode = 1;
} else {
  console.log(JSON.stringify({ catalogVersion: evaluationCatalogV1.catalogVersion, valid: true, counts: result.counts }, null, 2));
}
