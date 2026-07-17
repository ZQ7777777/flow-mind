package com.flowmind.platform.api.enums;

/**
 * 平台异常告警类型。
 *
 * @author FlowMind
 * @since 1.0.0
 */
public enum AlertTypeEnum {
    /** 任务超时。 */
    TASK_TIMEOUT,
    /** 回调投递失败。 */
    CALLBACK_FAILED,
    /** 流程动作执行异常。 */
    ACTION_EXCEPTION
}
