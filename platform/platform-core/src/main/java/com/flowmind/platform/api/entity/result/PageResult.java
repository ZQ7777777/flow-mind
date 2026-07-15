package com.flowmind.platform.api.entity.result;

import java.util.List;

/**
 * 分页查询结果。
 */
public class PageResult<T> {

    /** 当前页数据。 */
    private List<T> records;
    /** 总记录数。 */
    private Long total;
    /** 当前页码，从 1 开始。 */
    private Integer pageNo;
    /** 每页条数。 */
    private Integer pageSize;

    public PageResult() {
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
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
