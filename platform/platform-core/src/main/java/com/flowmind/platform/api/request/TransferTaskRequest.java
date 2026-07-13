package com.flowmind.platform.api.request;

public class TransferTaskRequest extends TaskOperationRequest {

    private String targetUserId;

    public TransferTaskRequest() {
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }
}
