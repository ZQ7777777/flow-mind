package com.flowmind.platform.api.request;

/**
 * 面向活动流程任务的状态修改请求基类。
 */
public class TaskOperationRequest extends OperationRequest {

    /** 要操作的活动任务 ID。 */
    private String taskId;
    /** 读取任务时取得的乐观锁版本，执行时必须原样传入。 */
    private Long expectedTaskVersion;
    /** 实际执行任务动作的用户 ID。 */
    private String operatorUserId;
    /** 办理意见、操作原因或补充说明。 */
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
