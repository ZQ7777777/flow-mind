package com.flowmind.platform.api.request;

public class DeleteProcessInstanceRequest extends OperationRequest {

    /** 要删除的流程实例 ID。 */
    private String instanceId;
    /** 发起删除操作的用户 ID。 */
    private String operatorUserId;

    public DeleteProcessInstanceRequest() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
