package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.OperationTargetTypeEnum;

/**
 * 审计日志查询条件。
 */
public class AuditLogQuery extends PageQuery {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 被审计对象类型。 */
    private OperationTargetTypeEnum targetType;
    /** 被审计对象 ID。 */
    private String targetId;
    /** 操作人用户 ID。 */
    private String operatorUserId;
    /** 当前页码，从 1 开始。 */
    /** 每页条数。 */

    public AuditLogQuery() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
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

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

}
