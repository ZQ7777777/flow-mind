package com.flowmind.platform.api.entity.request;

public class RejectTaskRequest extends TaskOperationRequest {

    /** 驳回后要创建任务的目标用户任务节点编码。 */
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
