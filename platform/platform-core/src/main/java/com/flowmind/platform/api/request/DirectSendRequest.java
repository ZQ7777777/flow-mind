package com.flowmind.platform.api.request;

public class DirectSendRequest extends TaskOperationRequest {

    /** 直送操作要回到的目标用户任务节点编码。 */
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
