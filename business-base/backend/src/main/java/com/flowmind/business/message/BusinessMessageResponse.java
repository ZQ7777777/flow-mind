package com.flowmind.business.message;

import com.flowmind.platform.core.runtime.RuntimeJsonCodec;

import java.time.LocalDateTime;
import java.util.Map;

/** Current user's message item. */
public class BusinessMessageResponse {
    private String messageId;
    private String sourceMessageId;
    private String messageType;
    private String title;
    private String content;
    private String severity;
    private Map<String, Object> payload;
    private String readStatus;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    public static BusinessMessageResponse from(BusinessUserMessageEntity entity) {
        BusinessMessageResponse response = new BusinessMessageResponse();
        response.setMessageId(entity.getId());
        response.setSourceMessageId(entity.getSourceMessageId());
        response.setMessageType(entity.getMessageType());
        response.setTitle(entity.getTitle());
        response.setContent(entity.getContent());
        response.setSeverity(entity.getSeverity());
        response.setPayload(RuntimeJsonCodec.readObjectMap(entity.getPayloadJson()));
        response.setReadStatus(entity.getReadStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setReadAt(entity.getReadAt());
        return response;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getSourceMessageId() { return sourceMessageId; }
    public void setSourceMessageId(String sourceMessageId) { this.sourceMessageId = sourceMessageId; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public String getReadStatus() { return readStatus; }
    public void setReadStatus(String readStatus) { this.readStatus = readStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}