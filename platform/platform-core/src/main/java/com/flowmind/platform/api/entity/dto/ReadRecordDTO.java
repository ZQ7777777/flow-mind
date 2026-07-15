package com.flowmind.platform.api.entity.dto;

import java.time.LocalDateTime;

/**
 * 流程已阅记录。
 */
public class ReadRecordDTO {

    /** 已阅记录 ID。 */
    private String readRecordId;
    /** 流程实例 ID。 */
    private String instanceId;
    /** 任务 ID。 */
    private String taskId;
    /** 阅读用户 ID。 */
    private String userId;
    /** 阅读用户名称快照。 */
    private String userName;
    /** 阅读时间。 */
    private LocalDateTime readAt;

    public ReadRecordDTO() {
    }

    public String getReadRecordId() {
        return readRecordId;
    }

    public void setReadRecordId(String readRecordId) {
        this.readRecordId = readRecordId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }
}
