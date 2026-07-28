package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.AlertSeverityEnum;
import com.flowmind.platform.api.enums.AlertStatusEnum;
import com.flowmind.platform.api.enums.AlertTypeEnum;

import java.time.LocalDateTime;

/**
 * 告警查询条件。
 */
public class AlertQuery extends PageQuery {

    /** 流程实例 ID。 */
    private String instanceId;
    private String taskId;
    private AlertTypeEnum alertType;
    private AlertSeverityEnum severity;
    /** 告警状态。 */
    private AlertStatusEnum alertStatus;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
    /** 当前页码，从 1 开始。 */
    /** 每页条数。 */

    public AlertQuery() {
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

    public AlertTypeEnum getAlertType() {
        return alertType;
    }

    public void setAlertType(AlertTypeEnum alertType) {
        this.alertType = alertType;
    }

    public AlertSeverityEnum getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverityEnum severity) {
        this.severity = severity;
    }

    public AlertStatusEnum getAlertStatus() {
        return alertStatus;
    }

    public void setAlertStatus(AlertStatusEnum alertStatus) {
        this.alertStatus = alertStatus;
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
