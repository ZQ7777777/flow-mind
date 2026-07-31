package com.flowmind.platform.api.dto;

import java.time.LocalDateTime;

/**
 * 待办任务查询条件。
 */
public class TodoTaskQuery extends PageQuery {

    /** 当前用户 ID。 */
    private String userId;
    /** 流程编码。 */
    private String processCode;
    /** 流程名称关键字。 */
    private String processName;
    /** 流程实例标题关键字。 */
    private String instanceTitle;
    /** 发起人用户 ID。 */
    private String starterUserId;
    /** 当前节点编码。 */
    private String nodeCode;
    /** 活动任务状态，典型值：ACTIVE、CLAIMED。 */
    private String taskStatus;
    /** Todo source, typical values: ALL, OWN, DELEGATED. */
    private String todoSource;
    /** 任务创建时间起始边界，包含该时间。 */
    private LocalDateTime createdFrom;
    /** 任务创建时间结束边界，包含该时间。 */
    private LocalDateTime createdTo;
    /** 排序字段，典型值：createdAt、dueAt。 */
    private String sortBy;
    /** 排序方向，典型值：ASC、DESC。 */
    private String sortDirection;

    public TodoTaskQuery() {
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

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public String getTodoSource() {
        return todoSource;
    }

    public void setTodoSource(String todoSource) {
        this.todoSource = todoSource;
    }

    public LocalDateTime getCreatedFrom() {
        return createdFrom;
    }

    public void setCreatedFrom(LocalDateTime createdFrom) {
        this.createdFrom = createdFrom;
    }

    public LocalDateTime getCreatedTo() {
        return createdTo;
    }

    public void setCreatedTo(LocalDateTime createdTo) {
        this.createdTo = createdTo;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

}
