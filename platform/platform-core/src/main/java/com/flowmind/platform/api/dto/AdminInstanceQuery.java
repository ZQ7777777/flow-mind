package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.InstanceStatusEnum;

/**
 * 管理端流程实例查询条件。
 */
public class AdminInstanceQuery extends PageQuery {

    /** 流程编码。 */
    private String processCode;
    /** 实例状态。 */
    private InstanceStatusEnum instanceStatus;
    /** 当前页码，从 1 开始。 */
    /** 每页条数。 */

    public AdminInstanceQuery() {
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public InstanceStatusEnum getInstanceStatus() {
        return instanceStatus;
    }

    public void setInstanceStatus(InstanceStatusEnum instanceStatus) {
        this.instanceStatus = instanceStatus;
    }

}
