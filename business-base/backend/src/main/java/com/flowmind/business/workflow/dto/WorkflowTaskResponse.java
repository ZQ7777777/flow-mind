package com.flowmind.business.workflow.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WorkflowTaskResponse {
    private String taskId;
    private String instanceId;
    private String processCode;
    private String processName;
    private String instanceTitle;
    private String starterUserId;
    private String starterUserName;
    private String nodeCode;
    private String nodeName;
    private List<String> candidateUserIds = new ArrayList<String>();
    private String assigneeUserId;
    private String assigneeUserName;
    private String delegateFromUserId;
    private String delegateFromUserName;
    private String taskStatus;
    private Long taskVersion;
    private LocalDateTime createdAt;
    private LocalDateTime dueAt;
    private String deadlineStatus;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getProcessCode() { return processCode; }
    public void setProcessCode(String processCode) { this.processCode = processCode; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public String getInstanceTitle() { return instanceTitle; }
    public void setInstanceTitle(String instanceTitle) { this.instanceTitle = instanceTitle; }
    public String getStarterUserId() { return starterUserId; }
    public void setStarterUserId(String starterUserId) { this.starterUserId = starterUserId; }
    public String getStarterUserName() { return starterUserName; }
    public void setStarterUserName(String starterUserName) { this.starterUserName = starterUserName; }
    public String getNodeCode() { return nodeCode; }
    public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public List<String> getCandidateUserIds() { return candidateUserIds; }
    public void setCandidateUserIds(List<String> candidateUserIds) { this.candidateUserIds = candidateUserIds == null ? new ArrayList<String>() : candidateUserIds; }
    public String getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(String assigneeUserId) { this.assigneeUserId = assigneeUserId; }
    public String getAssigneeUserName() { return assigneeUserName; }
    public void setAssigneeUserName(String assigneeUserName) { this.assigneeUserName = assigneeUserName; }
    public String getDelegateFromUserId() { return delegateFromUserId; }
    public void setDelegateFromUserId(String delegateFromUserId) { this.delegateFromUserId = delegateFromUserId; }
    public String getDelegateFromUserName() { return delegateFromUserName; }
    public void setDelegateFromUserName(String delegateFromUserName) { this.delegateFromUserName = delegateFromUserName; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public Long getTaskVersion() { return taskVersion; }
    public void setTaskVersion(Long taskVersion) { this.taskVersion = taskVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getDueAt() { return dueAt; }
    public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
    public String getDeadlineStatus() { return deadlineStatus; }
    public void setDeadlineStatus(String deadlineStatus) { this.deadlineStatus = deadlineStatus; }
}