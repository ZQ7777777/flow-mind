package com.flowmind.platform.api.dto;

import java.time.LocalDateTime;

/**
 * 已办任务查询条件。
 */
public class CompletedTaskQuery extends PageQuery {

    /** 办理人用户 ID。 */
    private String userId;
    /** 流程编码。 */
    private String processCode;
    /** 流程名称关键字。 */
    private String processName;
    /** 流程实例标题关键字。 */
    private String instanceTitle;
    /** 发起人用户 ID。 */
    private String starterUserId;
    /** 历史任务节点编码。 */
    private String nodeCode;
    /** 动作类型，典型值：SEND、APPROVE、REJECT。 */
    private String actionType;
    /** 完成时间起始边界，包含该时间。 */
    private LocalDateTime completedFrom;
    /** 完成时间结束边界，包含该时间。 */
    private LocalDateTime completedTo;

    public CompletedTaskQuery() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getInstanceTitle() {
        return instanceTitle;
    }

    public void setInstanceTitle(String instanceTitle) {
        this.instanceTitle = instanceTitle;
    }

    public String getStarterUserId() {
        return starterUserId;
    }

    public void setStarterUserId(String starterUserId) {
        this.starterUserId = starterUserId;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public LocalDateTime getCompletedFrom() {
        return completedFrom;
    }

    public void setCompletedFrom(LocalDateTime completedFrom) {
        this.completedFrom = completedFrom;
    }

    public LocalDateTime getCompletedTo() {
        return completedTo;
    }

    public void setCompletedTo(LocalDateTime completedTo) {
        this.completedTo = completedTo;
    }

}
