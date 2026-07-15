package com.flowmind.platform.api.entity.request;

import java.util.Map;

public class UpdateVariablesRequest extends OperationRequest {

    /** 要更新流程变量的实例 ID。 */
    private String instanceId;
    /** 发起变量更新的用户 ID。 */
    private String operatorUserId;
    /** 要写入或覆盖的流程变量集合。 */
    private Map<String, Object> variables;

    public UpdateVariablesRequest() {
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

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
}
