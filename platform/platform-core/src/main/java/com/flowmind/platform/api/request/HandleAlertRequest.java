package com.flowmind.platform.api.request;

import com.flowmind.platform.api.enums.AlertStatusEnum;

/**
 * 处理告警请求。
 */
public class HandleAlertRequest extends OperationRequest {

    /** 告警记录 ID。 */
    private String alertId;
    /** 处理人用户 ID。 */
    private String operatorUserId;
    /** 目标告警状态，通常为 HANDLED 或 IGNORED。 */
    private AlertStatusEnum targetStatus;
    /** 处理说明。 */
    private String comment;

    public HandleAlertRequest() {
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public AlertStatusEnum getTargetStatus() {
        return targetStatus;
    }

    public void setTargetStatus(AlertStatusEnum targetStatus) {
        this.targetStatus = targetStatus;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
