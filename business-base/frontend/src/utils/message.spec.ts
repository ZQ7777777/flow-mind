import { describe, expect, it } from "vitest";
import { MESSAGE_TYPE_FILTERS, messageMeta, messageTypeLabel } from "./message";

describe("message display metadata", () => {
  it("labels and filters process notice messages", () => {
    expect(messageTypeLabel("PROCESS_NOTICE")).toBe("流程知会");
    expect(messageMeta("PROCESS_NOTICE").defaultTitle).toBe("流程知会");
    expect(MESSAGE_TYPE_FILTERS).toContainEqual({ value: "PROCESS_NOTICE", label: "流程知会" });
  });
});
