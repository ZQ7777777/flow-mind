package com.flowmind.business.workflow.dto;

public class WorkflowUserCandidateResponse {
    private String userId;
    private String userName;
    private String departmentId;
    private String departmentName;

    public WorkflowUserCandidateResponse() {
    }

    public WorkflowUserCandidateResponse(String userId, String userName, String departmentId, String departmentName) {
        this.userId = userId;
        this.userName = userName;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
}