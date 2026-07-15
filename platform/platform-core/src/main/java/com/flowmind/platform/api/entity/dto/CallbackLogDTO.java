package com.flowmind.platform.api.entity.dto;

import com.flowmind.platform.api.entity.enums.ActionTypeEnum;
import com.flowmind.platform.api.entity.enums.CallbackStatusEnum;
import com.flowmind.platform.api.entity.enums.WorkflowEventTypeEnum;

import java.time.LocalDateTime;

public class CallbackLogDTO {

    /** 回调日志 ID。 */
    private String callbackLogId;
    /** 回调事件 ID。 */
    private String eventId;
    /** 流程实例 ID。 */
    private String instanceId;
    /** 产生事件的操作幂等号。 */
    private String operationId;
    /** 回调事件类型。 */
    private WorkflowEventTypeEnum eventType;
    /** 产生事件的动作类型。 */
    private ActionTypeEnum actionType;
    /** 序列化后的回调事件载荷。 */
    private String payloadJson;
    /** 回调投递状态。 */
    private CallbackStatusEnum callbackStatus;
    /** 投递重试次数。 */
    private Integer retryCount;
    /** 兼容旧字段的错误信息。 */
    private String errorMessage;
    /** 最近一次投递错误。 */
    private String lastError;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最近更新时间。 */
    private LocalDateTime updatedAt;
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

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public WorkflowEventTypeEnum getEventType() {
        return eventType;
    }

    public void setEventType(WorkflowEventTypeEnum eventType) {
        this.eventType = eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = WorkflowEventTypeEnum.fromCode(eventType);
    }

    public String getEventTypeCode() {
        return eventType == null ? null : eventType.name();
    }

    public ActionTypeEnum getActionType() {
        return actionType;
    }

    public void setActionType(ActionTypeEnum actionType) {
        this.actionType = actionType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public CallbackStatusEnum getCallbackStatus() {
        return callbackStatus;
    }

    public void setCallbackStatus(CallbackStatusEnum callbackStatus) {
        this.callbackStatus = callbackStatus;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.lastError = errorMessage;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
        this.errorMessage = lastError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
