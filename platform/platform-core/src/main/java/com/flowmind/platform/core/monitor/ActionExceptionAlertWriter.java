package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.enums.AlertSeverityEnum;
import com.flowmind.platform.api.enums.AlertStatusEnum;
import com.flowmind.platform.api.enums.AlertTypeEnum;
import com.flowmind.platform.core.runtime.RuntimeJsonCodec;
import com.flowmind.platform.persistence.entity.ProcessAlertRecordEntity;
import com.flowmind.platform.persistence.repository.AlertRecordRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Writes deduplicated ACTION_EXCEPTION alerts for diagnosable platform failures. */
@Component
public class ActionExceptionAlertWriter {

    private final AlertRecordRepository alertRepository;

    public ActionExceptionAlertWriter(AlertRecordRepository alertRepository) {
        this.alertRepository = alertRepository;
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
