package com.flowmind.business.workflow.dto;

public class WorkflowStartableProcessResponse {
    private String processCode;
    private String processName;

    public WorkflowStartableProcessResponse() {
    }

    public WorkflowStartableProcessResponse(String processCode, String processName) {
        this.processCode = processCode;
        this.processName = processName;
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }
}
