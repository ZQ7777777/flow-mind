package com.flowmind.platform.api.enums;

/**
 * 流程平台支持的操作动作类型。
 */
public enum ActionTypeEnum {
    /** 发起流程。 */
    START,
    /** 发送任务。 */
    SEND,
    /** 审批通过。 */
    APPROVE,
    /** 驳回流程。 */
    REJECT,
    /** 退回任务。 */
    RETURN,
    /** 撤回流程。 */
    WITHDRAW,
    /** 直送到指定节点或处理人。 */
    DIRECT_SEND,
    /** 转办任务。 */
    TRANSFER,
    /** 加签任务。 */
    ADD_SIGN,
    /** 跳转流程节点。 */
    JUMP,
    /** 终止流程。 */
    TERMINATE,
    /** 认领任务。 */
    CLAIM,
    /** 取消认领任务。 */
    UNCLAIM,
    /** 取消流程或任务。 */
    CANCEL,
    /** 强制办结流程。 */
    FORCE_COMPLETE,
    /** 归档流程。 */
    ARCHIVE,
    /** 启用灰度发布。 */
    ENABLE_GRAY,
    /** 关闭灰度发布。 */
    DISABLE_GRAY,
    /** 催办提醒。 */
    REMIND,
    /** 处理异常告警。 */
    ALERT_HANDLE
}
