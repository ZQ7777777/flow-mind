package com.flowmind.platform.api.request;

public class DelegateTaskRequest extends TaskOperationRequest {

    private String targetUserId;

    private String targetUserName;

    public DelegateTaskRequest() {
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getTargetUserName() {
        return targetUserName;
    }

    public void setTargetUserName(String targetUserName) {
        this.targetUserName = targetUserName;
    }
}
