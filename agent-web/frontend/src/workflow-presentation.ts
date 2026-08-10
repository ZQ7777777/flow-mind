export function nextStepMessage(state: string | undefined): string {
  switch (state) {
    case "COLLECTING":
      return "继续对话，直到结构化需求准备完成。";
    case "REQUIREMENT_REVIEW":
      return "检查并保存需求，然后完成人工门禁一。";
    case "PROCESS_REVIEW":
      return "检查流程图、字段、附件和校验结果，然后完成人工门禁二。";
    case "PROCESS_PROVISIONING":
    case "PROCESS_ACTIVATING":
    case "CODE_VERIFYING":
    case "CODE_REVIEWING":
    case "CODE_REPAIRING":
    case "WRITING_ARTIFACTS":
      return "Agent 正在和流程平台协作，请稍候。";
    case "PROCESS_PROVISION_FAILED":
      return "创建流程失败；请检查错误信息后重试失败步骤。";
    case "PROCESS_ACTIVATION_FAILED":
      return "激活流程失败；请检查错误信息后重试失败步骤。";
    case "PROCESS_ACTIVE":
      return "流程已激活；绑定并校验③工程后可按确认需求生成业务发起代码。";
    case "CODE_GENERATING":
      return "Generator 正在受限暂存区生成固定范围的代码和测试。";
    case "CODE_REVIEW":
      return "检查全部文件、编辑内容并查看与目标基线的 diff。";
    case "CODE_PIPELINE_FAILED":
      return "代码质量流水线失败；请修复目标前置条件或生成内容后重新验证。";
    case "ARTIFACT_WRITE_FAILED":
      return "写入工程失败；请根据错误信息确认回滚状态后再执行允许的重试操作。";
    case "COMPLETED":
      return "代码已安全写入目标工程；可重置当前会话开始新需求。";
    default:
      return "当前状态暂无可执行的下一步操作。";
  }
}

export function isResetSessionDisabled(allowedActions: string[], busy: boolean): boolean {
  return busy || !allowedActions.includes("RESET_SESSION");
}
