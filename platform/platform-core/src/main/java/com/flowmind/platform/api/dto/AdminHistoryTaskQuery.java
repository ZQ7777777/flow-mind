package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ActionTypeEnum;

import java.time.LocalDateTime;

/** Admin-side history task query conditions. */
public class AdminHistoryTaskQuery extends PageQuery {

    private String instanceId;
    private String assigneeUserId;
    private String processCode;
    private String nodeCode;
    private ActionTypeEnum actionType;
    private String taskGroupId;
    private String branchKey;
    private LocalDateTime completedFrom;
    private LocalDateTime completedTo;

    public AdminHistoryTaskQuery() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getAssigneeUserId() {
        return assigneeUserId;
    }

    public void setAssigneeUserId(String assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public ActionTypeEnum getActionType() {
        return actionType;
    }

    public void setActionType(ActionTypeEnum actionType) {
        this.actionType = actionType;
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

    public LocalDateTime getCompletedFrom() {
        return completedFrom;
    }

    public void setCompletedFrom(LocalDateTime completedFrom) {
        this.completedFrom = completedFrom;
    }

    public LocalDateTime getCompletedTo() {
        return completedTo;
    }

    public void setCompletedTo(LocalDateTime completedTo) {
        this.completedTo = completedTo;
    }
}
