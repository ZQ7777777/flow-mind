package com.flowmind.platform.api.enums;

/**
 * 流程图节点类型。
 */
public enum NodeTypeEnum {
    /** 开始节点。 */
    START,
    /** 用户任务节点。 */
    USER_TASK,
    /** 自动知会节点，不创建待办并在生成知会事件后继续推进。 */
    NOTICE,
    /** 排他网关。 */
    EXCLUSIVE_GATEWAY,
    /** 并行分支网关。 */
    PARALLEL_SPLIT_GATEWAY,
    /** 并行汇聚网关。 */
    PARALLEL_JOIN_GATEWAY,
    /** 结束节点。 */
    END
}
