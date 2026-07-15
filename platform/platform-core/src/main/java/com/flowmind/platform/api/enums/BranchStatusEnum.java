package com.flowmind.platform.api.enums;

/**
 * 并行分支运行状态。
 */
public enum BranchStatusEnum {
    /** 分支运行中。 */
    RUNNING,
    /** 分支已到达汇聚点。 */
    ARRIVED,
    /** 分支已取消。 */
    CANCELED
}
