package com.flowmind.platform.api.entity.request;

/**
 * 流程定义操作请求。
 */
public class DefinitionOperationRequest extends OperationRequest {

    /** 流程定义 ID。 */
    private String definitionId;
    /** 操作人用户 ID。 */
    private String operatorUserId;

    public DefinitionOperationRequest() {
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
