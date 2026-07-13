package com.flowmind.platform.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public class TaskDTO {

    private String taskId;
    private String instanceId;
    private String definitionId;
    private String nodeCode;
    private String nodeName;
    private List<String> candidateUserIds;
    private String assigneeUserId;
    private String assigneeUserName;
    private String delegateFromUserId;
    private String delegateFromUserName;
    private String taskGroupId;
    private String branchKey;
    private Long taskVersion;
    private LocalDateTime createdAt;
    private LocalDateTime dueAt;

    public TaskDTO() {
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
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

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public List<String> getCandidateUserIds() {
        return candidateUserIds;
    }

    public void setCandidateUserIds(List<String> candidateUserIds) {
        this.candidateUserIds = candidateUserIds;
    }

    public String getAssigneeUserId() {
        return assigneeUserId;
    }

    public void setAssigneeUserId(String assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
    }

    public String getAssigneeUserName() {
        return assigneeUserName;
    }

    public void setAssigneeUserName(String assigneeUserName) {
        this.assigneeUserName = assigneeUserName;
    }

    public String getDelegateFromUserId() {
        return delegateFromUserId;
    }

    public void setDelegateFromUserId(String delegateFromUserId) {
        this.delegateFromUserId = delegateFromUserId;
    }

    public String getDelegateFromUserName() {
        return delegateFromUserName;
    }

    public void setDelegateFromUserName(String delegateFromUserName) {
        this.delegateFromUserName = delegateFromUserName;
    }

    public String getTaskGroupId() {
        return taskGroupId;
    }

    public void setTaskGroupId(String taskGroupId) {
        this.taskGroupId = taskGroupId;
    }

    public String getBranchKey() {
        return branchKey;
    }

    public void setBranchKey(String branchKey) {
        this.branchKey = branchKey;
    }

    public Long getTaskVersion() {
        return taskVersion;
    }

    public void setTaskVersion(Long taskVersion) {
        this.taskVersion = taskVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }
}
