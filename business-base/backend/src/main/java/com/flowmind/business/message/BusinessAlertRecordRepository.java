package com.flowmind.business.message;

import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.core.runtime.RuntimeJsonCodec;
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
        return new ProcessMessage(alert.getId(), "ALERT", "流程异常告警", messageContent(alert),
                Collections.<String>emptyList(), payload, alert.getCreatedAt());
    }

    private String messageContent(ProcessAlertRecordEntity alert) {
        Map<String, Object> detail = alertDetail(alert.getDetailJson());
        return ALERT_MESSAGE + "\n告警类型：" + alertTypeName(alert.getAlertType())
                + "\n告警原因：" + alertReason(alert.getAlertType(), detail);
    }

    private String alertTypeName(String alertType) {
        if ("TASK_TIMEOUT".equals(alertType)) {
            return "任务超时";
        }
        if ("CALLBACK_FAILED".equals(alertType)) {
            return "回调失败";
        }
        if ("ACTION_EXCEPTION".equals(alertType)) {
            return "动作异常";
        }
        return isBlank(alertType) ? "未知告警" : alertType;
    }

    private String alertReason(String alertType, Map<String, Object> detail) {
        if ("TASK_TIMEOUT".equals(alertType)) {
            String reason = "任务已超过处理期限";
            String dueAt = text(detail, "dueAt");
            String action = timeoutActionName(text(detail, "action"));
            if (!isBlank(dueAt)) {
                reason += "，到期时间：" + dueAt;
            }
            if (!isBlank(action)) {
                reason += "，处理动作：" + action;
            }
            return reason;
        }
        if ("CALLBACK_FAILED".equals(alertType)) {
            String summary = text(detail, "errorSummary");
            return isBlank(summary) ? "流程回调发送失败" : summary;
        }
        if ("ACTION_EXCEPTION".equals(alertType)) {
            String summary = text(detail, "errorSummary");
            if (!isBlank(summary)) {
                return summary;
            }
            String errorCode = text(detail, "errorCode");
            return isBlank(errorCode) ? "动作执行发生异常" : "动作执行失败，错误码：" + errorCode;
        }
        String summary = text(detail, "errorSummary");
        return isBlank(summary) ? "告警记录待处理" : summary;
    }

    private Map<String, Object> alertDetail(String detailJson) {
        try {
            return RuntimeJsonCodec.readObjectMap(detailJson);
        } catch (IllegalArgumentException ex) {
            return Collections.emptyMap();
        }
    }

    private String timeoutActionName(String action) {
        if ("REMIND".equals(action)) {
            return "提醒";
        }
        if ("ALERT".equals(action)) {
            return "生成告警";
        }
        if ("JUMP".equals(action)) {
            return "自动跳转";
        }
        if ("TERMINATE".equals(action)) {
            return "自动终止";
        }
        if ("FORCE_COMPLETE".equals(action)) {
            return "强制办结";
        }
        return action;
    }

    private String text(Map<String, Object> detail, String key) {
        Object value = detail.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
