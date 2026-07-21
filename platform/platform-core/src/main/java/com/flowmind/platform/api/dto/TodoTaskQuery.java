package com.flowmind.platform.api.dto;

/**
 * 待办任务查询条件。
 */
public class TodoTaskQuery extends PageQuery {

    /** 当前用户 ID。 */
    private String userId;
    /** 流程编码。 */
    private String processCode;
    /** 当前页码，从 1 开始。 */
    /** 每页条数。 */

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

}
