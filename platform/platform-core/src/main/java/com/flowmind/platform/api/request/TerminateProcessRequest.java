package com.flowmind.platform.api.request;

public class TerminateProcessRequest extends OperationRequest {

    /** 要终止的流程实例 ID。 */
    private String instanceId;
    /** 发起终止操作的用户 ID。 */
    private String operatorUserId;
    /** 终止原因或处理说明。 */
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
