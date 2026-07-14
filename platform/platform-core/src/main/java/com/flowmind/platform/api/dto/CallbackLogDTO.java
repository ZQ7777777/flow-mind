package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.CallbackStatus;

import java.time.LocalDateTime;

/**
 * 回调日志。
 */
public class CallbackLogDTO {

    /** 回调日志 ID。 */
    private String callbackLogId;
    /** 事件 ID。 */
    private String eventId;
    /** 流程实例 ID。 */
    private String instanceId;
    /** 事件类型。 */
    private String eventType;
    /** 回调状态。 */
    private CallbackStatus callbackStatus;
    /** 错误信息。 */
    private String errorMessage;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 完成时间。 */
    private LocalDateTime completedAt;

    public CallbackLogDTO() {
    }

    public String getCallbackLogId() {
        return callbackLogId;
    }

    public void setCallbackLogId(String callbackLogId) {
        this.callbackLogId = callbackLogId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public CallbackStatus getCallbackStatus() {
        return callbackStatus;
    }

    public void setCallbackStatus(CallbackStatus callbackStatus) {
        this.callbackStatus = callbackStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
