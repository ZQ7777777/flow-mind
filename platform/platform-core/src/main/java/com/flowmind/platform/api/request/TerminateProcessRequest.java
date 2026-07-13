package com.flowmind.platform.api.request;

public class TerminateProcessRequest extends OperationRequest {

    private String instanceId;
    private String operatorUserId;
    private String comment;

    public TerminateProcessRequest() {
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

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
