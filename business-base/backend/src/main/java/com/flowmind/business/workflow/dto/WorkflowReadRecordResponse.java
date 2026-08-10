package com.flowmind.business.workflow.dto;

import java.time.LocalDateTime;

public class WorkflowReadRecordResponse {
    private String readRecordId;
    private String instanceId;
    private String taskId;
    private String processCode;
    private String processName;
    private String instanceTitle;
    private String instanceStatus;
    private LocalDateTime readAt;

    public String getReadRecordId() { return readRecordId; }
    public void setReadRecordId(String readRecordId) { this.readRecordId = readRecordId; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getProcessCode() { return processCode; }
    public void setProcessCode(String processCode) { this.processCode = processCode; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public String getInstanceTitle() { return instanceTitle; }
    public void setInstanceTitle(String instanceTitle) { this.instanceTitle = instanceTitle; }
    public String getInstanceStatus() { return instanceStatus; }
    public void setInstanceStatus(String instanceStatus) { this.instanceStatus = instanceStatus; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}
