package com.flowmind.business.workflow.dto;

import java.time.LocalDateTime;

public class WorkflowHistoryTaskResponse {
    private String historyTaskId;
    private String instanceId;
    private String activeTaskId;
    private String processCode;
    private String processName;
    private String instanceTitle;
    private String nodeCode;
    private String nodeName;
    private String assigneeUserId;
    private String assigneeUserName;
    private String delegateFromUserId;
    private String delegateFromUserName;
    private String handleType;
    private String actionType;
    private String comment;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private WithdrawContext withdrawContext;

    public String getHistoryTaskId() { return historyTaskId; }
    public void setHistoryTaskId(String historyTaskId) { this.historyTaskId = historyTaskId; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getActiveTaskId() { return activeTaskId; }
    public void setActiveTaskId(String activeTaskId) { this.activeTaskId = activeTaskId; }
    public String getProcessCode() { return processCode; }
    public void setProcessCode(String processCode) { this.processCode = processCode; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public String getInstanceTitle() { return instanceTitle; }
    public void setInstanceTitle(String instanceTitle) { this.instanceTitle = instanceTitle; }
    public String getNodeCode() { return nodeCode; }
    public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(String assigneeUserId) { this.assigneeUserId = assigneeUserId; }
    public String getAssigneeUserName() { return assigneeUserName; }
    public void setAssigneeUserName(String assigneeUserName) { this.assigneeUserName = assigneeUserName; }
    public String getDelegateFromUserId() { return delegateFromUserId; }
    public void setDelegateFromUserId(String delegateFromUserId) { this.delegateFromUserId = delegateFromUserId; }
    public String getDelegateFromUserName() { return delegateFromUserName; }
    public void setDelegateFromUserName(String delegateFromUserName) { this.delegateFromUserName = delegateFromUserName; }
    public String getHandleType() { return handleType; }
    public void setHandleType(String handleType) { this.handleType = handleType; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public WithdrawContext getWithdrawContext() { return withdrawContext; }
    public void setWithdrawContext(WithdrawContext withdrawContext) { this.withdrawContext = withdrawContext; }

    /**
     * 当前已办记录可撤回时所需的可信活动任务快照。
     */
    public static class WithdrawContext {
        private String taskId;
        private Long expectedTaskVersion;
        private String targetNodeCode;
        private String targetNodeName;

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public Long getExpectedTaskVersion() { return expectedTaskVersion; }
        public void setExpectedTaskVersion(Long expectedTaskVersion) { this.expectedTaskVersion = expectedTaskVersion; }
        public String getTargetNodeCode() { return targetNodeCode; }
        public void setTargetNodeCode(String targetNodeCode) { this.targetNodeCode = targetNodeCode; }
        public String getTargetNodeName() { return targetNodeName; }
        public void setTargetNodeName(String targetNodeName) { this.targetNodeName = targetNodeName; }
    }
}
