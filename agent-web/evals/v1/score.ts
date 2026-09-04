import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { evaluationCatalogV1 } from "./catalog.js";
import { scoreEvaluationRun, type EvaluationRun } from "./runner.js";

const resultPath = process.argv[2];
if (!resultPath) {
  console.error("Usage: npm run eval:score -- <evaluation-run.json>");
  process.exitCode = 1;
} else {
  const run = JSON.parse(await readFile(resolve(resultPath), "utf8")) as EvaluationRun;
  console.log(JSON.stringify(scoreEvaluationRun(evaluationCatalogV1, run), null, 2));
}
