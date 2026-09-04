# Agent Web Evaluation Suite

`evals/v1` is the frozen M0 benchmark for the Agent Web code-generation upgrade. It does not call a model or modify the production generation path.

## Dataset

- 20 development tasks: 10 basic forms and 10 composite forms.
- 5 hidden regression tasks for modifying an existing module.
- 5 hidden adversarial tasks that must stop at the requirement gate.
- Every task contains a draft Requirement IR and runner-only acceptance assertions.

"Hidden" means the task and its assertions are withheld from the generation prompt and retrieval corpus. It is an evaluation boundary, not a claim that repository readers cannot inspect the source file. `agent-web/evals/**` must never be added to `GenerationContextRegistry` or the RAG corpus.

## Commands

From `agent-web`:

```powershell
npm run eval:typecheck
npm run eval:validate
npm run eval:test
npm run eval:score -- C:\absolute\path\to\evaluation-run.json
```

`eval:validate` checks the JSON Schema-compatible contract, exact split sizes, unique identifiers, cross-references, generation-mode boundaries, and the blocking-ambiguity rule. `eval:score` consumes an offline run record and prints aggregate metrics. Neither command invokes a model.

## Run record

The scorer accepts this shape:

```json
{
  "runVersion": "1.0",
  "catalogVersion": "1.0",
  "strategyVersion": "legacy-baseline",
  "model": "provider/model-version",
  "promptHash": "sha256-or-stable-hash",
  "contextHash": "sha256-or-stable-hash",
  "startedAt": "2026-09-04T00:00:00.000Z",
  "tasks": [
    {
      "taskId": "dev-basic-01-travel-expense",
      "attempt": 1,
      "generationSucceeded": true,
      "technicalGatesPassed": true,
      "businessAssertions": [
        { "assertionId": "ir-business-code", "status": "PASSED" }
      ],
      "repairRounds": 0,
      "durationMs": 1000,
      "inputTokens": 100,
      "outputTokens": 50
    }
  ]
}
```

Assertions omitted from a task result count as failures. This prevents a strategy from improving its score by skipping difficult checks.

## Freeze and promotion rules

- Version 1 tasks and assertions are frozen before generation-strategy work begins.
- Corrections require a documented catalog version change; do not silently edit the benchmark during optimization.
- Generated answers, golden source code, and repair outputs are excluded from retrieval.
- A future retrieval example may be promoted only after technical gates, business assertions, and human review all pass, and it must be tagged with its target-contract and component versions.

