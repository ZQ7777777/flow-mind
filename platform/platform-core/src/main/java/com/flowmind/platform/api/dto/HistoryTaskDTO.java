package com.flowmind.platform.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class HistoryTaskDTO {

    private String historyTaskId;
    private String instanceId;
    private String operationId;
    private String activeTaskId;
    private String nodeCode;
    private String taskGroupId;
    private String branchKey;
    private String assigneeUserId;
    private String assigneeUserName;
    private String delegateFromUserId;
    private String delegateFromUserName;
    private String comment;
    private Map<String, Object> variablesSnapshot;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public HistoryTaskDTO() {
    }

    public String getHistoryTaskId() {
        return historyTaskId;
    }

    public void setHistoryTaskId(String historyTaskId) {
        this.historyTaskId = historyTaskId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getActiveTaskId() {
        return activeTaskId;
    }

    public void setActiveTaskId(String activeTaskId) {
        this.activeTaskId = activeTaskId;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
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

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Map<String, Object> getVariablesSnapshot() {
        return variablesSnapshot;
    }

    public void setVariablesSnapshot(Map<String, Object> variablesSnapshot) {
        this.variablesSnapshot = variablesSnapshot;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
