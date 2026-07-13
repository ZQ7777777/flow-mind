package com.flowmind.platform.api.request;

public class RejectTaskRequest extends TaskOperationRequest {

    private String targetNodeCode;

    public RejectTaskRequest() {
    }

    public String getTargetNodeCode() {
        return targetNodeCode;
    }

    public void setTargetNodeCode(String targetNodeCode) {
        this.targetNodeCode = targetNodeCode;
    }
}
