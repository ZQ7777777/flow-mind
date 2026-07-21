package com.flowmind.platform.api.dto;

/**
 * 提醒记录查询条件。
 */
public class ReminderQuery extends PageQuery {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 任务 ID。 */
    private String taskId;
    /** 当前页码，从 1 开始。 */
    /** 每页条数。 */

    public ReminderQuery() {
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

}
