package com.flowmind.platform.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ProcessMessage {

    /** 消息 ID。 */
    private String messageId;
    /** 消息类型。 */
    private String messageType;
    /** 消息标题。 */
    private String title;
    /** 消息正文。 */
    private String content;
    /** 消息接收用户 ID 列表。 */
    private List<String> targetUserIds;
    /** 消息扩展载荷。 */
    private Map<String, Object> payload;
    /** 消息创建时间。 */
    private LocalDateTime createdAt;

    public ProcessMessage() {
    }

    public ProcessMessage(String messageId, String messageType, String title, String content,
            List<String> targetUserIds, Map<String, Object> payload, LocalDateTime createdAt) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.title = title;
        this.content = content;
        this.targetUserIds = targetUserIds;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getTargetUserIds() {
        return targetUserIds;
    }

    public void setTargetUserIds(List<String> targetUserIds) {
        this.targetUserIds = targetUserIds;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
