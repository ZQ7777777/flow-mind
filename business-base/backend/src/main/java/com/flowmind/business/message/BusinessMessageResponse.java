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
        Map<String, Object> payload = RuntimeJsonCodec.readObjectMap(entity.getPayloadJson());
        response.setMessageId(entity.getId());
        response.setSourceMessageId(entity.getSourceMessageId());
        response.setMessageType(entity.getMessageType());
        response.setTitle(entity.getTitle());
        response.setContent(normalizedContent(entity.getMessageType(), entity.getContent(), payload));
        response.setSeverity(entity.getSeverity());
        response.setPayload(payload);
        response.setReadStatus(entity.getReadStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setReadAt(entity.getReadAt());
        return response;
    }

    private static String normalizedContent(String messageType, String content, Map<String, Object> payload) {
        if ("TASK_REMIND".equals(messageType)) {
            return "【催办提醒】流程“" + payloadText(payload, "instanceTitle") + "”中的任务“"
                    + payloadText(payload, "taskName", "nodeCode") + "”正在等待您处理，请及时办理。";
        }
        if ("TASK_DUE_SOON".equals(messageType)) {
            return "【即将超时提醒】流程“" + payloadText(payload, "instanceTitle") + "”中的任务“"
                    + payloadText(payload, "taskName", "nodeCode") + "”即将超过处理时限，请尽快办理。";
        }
        if ("TASK_TIMEOUT".equals(messageType)) {
            String prefix = "【超时提醒】流程“" + payloadText(payload, "instanceTitle") + "”中的任务“"
                    + payloadText(payload, "taskName", "nodeCode") + "”已超过处理时限，";
            String action = payloadText(payload, "timeoutAction");
            if ("REMIND".equals(action)) {
                return prefix + "请尽快办理。";
            }
            if ("TERMINATE".equals(action)) {
                return prefix + "系统将按配置终止流程。";
            }
            if ("FORCE_COMPLETE".equals(action)) {
                return prefix + "系统将按配置自动完成当前任务。";
            }
            return prefix + "请关注处理。";
        }
        return content;
    }

    private static String payloadText(Map<String, Object> payload, String key) {
        return payloadText(payload, key, null);
    }

    private static String payloadText(Map<String, Object> payload, String key, String fallbackKey) {
        if (payload != null) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return String.valueOf(value);
            }
            if (fallbackKey != null) {
                Object fallback = payload.get(fallbackKey);
                if (fallback != null && !String.valueOf(fallback).trim().isEmpty()) {
                    return String.valueOf(fallback);
                }
            }
        }
        return "--";
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
