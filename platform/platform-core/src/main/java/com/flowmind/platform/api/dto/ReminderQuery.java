package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ReminderStatusEnum;
import com.flowmind.platform.api.enums.ReminderTypeEnum;

import java.time.LocalDateTime;

/**
 * 提醒记录查询条件。
 */
public class ReminderQuery extends PageQuery {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 任务 ID。 */
    private String taskId;
    private ReminderTypeEnum reminderType;
    private ReminderStatusEnum reminderStatus;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
    /** 当前页码，从 1 开始。 */
    /** 每页条数。 */

    public ReminderQuery() {
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

    public ReminderTypeEnum getReminderType() {
        return reminderType;
    }

    public void setReminderType(ReminderTypeEnum reminderType) {
        this.reminderType = reminderType;
    }

    public ReminderStatusEnum getReminderStatus() {
        return reminderStatus;
    }

    public void setReminderStatus(ReminderStatusEnum reminderStatus) {
        this.reminderStatus = reminderStatus;
    }

    public LocalDateTime getCreatedFrom() {
        return createdFrom;
    }

    public void setCreatedFrom(LocalDateTime createdFrom) {
        this.createdFrom = createdFrom;
    }

    public LocalDateTime getCreatedTo() {
        return createdTo;
    }

    public void setCreatedTo(LocalDateTime createdTo) {
        this.createdTo = createdTo;
    }

}
