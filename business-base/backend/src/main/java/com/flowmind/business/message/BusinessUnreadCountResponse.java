package com.flowmind.business.message;

/** Current user's unread message count. */
public class BusinessUnreadCountResponse {
    private Long unreadCount;

    public BusinessUnreadCountResponse(Long unreadCount) {
        this.unreadCount = unreadCount;
    }

    public Long getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Long unreadCount) { this.unreadCount = unreadCount; }
}