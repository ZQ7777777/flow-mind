package com.flowmind.platform.core.validation;

/**
 * 校验流程实例状态迁移。
 *
 * <p>规则表只描述 M0.5 冻结允许的状态流向，不触发节点推进或任务生成。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 */
public class ProcessInstanceStatusValidator {

    /** 实例状态迁移白名单。终态只能归档，不能回退到运行态。 */
    private static final StatusTransitionRules RULES = new StatusTransitionRules()
            .allow("NOT_STARTED", "RUNNING")
            .allow("RUNNING", "COMPLETED")
            .allow("RUNNING", "TERMINATED")
            .allow("COMPLETED", "ARCHIVED")
            .allow("TERMINATED", "ARCHIVED");

    public void validateTransition(String sourceStatus, String targetStatus) {
        if (!RULES.allows(sourceStatus, targetStatus)) {
            throw new FrozenValidationException(
                    FrozenValidationErrorCodes.INSTANCE_STATUS_TRANSITION_INVALID,
                    "Invalid process instance status transition: "
                            + sourceStatus + " -> " + targetStatus + ".");
        }
    }
}
