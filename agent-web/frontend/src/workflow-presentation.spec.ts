import { describe, expect, it } from "vitest";
import { isResetSessionDisabled, nextStepMessage } from "./workflow-presentation";

describe("workflow presentation", () => {
  it("describes completed writes as successful", () => {
    expect(nextStepMessage("COMPLETED")).toBe("代码已安全写入目标工程；可重置当前会话开始新需求。");
  });

  it("uses state-specific failure guidance and a neutral unknown-state fallback", () => {
    expect(nextStepMessage("PROCESS_PROVISION_FAILED")).toContain("创建流程失败");
    expect(nextStepMessage("PROCESS_ACTIVATION_FAILED")).toContain("激活流程失败");
    expect(nextStepMessage("ARTIFACT_WRITE_FAILED")).toContain("写入工程失败");
    expect(nextStepMessage("UNKNOWN")).toBe("当前状态暂无可执行的下一步操作。");
  });

  it("enables reset only when the snapshot contract allows it", () => {
    expect(isResetSessionDisabled(["RESET_SESSION"], false)).toBe(false);
    expect(isResetSessionDisabled([], false)).toBe(true);
    expect(isResetSessionDisabled(["RESET_SESSION"], true)).toBe(true);
  });
});
