package com.flowmind.platform.api.entity.query;

/**
 * 管理端活动任务查询条件。
 */
public class AdminTaskQuery {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 办理人用户 ID。 */
    private String assigneeUserId;
    /** 当前页码，从 1 开始。 */
    private Integer pageNo;
    /** 每页条数。 */
    private Integer pageSize;

    public AdminTaskQuery() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getAssigneeUserId() {
        return assigneeUserId;
    }

    public void setAssigneeUserId(String assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
