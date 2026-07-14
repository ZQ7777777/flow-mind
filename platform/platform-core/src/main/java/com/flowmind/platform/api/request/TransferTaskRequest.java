package com.flowmind.platform.api.request;

public class TransferTaskRequest extends TaskOperationRequest {

    /** 转办后接收当前任务的目标用户 ID。 */
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
