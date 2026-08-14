package com.flowmind.business.message;

import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.persistence.entity.ProcessAlertRecordEntity;
import com.flowmind.platform.persistence.repository.AlertRecordRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Publishes a Business Base inbox notification after a new alert is persisted. */
@Repository
public class BusinessAlertRecordRepository extends AlertRecordRepository {

    private static final String ALERT_MESSAGE = "您有一条告警异常急需处理，请关注。";

    private final MessagePublisher messagePublisher;

    public BusinessAlertRecordRepository(JdbcTemplate jdbcTemplate, MessagePublisher messagePublisher) {
        super(jdbcTemplate);
        this.messagePublisher = messagePublisher;
    }

    @Override
    public int insert(ProcessAlertRecordEntity alert) {
        int inserted = super.insert(alert);
        if (inserted == 1) {
            messagePublisher.publish(toMessage(alert));
        }
        return inserted;
    }

    private ProcessMessage toMessage(ProcessAlertRecordEntity alert) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("alertId", alert.getId());
        payload.put("alertType", alert.getAlertType());
        payload.put("instanceId", alert.getInstanceId());
        payload.put("taskId", alert.getTaskId());
        payload.put("severity", alert.getSeverity());
        return new ProcessMessage(alert.getId(), "ALERT", "流程异常告警", ALERT_MESSAGE,
                Collections.<String>emptyList(), payload, alert.getCreatedAt());
    }
}
