package com.flowmind.platform.core.validation;

/**
 * 校验操作记录状态迁移。
 *
 * <p>幂等操作记录从 PROCESSING 进入 SUCCESS 或 FAILED 后即结束。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 */
public class OperationRecordStatusValidator {

    /** 操作记录只允许从处理中态进入确定结果态。 */
    private static final StatusTransitionRules RULES = new StatusTransitionRules()
            .allow("PROCESSING", "SUCCESS")
            .allow("PROCESSING", "FAILED");

    public void validateTransition(String sourceStatus, String targetStatus) {
        if (!RULES.allows(sourceStatus, targetStatus)) {
            throw new FrozenValidationException(
                    FrozenValidationErrorCodes.OPERATION_STATUS_TRANSITION_INVALID,
                    "Invalid operation record status transition: "
                            + sourceStatus + " -> " + targetStatus + ".");
        }
    }
}
