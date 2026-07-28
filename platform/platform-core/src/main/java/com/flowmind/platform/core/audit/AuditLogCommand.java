package com.flowmind.platform.core.audit;

import com.flowmind.platform.api.enums.OperationTargetTypeEnum;

import java.util.Map;

/** Internal command for writing process audit logs. */
public class AuditLogCommand {

    private String instanceId;
    private String operationId;
    private OperationTargetTypeEnum targetType;
    private String targetId;
    private String actionType;
    private String operatorId;
    private Map<String, Object> detail;

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

    public OperationTargetTypeEnum getTargetType() {
        return targetType;
    }

    public void setTargetType(OperationTargetTypeEnum targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public void setDetail(Map<String, Object> detail) {
        this.detail = detail;
    }
}
