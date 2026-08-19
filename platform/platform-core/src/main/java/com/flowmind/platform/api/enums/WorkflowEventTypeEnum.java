package com.flowmind.platform.api.enums;

/**
 * 工作流运行时动作产生的回调事件类型。
 */
public enum WorkflowEventTypeEnum {
    /** 流程发起。 */
    PROCESS_STARTED,
    /** 任务创建。 */
    TASK_CREATED,
    /** 自动知会消息创建。 */
    NOTICE_CREATED,
    /** 任务发送。 */
    TASK_SUBMITTED,
    /** 任务完成。 */
    TASK_COMPLETED,
    /** 流程驳回。 */
    PROCESS_REJECTED,
    /** 流程退回。 */
    PROCESS_RETURNED,
    /** 流程撤回。 */
    PROCESS_WITHDRAWN,
    /** 流程直送。 */
    PROCESS_DIRECT_SENT,
    /** 任务转办。 */
    TASK_TRANSFERRED,
    /** 任务加签。 */
    TASK_ADDED_SIGN,
    /** 任务被认领。 */
    TASK_CLAIMED,
    /** 任务取消认领。 */
    TASK_UNCLAIMED,
    /** 流程跳转。 */
    PROCESS_JUMPED,
    /** 流程终止。 */
    PROCESS_TERMINATED,
    /** 流程取消。 */
    PROCESS_CANCELED,
    /** 流程办结。 */
    PROCESS_COMPLETED
}
