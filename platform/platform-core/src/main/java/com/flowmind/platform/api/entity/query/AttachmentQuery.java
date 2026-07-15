package com.flowmind.platform.api.entity.query;

import com.flowmind.platform.api.entity.enums.AttachmentOwnerTypeEnum;

/**
 * 附件查询条件。
 */
public class AttachmentQuery {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 任务 ID。 */
    private String taskId;
    /** 附件归属类型。 */
    private AttachmentOwnerTypeEnum ownerType;
    /** 查询人用户 ID。 */
    private String operatorUserId;

    public AttachmentQuery() {
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

    public AttachmentOwnerTypeEnum getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(AttachmentOwnerTypeEnum ownerType) {
        this.ownerType = ownerType;
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
