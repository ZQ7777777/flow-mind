package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.DefinitionActionTypeEnum;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计日志。
 */
public class AuditLogDTO {

    /** 审计日志 ID。 */
    private String auditLogId;
    /** 操作幂等号。 */
    private String operationId;
    /** 流程实例 ID。 */
    private String instanceId;
    /** 任务 ID。 */
    private String taskId;
    /** 动作类型。运行时动作直接存枚举名，定义管理动作使用 DEFINITION_* 命名空间。 */
    private String actionType;
    /** 操作人用户 ID。 */
    private String operatorUserId;
    /** 操作人名称快照。 */
    private String operatorUserName;
    /** 操作详情。 */
    private Map<String, Object> detail;
    /** 创建时间。 */
    private LocalDateTime createdAt;

    public AuditLogDTO() {
    }

    public String getAuditLogId() {
        return auditLogId;
    }

    public void setAuditLogId(String auditLogId) {
        this.auditLogId = auditLogId;
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

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public String getOperatorUserName() {
        return operatorUserName;
    }

    public void setOperatorUserName(String operatorUserName) {
        this.operatorUserName = operatorUserName;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public void setDetail(Map<String, Object> detail) {
        this.detail = detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
