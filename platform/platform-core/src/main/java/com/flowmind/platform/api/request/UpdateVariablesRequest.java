package com.flowmind.platform.api.request;

import java.util.Map;

public class UpdateVariablesRequest extends OperationRequest {

    private String instanceId;
    private String operatorUserId;
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
