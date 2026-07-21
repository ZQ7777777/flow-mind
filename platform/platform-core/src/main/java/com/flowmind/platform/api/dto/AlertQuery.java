package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.AlertStatusEnum;

/**
 * 告警查询条件。
 */
public class AlertQuery extends PageQuery {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 告警状态。 */
    private AlertStatusEnum alertStatus;
    /** 当前页码，从 1 开始。 */
    /** 每页条数。 */

    public AlertQuery() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public AlertStatusEnum getAlertStatus() {
        return alertStatus;
    }

    public void setAlertStatus(AlertStatusEnum alertStatus) {
        this.alertStatus = alertStatus;
    }

}
