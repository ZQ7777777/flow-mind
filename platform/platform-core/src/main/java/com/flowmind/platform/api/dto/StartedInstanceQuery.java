package com.flowmind.platform.api.dto;

import java.time.LocalDateTime;

/**
 * 我发起的流程实例查询条件。
 */
public class StartedInstanceQuery extends PageQuery {

    /** 发起人用户 ID。 */
    private String starterUserId;
    /** 流程编码。 */
    private String processCode;
    /** 流程实例标题关键字。 */
    private String instanceTitle;
    /** 外部业务键。 */
    private String businessKey;
    /** 流程实例状态，典型值：RUNNING、COMPLETED、TERMINATED。 */
    private String instanceStatus;
    /** 当前节点编码。 */
    private String currentNodeCode;
    /** 发起时间起始边界，包含该时间。 */
    private LocalDateTime startedFrom;
    /** 发起时间结束边界，包含该时间。 */
    private LocalDateTime startedTo;

    public StartedInstanceQuery() {
    }

    public String getStarterUserId() {
        return starterUserId;
    }

    public void setStarterUserId(String starterUserId) {
        this.starterUserId = starterUserId;
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public String getInstanceTitle() {
        return instanceTitle;
    }

    public void setInstanceTitle(String instanceTitle) {
        this.instanceTitle = instanceTitle;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getInstanceStatus() {
        return instanceStatus;
    }

    public void setInstanceStatus(String instanceStatus) {
        this.instanceStatus = instanceStatus;
    }

    public String getCurrentNodeCode() {
        return currentNodeCode;
    }

    public void setCurrentNodeCode(String currentNodeCode) {
        this.currentNodeCode = currentNodeCode;
    }

    public LocalDateTime getStartedFrom() {
        return startedFrom;
    }

    public void setStartedFrom(LocalDateTime startedFrom) {
        this.startedFrom = startedFrom;
    }

    public LocalDateTime getStartedTo() {
        return startedTo;
    }

    public void setStartedTo(LocalDateTime startedTo) {
        this.startedTo = startedTo;
    }

}
