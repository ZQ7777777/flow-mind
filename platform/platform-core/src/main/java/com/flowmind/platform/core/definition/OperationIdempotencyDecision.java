package com.flowmind.platform.core.definition;

import com.flowmind.platform.persistence.entity.ProcessOperationRecordEntity;
import lombok.Data;

/**
 * 操作幂等决策结果。
 *
 * @author Yuxin Xu
 * @since 2026-07-20
 */
@Data
public class OperationIdempotencyDecision {

    /**
     * 决策类型，典型值：NEW、REPLAY_SUCCESS、REPLAY_FAILED、IN_PROGRESS、TAKE_OVER、CONFLICT。
     */
    private final OperationIdempotencyDecisionType type;

    /**
     * 关联幂等操作记录；NEW 时为新插入记录，其他类型为已有记录。
     */
    private final ProcessOperationRecordEntity record;
}
