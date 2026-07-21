package com.flowmind.platform.api.dto;

/**
 * 我发起的流程实例查询条件。
 */
public class StartedInstanceQuery extends PageQuery {

    /** 发起人用户 ID。 */
    private String starterUserId;
    /** 流程编码。 */
    private String processCode;
    /** 当前页码，从 1 开始。 */
    /** 每页条数。 */

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

}
