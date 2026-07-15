package com.flowmind.platform.api.entity.request;

public class ForceCompleteRequest extends OperationRequest {

    /** 要强制办结的流程实例 ID。 */
    private String instanceId;
    /** 执行强制办结的管理员用户 ID。 */
    private String operatorUserId;
    /** 强制办结的说明或处理意见。 */
    private String comment;

    public ForceCompleteRequest() {
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
