package com.flowmind.platform.api.enums;

/**
 * 提醒或催办的触发类型。
 *
 * @author FlowMind
 * @since 1.0.0
 */
public enum ReminderTypeEnum {
    /** 人工催办。 */
    MANUAL,
    /** 自动提醒。 */
    AUTO,
    /** 任务临期提醒。 */
    DUE_SOON,
    /** 任务超时提醒。 */
    TIMEOUT
}
