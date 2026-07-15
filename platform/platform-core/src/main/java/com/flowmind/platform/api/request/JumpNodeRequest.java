package com.flowmind.platform.api.request;

public class JumpNodeRequest extends OperationRequest {

    /** 要跳转的流程实例 ID。 */
    private String instanceId;
    /** 当前流程定义版本中的目标节点编码。 */
    private String targetNodeCode;
    /** 执行跳转的管理员用户 ID。 */
    private String operatorUserId;
    /** 跳转原因或处理说明。 */
    private String comment;

    public JumpNodeRequest() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getTargetNodeCode() {
        return targetNodeCode;
    }

    public void setTargetNodeCode(String targetNodeCode) {
        this.targetNodeCode = targetNodeCode;
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
