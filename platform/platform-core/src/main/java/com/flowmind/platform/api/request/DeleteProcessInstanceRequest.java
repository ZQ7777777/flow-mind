package com.flowmind.platform.api.request;

public class DeleteProcessInstanceRequest extends OperationRequest {

    private String instanceId;
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
