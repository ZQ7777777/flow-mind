import { describe, expect, it } from "vitest";
import {
  chunkForLocalEmbedding,
  cosineSimilarity,
  embedLocal,
} from "../src/retrieval/local-semantic-embedding.js";

describe("M5 local semantic embedding", () => {
  it("is deterministic, normalized and maps domain synonyms into nearby features", () => {
    const chinese = embedLocal("填写采购申请并自动计算金额", ["BASE_FORM", "CALCULATION"]);
    const repeated = embedLocal("填写采购申请并自动计算金额", ["BASE_FORM", "CALCULATION"]);
    const english = embedLocal("application form with amount formula", ["BASE_FORM", "CALCULATION"]);
    const unrelated = embedLocal("organization directory approver", ["BASE_FORM"]);
    expect(chinese).toEqual(repeated);
    expect(chinese).toHaveLength(256);
    expect(cosineSimilarity(chinese, english)).toBeGreaterThan(cosineSimilarity(chinese, unrelated));
  });

  it("uses deterministic 800-codepoint chunks with 100-codepoint overlap", () => {
    const source = [...Array(1500)].map((_, index) => String.fromCodePoint(0x4e00 + (index % 100))).join("");
    const chunks = chunkForLocalEmbedding(source);
    expect(chunks.map((chunk) => [...chunk].length)).toEqual([800, 800]);
    expect([...chunks[0]].slice(-100).join("")).toBe([...chunks[1]].slice(0, 100).join(""));
  });
});
