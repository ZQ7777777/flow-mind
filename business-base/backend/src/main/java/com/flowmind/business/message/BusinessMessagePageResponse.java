package com.flowmind.business.message;

import java.util.List;

/** Page response for current user's message inbox. */
public class BusinessMessagePageResponse {
    private List<BusinessMessageResponse> records;
    private Integer pageNo;
    private Integer pageSize;
    private Long unreadCount;

    public List<BusinessMessageResponse> getRecords() { return records; }
    public void setRecords(List<BusinessMessageResponse> records) { this.records = records; }
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public Long getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Long unreadCount) { this.unreadCount = unreadCount; }
}