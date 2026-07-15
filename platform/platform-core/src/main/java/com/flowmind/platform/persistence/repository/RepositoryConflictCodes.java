package com.flowmind.platform.persistence.repository;

/**
 * Repository 条件更新失败与上层业务错误码的映射契约。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 */
public final class RepositoryConflictCodes {

    /** 活动任务不存在、状态不允许或乐观锁版本已过期。 */
    public static final String TASK_CONCURRENT_MODIFIED = "FLOW_TASK_CONCURRENT_MODIFIED";

    /** 任务组在有限重试后仍发生状态或乐观锁冲突。 */
    public static final String TASK_GROUP_CONCURRENT_MODIFIED = "FLOW_TASK_GROUP_CONCURRENT_MODIFIED";

    private RepositoryConflictCodes() {
    }
}
