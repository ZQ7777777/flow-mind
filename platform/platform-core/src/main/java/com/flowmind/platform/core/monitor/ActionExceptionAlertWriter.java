package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.enums.AlertSeverityEnum;
import com.flowmind.platform.api.enums.AlertStatusEnum;
import com.flowmind.platform.api.enums.AlertTypeEnum;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.core.runtime.RuntimeJsonCodec;
import com.flowmind.platform.persistence.entity.ProcessAlertRecordEntity;
import com.flowmind.platform.persistence.repository.AlertRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Writes deduplicated ACTION_EXCEPTION alerts for diagnosable platform failures. */
@Component
public class ActionExceptionAlertWriter {

    private final AlertRecordRepository alertRepository;
    private final MessagePublisher messagePublisher;
    private final List<String> alertTargetUserIds;

    @Autowired
    public ActionExceptionAlertWriter(AlertRecordRepository alertRepository) {
        this(alertRepository, null, Collections.<String>emptyList());
    }

    public ActionExceptionAlertWriter(AlertRecordRepository alertRepository,
                                      MessagePublisher messagePublisher,
                                      List<String> alertTargetUserIds) {
        this.alertRepository = alertRepository;
        this.messagePublisher = messagePublisher;
        this.alertTargetUserIds = alertTargetUserIds == null
                ? Collections.<String>emptyList()
                : new ArrayList<String>(alertTargetUserIds);
    }

    public void write(String operationId,
                      String actionType,
                      String instanceId,
                      String taskId,
                      String errorCode,
                      String errorSummary,
                      String operatorId) {
        if (isBlank(operationId) || isBlank(actionType)
                || alertRepository.findOpenByTypeAndDetailValue(AlertTypeEnum.ACTION_EXCEPTION.name(),
                "dedupKey", dedupKey(operationId, actionType)) != null) {
            return;
        }
        ProcessAlertRecordEntity alert = new ProcessAlertRecordEntity();
        alert.setId(UUID.randomUUID().toString());
        alert.setInstanceId(instanceId);
        alert.setTaskId(taskId);
        alert.setAlertType(AlertTypeEnum.ACTION_EXCEPTION.name());
        alert.setSeverity(AlertSeverityEnum.HIGH.name());
        alert.setAlertStatus(AlertStatusEnum.OPEN.name());
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("schemaVersion", Integer.valueOf(1));
        detail.put("dedupKey", dedupKey(operationId, actionType));
        detail.put("operationId", operationId);
        detail.put("actionType", actionType);
        detail.put("instanceId", instanceId);
        detail.put("taskId", taskId);
        detail.put("errorCode", errorCode);
        detail.put("errorSummary", abbreviate(errorSummary));
        detail.put("operatorId", operatorId);
        alert.setDetailJson(RuntimeJsonCodec.toJson(detail));
        alert.setCreatedAt(LocalDateTime.now());
        alertRepository.insert(alert);
        publishAlert(alert);
    }

    private void publishAlert(ProcessAlertRecordEntity alert) {
        if (messagePublisher == null) {
            return;
        }
        ProcessMessage message = new ProcessMessage();
        message.setMessageId(alert.getId());
        message.setMessageType("ALERT");
        message.setTitle("流程异常告警");
        message.setContent("流程自动处理发生异常，请及时处理");
        message.setTargetUserIds(new ArrayList<String>(alertTargetUserIds));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("alertId", alert.getId());
        payload.put("alertType", alert.getAlertType());
        payload.put("instanceId", alert.getInstanceId());
        payload.put("taskId", alert.getTaskId());
        payload.put("severity", alert.getSeverity());
        message.setPayload(payload);
        message.setCreatedAt(LocalDateTime.now());
        messagePublisher.publish(message);
    }

    private String dedupKey(String operationId, String actionType) {
        return operationId + ":" + actionType;
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 300) {
            return value;
        }
        return value.substring(0, 300);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}