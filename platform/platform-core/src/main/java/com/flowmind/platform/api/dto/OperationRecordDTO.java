package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.DefinitionActionTypeEnum;
import com.flowmind.platform.api.enums.OperationStatusEnum;

import java.time.LocalDateTime;

public class OperationRecordDTO {

    /** 操作记录 ID。 */
    private String operationRecordId;
    /** 调用方生成的全局唯一操作幂等号。 */
    private String operationId;
    /** 关联流程实例 ID，可空。 */
    private String instanceId;
    /** 关联活动任务 ID，可空。 */
    private String taskId;
    /** 操作动作类型。运行时动作直接存枚举名，定义管理动作使用 DEFINITION_* 命名空间。 */
    private String actionType;
    /** 操作人 ID。 */
    private String operatorId;
    /** 规范化请求内容的哈希。 */
    private String requestHash;
    /** 操作执行状态。 */
    private OperationStatusEnum operationStatus;
    /** 成功结果快照 JSON，重放时直接返回。 */
    private String resultJson;
    /** 已落库失败错误码。 */
    private String errorCode;
    /** PROCESSING 租约截止时间。 */
    private LocalDateTime processingExpiresAt;
    /** 幂等记录保留截止时间。 */
    private LocalDateTime expiresAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;

    public OperationRecordDTO() {
    }

    public String getOperationRecordId() {
        return operationRecordId;
    }

    public void setOperationRecordId(String operationRecordId) {
        this.operationRecordId = operationRecordId;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
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

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public void setActionType(ActionTypeEnum actionType) {
        this.actionType = actionType == null ? null : actionType.name();
    }

    public void setActionType(DefinitionActionTypeEnum actionType) {
        this.actionType = actionType == null ? null : actionType.getOperationActionType();
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public OperationStatusEnum getOperationStatus() {
        return operationStatus;
    }

    public void setOperationStatus(OperationStatusEnum operationStatus) {
        this.operationStatus = operationStatus;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public LocalDateTime getProcessingExpiresAt() {
        return processingExpiresAt;
    }

    public void setProcessingExpiresAt(LocalDateTime processingExpiresAt) {
        this.processingExpiresAt = processingExpiresAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
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
}
