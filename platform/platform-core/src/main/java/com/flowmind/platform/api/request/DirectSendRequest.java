package com.flowmind.platform.api.request;

public class DirectSendRequest extends TaskOperationRequest {

    private String targetNodeCode;

    public DirectSendRequest() {
    }

    public String getTargetNodeCode() {
        return targetNodeCode;
    }

    public void setTargetNodeCode(String targetNodeCode) {
        this.targetNodeCode = targetNodeCode;
    }
}
