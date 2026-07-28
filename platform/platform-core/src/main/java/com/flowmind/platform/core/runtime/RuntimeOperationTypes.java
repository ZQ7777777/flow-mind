package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.enums.ActionTypeEnum;

/**
 * 运行时幂等记录的内部动作名。
 *
 * <p>这里的值仅写入 {@code process_operation_record.action_type}，用于区分同一请求 DTO
 * 但服务语义不同的操作；它不改变历史任务或回调事件的 {@link ActionTypeEnum} 语义。</p>
 *
 * @author FlowMind
 * @since 2026-07-22
 */
public final class RuntimeOperationTypes {

    /** 仅创建未启动实例。 */
    public static final String START_PROCESS = ActionTypeEnum.START.name();
    /** 创建运行中实例并推进到首批用户任务。 */
    public static final String START_AND_SUBMIT = "START_AND_SUBMIT";
    /** 办理发起任务。 */
    public static final String SUBMIT_TASK = ActionTypeEnum.SEND.name();
    /** 办理普通审批任务。 */
    public static final String APPROVE_TASK = ActionTypeEnum.APPROVE.name();
    /** 合并流程变量快照。 */
    public static final String UPDATE_VARIABLES = "UPDATE_VARIABLES";
    /** 终止运行中的流程实例。 */
    public static final String TERMINATE = ActionTypeEnum.TERMINATE.name();
    /** 删除流程实例复用既有取消动作语义。 */
    public static final String DELETE_INSTANCE = ActionTypeEnum.CANCEL.name();
    /** 管理员跳转流程节点。 */
    public static final String JUMP = ActionTypeEnum.JUMP.name();
    /** 管理员强制办结流程实例。 */
    public static final String FORCE_COMPLETE = ActionTypeEnum.FORCE_COMPLETE.name();
    public static final String REJECT = ActionTypeEnum.REJECT.name();
    public static final String RETURN = ActionTypeEnum.RETURN.name();
    public static final String WITHDRAW = ActionTypeEnum.WITHDRAW.name();
    public static final String DIRECT_SEND = ActionTypeEnum.DIRECT_SEND.name();
    public static final String TRANSFER = ActionTypeEnum.TRANSFER.name();
    public static final String ADD_SIGN = ActionTypeEnum.ADD_SIGN.name();
    public static final String CLAIM = ActionTypeEnum.CLAIM.name();
    public static final String UNCLAIM = ActionTypeEnum.UNCLAIM.name();
    public static final String REMIND = ActionTypeEnum.REMIND.name();
    public static final String ALERT_HANDLE = ActionTypeEnum.ALERT_HANDLE.name();

    /** 上传实例或任务附件。 */
    public static final String ATTACHMENT_UPLOAD = "ATTACHMENT_UPLOAD";
    /** 删除运行时附件。 */
    public static final String ATTACHMENT_DELETE = "ATTACHMENT_DELETE";

    private RuntimeOperationTypes() {
    }
}
