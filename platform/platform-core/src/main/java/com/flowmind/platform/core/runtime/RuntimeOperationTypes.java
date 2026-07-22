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
final class RuntimeOperationTypes {

    /** 仅创建未启动实例。 */
    static final String START_PROCESS = ActionTypeEnum.START.name();
    /** 创建运行中实例并推进到首批用户任务。 */
    static final String START_AND_SUBMIT = "START_AND_SUBMIT";
    /** 办理发起任务。 */
    static final String SUBMIT_TASK = ActionTypeEnum.SEND.name();
    /** 办理普通审批任务。 */
    static final String APPROVE_TASK = ActionTypeEnum.APPROVE.name();
    /** 合并流程变量快照。 */
    static final String UPDATE_VARIABLES = "UPDATE_VARIABLES";

    private RuntimeOperationTypes() {
    }
}
