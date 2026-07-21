package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.CallbackStatusEnum;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;

/**
 * 回调日志查询条件。
 */
public class CallbackLogQuery extends PageQuery {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 事件类型。 */
    private WorkflowEventTypeEnum eventType;
    /** 回调状态。 */
    private CallbackStatusEnum callbackStatus;
    /** 当前页码，从 1 开始。 */
    /** 每页条数。 */

    public CallbackLogQuery() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public WorkflowEventTypeEnum getEventType() {
        return eventType;
    }

    public void setEventType(WorkflowEventTypeEnum eventType) {
        this.eventType = eventType;
    }

    public CallbackStatusEnum getCallbackStatus() {
        return callbackStatus;
    }

    public void setCallbackStatus(CallbackStatusEnum callbackStatus) {
        this.callbackStatus = callbackStatus;
    }

}
