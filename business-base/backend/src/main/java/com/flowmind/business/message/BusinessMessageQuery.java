package com.flowmind.business.message;

/** Query parameters for current user's message inbox. */
public class BusinessMessageQuery {
    private Integer pageNo = Integer.valueOf(1);
    private Integer pageSize = Integer.valueOf(20);
    private String readStatus;
    private String messageType;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getReadStatus() { return readStatus; }
    public void setReadStatus(String readStatus) { this.readStatus = readStatus; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
}