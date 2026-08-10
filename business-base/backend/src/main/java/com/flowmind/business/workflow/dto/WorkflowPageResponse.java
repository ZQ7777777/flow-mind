package com.flowmind.business.workflow.dto;

import java.util.ArrayList;
import java.util.List;

public class WorkflowPageResponse<T> {
    private List<T> records = new ArrayList<T>();
    private Integer pageNo;
    private Integer pageSize;
    private Long total;
    private Integer totalPages;

    public List<T> getRecords() { return records; }
    public void setRecords(List<T> records) { this.records = records == null ? new ArrayList<T>() : records; }
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
}
