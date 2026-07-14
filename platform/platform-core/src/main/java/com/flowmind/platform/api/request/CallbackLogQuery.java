package com.flowmind.platform.api.request;

import com.flowmind.platform.api.enums.CallbackStatus;

/**
 * 回调日志查询条件。
 */
public class CallbackLogQuery {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 事件类型。 */
    private String eventType;
    /** 回调状态。 */
    private CallbackStatus callbackStatus;
    /** 当前页码，从 1 开始。 */
    private Integer pageNo;
    /** 每页条数。 */
    private Integer pageSize;

    public CallbackLogQuery() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public CallbackStatus getCallbackStatus() {
        return callbackStatus;
    }

    public void setCallbackStatus(CallbackStatus callbackStatus) {
        this.callbackStatus = callbackStatus;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
