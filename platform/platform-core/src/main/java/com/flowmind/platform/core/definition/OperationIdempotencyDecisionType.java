package com.flowmind.platform.core.definition;

/**
 * 操作幂等处理决策类型。
 *
 * @author Yuxin Xu
 * @since 2026-07-20
 */
public enum OperationIdempotencyDecisionType {
    /**
     * 新操作，调用方应继续执行业务。
     */
    NEW,
    /**
     * 已成功的相同请求，调用方应返回首次结果。
     */
    REPLAY_SUCCESS,
    /**
     * 已失败的相同请求，调用方应返回首次错误码。
     */
    REPLAY_FAILED,
    /**
     * 相同请求仍在有效租约内处理中。
     */
    IN_PROGRESS,
    /**
     * 相同请求的处理租约已过期，本次调用已接管并应继续执行业务。
     */
    TAKE_OVER,
    /**
     * 同一 operationId 对应不同动作或不同请求摘要。
     */
    CONFLICT
}
