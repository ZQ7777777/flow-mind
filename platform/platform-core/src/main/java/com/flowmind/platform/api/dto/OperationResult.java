package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.OperationTargetTypeEnum;

public class OperationResult {

    /** 本次操作的幂等号。 */
    private String operationId;
    /** 被操作对象的类型，如 DEFINITION、INSTANCE、TASK、ATTACHMENT。 */
    private OperationTargetTypeEnum targetType;
    /** 被操作对象的 ID。 */
    private String targetId;
    /** 被操作对象是否已删除。 */
    private boolean deleted;
    /** 是否由相同幂等号的重复请求返回首次执行结果。 */
    private boolean replayed;

    public OperationResult() {
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

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public void setReplayed(boolean replayed) {
        this.replayed = replayed;
    }
}
