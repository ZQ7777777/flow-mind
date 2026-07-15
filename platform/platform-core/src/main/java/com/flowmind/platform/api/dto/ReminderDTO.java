package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ReminderStatusEnum;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提醒或催办记录。
 */
public class ReminderDTO {

    /** 提醒记录 ID。 */
    private String reminderId;
    /** 流程实例 ID。 */
    private String instanceId;
    /** 活动任务 ID。 */
    private String taskId;
    /** 提醒类型。 */
    private String reminderType;
    /** 提醒目标用户 ID 列表。 */
    private List<String> targetUserIds;
    /** 提醒内容。 */
    private String message;
    /** 提醒状态。 */
    private ReminderStatusEnum reminderStatus;
    /** 错误信息。 */
    private String errorMessage;
    /** 创建人。 */
    private String createdBy;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 发送时间。 */
    private LocalDateTime sentAt;

    public ReminderDTO() {
    }

    public String getReminderId() {
        return reminderId;
    }

    public void setReminderId(String reminderId) {
        this.reminderId = reminderId;
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

    public String getReminderType() {
        return reminderType;
    }

    public void setReminderType(String reminderType) {
        this.reminderType = reminderType;
    }

    public List<String> getTargetUserIds() {
        return targetUserIds;
    }

    public void setTargetUserIds(List<String> targetUserIds) {
        this.targetUserIds = targetUserIds;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ReminderStatusEnum getReminderStatus() {
        return reminderStatus;
    }

    public void setReminderStatus(ReminderStatusEnum reminderStatus) {
        this.reminderStatus = reminderStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
