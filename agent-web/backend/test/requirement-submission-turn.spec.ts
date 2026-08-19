import { describe, expect, it, vi } from "vitest";
import { ENTRY_APPLICATION_REQUIREMENT } from "@flowmind/agent-contracts";
import { createRequirementSubmissionTurn } from "../src/pi/pi-adapter.service.js";

describe("requirement submission turn", () => {
  it("replays the first rejection until a new user turn instead of retrying", async () => {
    const submit = vi.fn()
      .mockResolvedValueOnce({
        accepted: false,
        action: "ASK_USER",
        missingItems: ["缺少 DELIVERY_REVIEWER"],
        ambiguities: [],
      })
      .mockResolvedValue({ accepted: true });
    const turn = createRequirementSubmissionTurn(submit);
    const params = {
      requirement: ENTRY_APPLICATION_REQUIREMENT,
      missingItems: [],
      ambiguities: [],
      readyForReview: true,
    };

    const rejected = await turn.submit(params);
    await expect(turn.submit(params)).resolves.toEqual(rejected);
    expect(submit).toHaveBeenCalledTimes(1);

    turn.reset();
    await expect(turn.submit(params)).resolves.toEqual({ accepted: true });
    expect(submit).toHaveBeenCalledTimes(2);
  });
});
