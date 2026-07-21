package com.flowmind.platform.api.enums;

/**
 * 平台操作所针对的对象类型。
 *
 * @author FlowMind
 * @since 1.0.0
 */
public enum OperationTargetTypeEnum {
    /** 流程定义。 */
    DEFINITION,
    /** 流程实例。 */
    INSTANCE,
    /** 流程任务。 */
    TASK,
    /** 流程附件。 */
    ATTACHMENT
}
