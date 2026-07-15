package com.flowmind.platform.core.validation;

/**
 * 校验活动任务状态迁移。
 *
 * <p>仅校验 ACTIVE、CLAIMED、COMPLETED、CANCELED 的合法流向，
 * 不负责认领人、版本号或数据库原子更新。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 */
public class ActiveTaskStatusValidator {

    /** 活动任务迁移白名单：完成和取消为终态，终态不可再次流转。 */
    private static final StatusTransitionRules RULES = new StatusTransitionRules()
            .allow("ACTIVE", "CLAIMED")
            .allow("ACTIVE", "COMPLETED")
            .allow("ACTIVE", "CANCELED")
            .allow("CLAIMED", "ACTIVE")
            .allow("CLAIMED", "COMPLETED")
            .allow("CLAIMED", "CANCELED");

    public void validateTransition(String sourceStatus, String targetStatus) {
        if (!RULES.allows(sourceStatus, targetStatus)) {
            throw new FrozenValidationException(
                    FrozenValidationErrorCodes.TASK_STATUS_TRANSITION_INVALID,
                    "Invalid active task status transition: "
                            + sourceStatus + " -> " + targetStatus + ".");
        }
    }
}
