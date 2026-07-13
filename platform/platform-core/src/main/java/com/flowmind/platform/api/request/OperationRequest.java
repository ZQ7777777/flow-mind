package com.flowmind.platform.api.request;

/**
 * Base request for operations that modify workflow platform state.
 */
public class OperationRequest {

    private String operationId;

    public OperationRequest() {
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }
}
