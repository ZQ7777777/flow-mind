package com.flowmind.platform.api.dto;

/**
 * 管理端活动任务查询条件。
 */
public class AdminTaskQuery extends PageQuery {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 办理人用户 ID。 */
    private String assigneeUserId;
    /** 当前页码，从 1 开始。 */
    /** 每页条数。 */

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

}
