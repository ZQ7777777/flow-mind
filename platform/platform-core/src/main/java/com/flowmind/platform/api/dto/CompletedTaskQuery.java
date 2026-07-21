package com.flowmind.platform.api.dto;

/**
 * 已办任务查询条件。
 */
public class CompletedTaskQuery extends PageQuery {

    /** 办理人用户 ID。 */
    private String userId;
    /** 流程编码。 */
    private String processCode;
    /** 当前页码，从 1 开始。 */
    /** 每页条数。 */

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

}
