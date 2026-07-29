package com.flowmind.platform.api.dto;

/** Result for manual callback outbox dispatch. */
public class CallbackDispatchResult {

    private Integer dispatchedCount;

    public Integer getDispatchedCount() {
        return dispatchedCount;
    }

    public void setDispatchedCount(Integer dispatchedCount) {
        this.dispatchedCount = dispatchedCount;
    }
}
