import { describe, expect, it } from "vitest";
import { splitReviewContextItems, type ReviewContextItem } from "../src/review/reviewer.service.js";

describe("review context selection", () => {
  it("keeps target samples out of default context while preserving explicit example access", () => {
    const items = [
      item("skill:flowmind-business-generation:SKILL.md"),
      item("skill:flowmind-business-generation:references/generated-form-contract.md"),
      item("skill:flowmind-business-generation:references/quality-and-boundaries.md"),
      item("skill:flowmind-business-generation:references/backend-api-contract.md"),
      item("skill:flowmind-business-generation:references/golden-example.md"),
      item("skill:flowmind-business-generation:references/frontend-design.md"),
      item("reference:REPOSITORY:doc/example_process/warehouse.md"),
      item("reference:TARGET:frontend/src/modules/generated/sample/BusinessForm.vue"),
    ];

    const result = splitReviewContextItems(items);

    expect(result.defaultItems.map(({ key }) => key)).toEqual([
      "skill:flowmind-business-generation:SKILL.md",
      "skill:flowmind-business-generation:references/generated-form-contract.md",
      "skill:flowmind-business-generation:references/quality-and-boundaries.md",
      "skill:flowmind-business-generation:references/backend-api-contract.md",
      "skill:flowmind-business-generation:references/golden-example.md",
    ]);
    expect(result.exampleItems.map(({ key }) => key)).toEqual([
      "reference:TARGET:frontend/src/modules/generated/sample/BusinessForm.vue",
    ]);
    expect(result.exampleItems[0].read()).toBe(
      "reference:TARGET:frontend/src/modules/generated/sample/BusinessForm.vue",
    );
  });
});

function item(key: string): ReviewContextItem {
  return { key, sha256: key, required: false, read: () => key };
}
