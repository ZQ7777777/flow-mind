package com.flowmind.platform.api.entity.query;

/**
 * 审计日志查询条件。
 */
public class AuditLogQuery {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 操作人用户 ID。 */
    private String operatorUserId;
    /** 当前页码，从 1 开始。 */
    private Integer pageNo;
    /** 每页条数。 */
    private Integer pageSize;

    public AuditLogQuery() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
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
