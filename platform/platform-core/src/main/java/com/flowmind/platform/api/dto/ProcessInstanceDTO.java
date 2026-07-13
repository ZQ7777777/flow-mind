package com.flowmind.platform.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ProcessInstanceDTO {

    private String instanceId;
    private String definitionId;
    private String attachmentConfigId;
    private String processCode;
    private String processName;
    private Integer version;
    private String instanceTitle;
    private String businessKey;
    private String starterUserId;
    private String starterUserName;
    private String starterDeptId;
    private List<String> currentNodeCodes;
    private Map<String, Object> variables;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private List<TaskDTO> createdTasks;

    public ProcessInstanceDTO() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getAttachmentConfigId() {
        return attachmentConfigId;
    }

    public void setAttachmentConfigId(String attachmentConfigId) {
        this.attachmentConfigId = attachmentConfigId;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getInstanceTitle() {
        return instanceTitle;
    }

    public void setInstanceTitle(String instanceTitle) {
        this.instanceTitle = instanceTitle;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getStarterUserId() {
        return starterUserId;
    }

    public void setStarterUserId(String starterUserId) {
        this.starterUserId = starterUserId;
    }

    public String getStarterUserName() {
        return starterUserName;
    }

    public void setStarterUserName(String starterUserName) {
        this.starterUserName = starterUserName;
    }

    public String getStarterDeptId() {
        return starterDeptId;
    }

    public void setStarterDeptId(String starterDeptId) {
        this.starterDeptId = starterDeptId;
    }

    public List<String> getCurrentNodeCodes() {
        return currentNodeCodes;
    }

    public void setCurrentNodeCodes(List<String> currentNodeCodes) {
        this.currentNodeCodes = currentNodeCodes;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public List<TaskDTO> getCreatedTasks() {
        return createdTasks;
    }

    public void setCreatedTasks(List<TaskDTO> createdTasks) {
        this.createdTasks = createdTasks;
    }
}
