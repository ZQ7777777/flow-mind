package com.flowmind.business.message;

import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.core.runtime.RuntimeJsonCodec;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persists platform messages to each user's Business Base inbox. */
@Component
public class BusinessMessagePublisher implements MessagePublisher {

    private final BusinessUserMessageRepository repository;
    private final BusinessAdministratorProvider administratorProvider;
    private final UserMessageSseHub sseHub;

    public BusinessMessagePublisher(BusinessUserMessageRepository repository,
                                    BusinessAdministratorProvider administratorProvider,
                                    UserMessageSseHub sseHub) {
        this.repository = repository;
        this.administratorProvider = administratorProvider;
        this.sseHub = sseHub;
    }

    @Override
    public void publish(ProcessMessage message) {
        if (message == null || isBlank(message.getMessageId()) || isBlank(message.getMessageType())) {
            throw new IllegalArgumentException("messageId and messageType are required");
        }
        for (String recipientUserId : recipients(message)) {
            BusinessUserMessageEntity entity = entity(message, recipientUserId);
            if (repository.insertIgnore(entity) == 1 && sseHub != null) {
                sseHub.publish(recipientUserId, entity);
            }
        }
    }

    private List<String> recipients(ProcessMessage message) {
        Set<String> recipients = new LinkedHashSet<String>();
        if ("ALERT".equals(message.getMessageType()) && administratorProvider != null) {
            recipients.addAll(administratorProvider.listAdministratorUserIds());
        }
        if (message.getTargetUserIds() != null) {
            recipients.addAll(message.getTargetUserIds());
        }
        List<String> result = new ArrayList<String>();
        for (String recipient : recipients) {
            if (!isBlank(recipient)) {
                result.add(recipient.trim());
            }
        }
        return result;
    }

    private BusinessUserMessageEntity entity(ProcessMessage message, String recipientUserId) {
        BusinessUserMessageEntity entity = new BusinessUserMessageEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setSourceMessageId(message.getMessageId());
        entity.setRecipientUserId(recipientUserId);
        entity.setMessageType(message.getMessageType());
        entity.setTitle(defaultText(message.getTitle(), message.getMessageType()));
        entity.setContent(defaultText(message.getContent(), ""));
        entity.setSeverity(severity(message.getMessageType(), message.getPayload()));
        entity.setPayloadJson(RuntimeJsonCodec.toJson(message.getPayload()));
        entity.setReadStatus("UNREAD");
        entity.setCreatedAt(message.getCreatedAt() == null ? LocalDateTime.now() : message.getCreatedAt());
        return entity;
    }

    private String severity(String messageType, Map<String, Object> payload) {
        if (payload != null && payload.get("severity") != null) {
            return String.valueOf(payload.get("severity"));
        }
        if ("ALERT".equals(messageType) || "TASK_TIMEOUT".equals(messageType)) {
            return "HIGH";
        }
        return "NORMAL";
    }

    private String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}