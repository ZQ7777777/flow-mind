package com.flowmind.platform.api.request;

/** Admin request for manually dispatching pending callback outbox rows. */
public class CallbackDispatchRequest {

    private String operatorUserId;
    private Integer limit;

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
