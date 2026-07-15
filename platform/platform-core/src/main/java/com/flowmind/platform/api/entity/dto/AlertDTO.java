package com.flowmind.platform.api.entity.dto;

import com.flowmind.platform.api.entity.enums.AlertStatusEnum;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 异常告警记录。
 */
public class AlertDTO {

    /** 告警记录 ID。 */
    private String alertId;
    /** 流程实例 ID。 */
    private String instanceId;
    /** 活动任务 ID。 */
    private String taskId;
    /** 告警类型。 */
    private String alertType;
    /** 告警级别。 */
    private String severity;
    /** 告警状态。 */
    private AlertStatusEnum alertStatus;
    /** 告警详情。 */
    private Map<String, Object> detail;
    /** 处理人。 */
    private String handledBy;
    /** 处理时间。 */
    private LocalDateTime handledAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;

    public AlertDTO() {
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
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

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public AlertStatusEnum getAlertStatus() {
        return alertStatus;
    }

    public void setAlertStatus(AlertStatusEnum alertStatus) {
        this.alertStatus = alertStatus;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public void setDetail(Map<String, Object> detail) {
        this.detail = detail;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(String handledBy) {
        this.handledBy = handledBy;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
