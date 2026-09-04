import { describe, expect, it } from "vitest";
import { selectRetrievalRelease } from "../src/retrieval/retrieval-release-policy.js";

const base = {
  ragReleaseMode: "SHADOW" as const,
  ragCanaryPercent: 10,
  ragPolicyVersion: "RAG_RELEASE_TEST_V1",
  ragForceBm25: false,
};

describe("M6 retrieval release policy", () => {
  it("keeps shadow candidates out of the selected control path", () => {
    expect(selectRetrievalRelease("generation-1", base)).toMatchObject({
      releaseMode: "SHADOW",
      selectedMode: "BM25",
      shadowMode: "HYBRID",
      forcedFallback: false,
    });
  });

  it("uses a stable bucket and honors canary boundaries", () => {
    const first = selectRetrievalRelease("generation-stable", { ...base, ragReleaseMode: "CANARY" });
    const second = selectRetrievalRelease("generation-stable", { ...base, ragReleaseMode: "CANARY" });
    expect(first.bucket).toBe(second.bucket);
    expect(selectRetrievalRelease("generation-stable", { ...base, ragReleaseMode: "CANARY", ragCanaryPercent: 100 }).selectedMode).toBe("HYBRID");
    expect(selectRetrievalRelease("generation-stable", { ...base, ragReleaseMode: "CANARY", ragCanaryPercent: 0 }).selectedMode).toBe("BM25");
  });

  it("supports default enablement and an immediate BM25 kill switch", () => {
    expect(selectRetrievalRelease("generation-1", { ...base, ragReleaseMode: "HYBRID_DEFAULT" }).selectedMode).toBe("HYBRID");
    expect(selectRetrievalRelease("generation-1", { ...base, ragReleaseMode: "HYBRID_DEFAULT", ragForceBm25: true })).toMatchObject({
      selectedMode: "BM25",
      forcedFallback: true,
    });
    expect(selectRetrievalRelease("generation-1", { ...base, ragReleaseMode: "HYBRID_DEFAULT", ragForceBm25: true }).shadowMode).toBeUndefined();
  });
});
