package com.flowmind.platform.api.entity.query;

/**
 * 我发起的流程实例查询条件。
 */
public class StartedInstanceQuery {

    /** 发起人用户 ID。 */
    private String starterUserId;
    /** 流程编码。 */
    private String processCode;
    /** 当前页码，从 1 开始。 */
    private Integer pageNo;
    /** 每页条数。 */
    private Integer pageSize;

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
