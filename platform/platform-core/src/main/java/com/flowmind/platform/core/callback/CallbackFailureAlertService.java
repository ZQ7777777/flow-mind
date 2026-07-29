package com.flowmind.platform.core.callback;

import com.flowmind.platform.api.enums.AlertSeverityEnum;
import com.flowmind.platform.api.enums.AlertStatusEnum;
import com.flowmind.platform.api.enums.AlertTypeEnum;
import com.flowmind.platform.core.runtime.RuntimeJsonCodec;
import com.flowmind.platform.persistence.entity.ProcessAlertRecordEntity;
import com.flowmind.platform.persistence.entity.ProcessCallbackLogEntity;
import com.flowmind.platform.persistence.repository.AlertRecordRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Writes deduplicated alerts for failed callback dispatches. */
@Component
public class CallbackFailureAlertService {

    private final AlertRecordRepository alertRepository;

    public CallbackFailureAlertService(AlertRecordRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public void alert(ProcessCallbackLogEntity callbackLog, String errorSummary) {
        if (callbackLog == null || alertRepository.findOpenByTypeAndDetailValue(
                AlertTypeEnum.CALLBACK_FAILED.name(), "eventId", callbackLog.getEventId()) != null) {
            return;
        }
        ProcessAlertRecordEntity alert = new ProcessAlertRecordEntity();
        alert.setId(UUID.randomUUID().toString());
        alert.setInstanceId(callbackLog.getInstanceId());
        alert.setAlertType(AlertTypeEnum.CALLBACK_FAILED.name());
        alert.setSeverity(AlertSeverityEnum.HIGH.name());
        alert.setAlertStatus(AlertStatusEnum.OPEN.name());
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("schemaVersion", Integer.valueOf(1));
        detail.put("eventId", callbackLog.getEventId());
        detail.put("operationId", callbackLog.getOperationId());
        detail.put("eventType", callbackLog.getEventType());
        detail.put("actionType", callbackLog.getActionType());
        detail.put("retryCount", callbackLog.getRetryCount());
        detail.put("errorSummary", abbreviate(errorSummary));
        alert.setDetailJson(RuntimeJsonCodec.toJson(detail));
        alert.setCreatedAt(LocalDateTime.now());
        alertRepository.insert(alert);
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 300) {
            return value;
        }
        return value.substring(0, 300);
    }
}
