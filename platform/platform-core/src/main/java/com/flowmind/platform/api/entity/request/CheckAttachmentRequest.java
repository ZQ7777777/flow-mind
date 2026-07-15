package com.flowmind.platform.api.entity.request;

/**
 * 必传附件校验请求。
 */
public class CheckAttachmentRequest {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 节点编码。 */
    private String nodeCode;
    /** 操作人用户 ID。 */
    private String operatorUserId;

    public CheckAttachmentRequest() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
