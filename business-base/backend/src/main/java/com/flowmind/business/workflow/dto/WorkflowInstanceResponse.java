package com.flowmind.business.workflow.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorkflowInstanceResponse {
    private String instanceId;
    private String definitionId;
    private String processCode;
    private String processName;
    private Integer version;
    private String instanceTitle;
    private String starterUserId;
    private String starterUserName;
    private String starterDepartmentId;
    private String instanceStatus;
    private List<String> currentNodeCodes = new ArrayList<String>();
    private List<String> currentNodeNames = new ArrayList<String>();
    private Map<String, Object> variables = new LinkedHashMap<String, Object>();
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }
    public String getProcessCode() { return processCode; }
    public void setProcessCode(String processCode) { this.processCode = processCode; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getInstanceTitle() { return instanceTitle; }
    public void setInstanceTitle(String instanceTitle) { this.instanceTitle = instanceTitle; }
    public String getStarterUserId() { return starterUserId; }
    public void setStarterUserId(String starterUserId) { this.starterUserId = starterUserId; }
    public String getStarterUserName() { return starterUserName; }
    public void setStarterUserName(String starterUserName) { this.starterUserName = starterUserName; }
    public String getStarterDepartmentId() { return starterDepartmentId; }
    public void setStarterDepartmentId(String starterDepartmentId) { this.starterDepartmentId = starterDepartmentId; }
    public String getInstanceStatus() { return instanceStatus; }
    public void setInstanceStatus(String instanceStatus) { this.instanceStatus = instanceStatus; }
    public List<String> getCurrentNodeCodes() { return currentNodeCodes; }
    public void setCurrentNodeCodes(List<String> currentNodeCodes) { this.currentNodeCodes = currentNodeCodes == null ? new ArrayList<String>() : currentNodeCodes; }
    public List<String> getCurrentNodeNames() { return currentNodeNames; }
    public void setCurrentNodeNames(List<String> currentNodeNames) { this.currentNodeNames = currentNodeNames == null ? new ArrayList<String>() : currentNodeNames; }
    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables == null ? new LinkedHashMap<String, Object>() : variables; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
}
