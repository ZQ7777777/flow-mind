package com.flowmind.platform.api.request;

import java.util.Map;

public class StartProcessRequest extends OperationRequest {

    private String processCode;
    private String businessKey;
    private String instanceTitle;
    private String starterUserId;
    private String starterDeptId;
    private Map<String, Object> variables;

    public StartProcessRequest() {
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getInstanceTitle() {
        return instanceTitle;
    }

    public void setInstanceTitle(String instanceTitle) {
        this.instanceTitle = instanceTitle;
    }

    public String getStarterUserId() {
        return starterUserId;
    }

    public void setStarterUserId(String starterUserId) {
        this.starterUserId = starterUserId;
    }

    public String getStarterDeptId() {
        return starterDeptId;
    }

    public void setStarterDeptId(String starterDeptId) {
        this.starterDeptId = starterDeptId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
}
