import { describe, expect, it } from "vitest";
import {
  isResetSessionDisabled,
  isSessionSwitchConfirmationRequired,
  nextStepMessage,
} from "./workflow-presentation";

describe("workflow presentation", () => {
  it("describes completed writes as successful", () => {
    expect(nextStepMessage("COMPLETED")).toBe("代码已安全写入目标工程并登记到业务大厅；可重置当前会话开始新需求。");
  });

  it("uses state-specific failure guidance and a neutral unknown-state fallback", () => {
    expect(nextStepMessage("PROCESS_PROVISION_FAILED")).toContain("创建流程失败");
    expect(nextStepMessage("PROCESS_ACTIVATION_FAILED")).toContain("激活流程失败");
    expect(nextStepMessage("ARTIFACT_WRITE_FAILED")).toContain("写入工程失败");
    expect(nextStepMessage("BUSINESS_ENTRY_CONFIG_FAILED")).toContain("重试不会重复写入文件");
    expect(nextStepMessage("UNKNOWN")).toBe("当前状态暂无可执行的下一步操作。");
  });

  it("enables reset only when the snapshot contract allows it", () => {
    expect(isResetSessionDisabled(["RESET_SESSION"], false)).toBe(false);
    expect(isResetSessionDisabled([], false)).toBe(true);
    expect(isResetSessionDisabled(["RESET_SESSION"], true)).toBe(true);
  });
});

describe("isSessionSwitchConfirmationRequired", () => {
  it.each([
    "PROCESS_PROVISIONING",
    "PROCESS_ACTIVATING",
    "CODE_GENERATING",
    "CODE_VERIFYING",
    "CODE_REVIEWING",
    "CODE_REPAIRING",
    "WRITING_ARTIFACTS",
    "BUSINESS_ENTRY_CONFIGURING",
  ])("requires confirmation before leaving processing state %s", (state) => {
    expect(isSessionSwitchConfirmationRequired(state, "current", "history")).toBe(true);
  });

  it("does not confirm when reopening the current session or leaving an idle state", () => {
    expect(isSessionSwitchConfirmationRequired("CODE_GENERATING", "current", "current")).toBe(false);
    expect(isSessionSwitchConfirmationRequired("CODE_PIPELINE_FAILED", "current", "history")).toBe(false);
  });
});
