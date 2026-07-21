package com.flowmind.platform.api.dto;

/**
 * 已阅记录查询条件。
 */
public class ReadRecordQuery extends PageQuery {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 阅读用户 ID。 */
    private String userId;
    /** 当前页码，从 1 开始。 */
    /** 每页条数。 */

    public ReadRecordQuery() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

}
