package com.flowmind.platform.api.request;

/**
 * Base request for operations targeting an active workflow task.
 */
public class TaskOperationRequest extends OperationRequest {

    private String taskId;
    private Long expectedTaskVersion;
    private String operatorUserId;
    private String comment;

    public TaskOperationRequest() {
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Long getExpectedTaskVersion() {
        return expectedTaskVersion;
    }

    public void setExpectedTaskVersion(Long expectedTaskVersion) {
        this.expectedTaskVersion = expectedTaskVersion;
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
