package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.AlertSeverityEnum;
import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeJsonCodec;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/** Reads and validates the minimal M6 timeout policy JSON. */
@Component
public class TimeoutPolicyReader {

    public TimeoutPolicy read(ProcessNodeDTO node) {
        return readJson(node == null ? null : node.getTimeoutConfig());
    }

    public TimeoutPolicy read(ProcessNodeEntity node) {
        return readJson(node == null ? null : node.getTimeoutConfig());
    }

    public TimeoutPolicy readJson(String json) {
        TimeoutPolicy policy = new TimeoutPolicy();
        Map<String, Object> config;
        try {
            config = RuntimeJsonCodec.readObjectMap(json);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeValidationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    "timeoutConfig must be a JSON object");
        }
        if (config.isEmpty()) {
            return policy;
        }
        policy.setEnabled(Boolean.TRUE.equals(config.get("enabled"))
                || "true".equalsIgnoreCase(String.valueOf(config.get("enabled"))));
        if (!policy.isEnabled()) {
            return policy;
        }
        Integer duration = integerValue(config.get("durationMinutes"), "durationMinutes");
        if (duration == null || duration.intValue() < 0) {
            throw new RuntimeValidationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    "timeout durationMinutes must be non-negative");
        }
        policy.setDurationMinutes(duration);
        Object severity = config.get("severity");
        if (severity != null) {
            try {
                policy.setSeverity(AlertSeverityEnum.valueOf(String.valueOf(severity).trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new RuntimeValidationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                        "unknown timeout severity");
            }
        }
        Object action = config.get("action");
        if (action != null) {
            String value = normalizeAction(action);
            if (!isSupportedAction(value)) {
                throw new RuntimeValidationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                        "unknown timeout action");
            }
            policy.setAction(value);
        }
        Object targetNodeCode = config.get("targetNodeCode");
        if (targetNodeCode != null) {
            policy.setTargetNodeCode(String.valueOf(targetNodeCode).trim());
        }
        if (TimeoutPolicy.ACTION_JUMP.equals(policy.getAction())
                && (policy.getTargetNodeCode() == null || policy.getTargetNodeCode().isEmpty())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    "timeout action JUMP requires targetNodeCode");
        }
        return policy;
    }

    public String normalizeAction(Object action) {
        String value = String.valueOf(action).trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if ("WARNING".equals(value) || "WARN".equals(value)) {
            return TimeoutPolicy.ACTION_ALERT;
        }
        if ("FORCE_COMPETE".equals(value) || "FROCE_COMPETE".equals(value)
                || "FORCE_COMPELETE".equals(value) || "FORCE_COMPLETED".equals(value)) {
            return TimeoutPolicy.ACTION_FORCE_COMPLETE;
        }
        return value;
    }

    public boolean isSupportedAction(String value) {
        return TimeoutPolicy.ACTION_ALERT.equals(value)
                || TimeoutPolicy.ACTION_REMIND.equals(value)
                || TimeoutPolicy.ACTION_JUMP.equals(value)
                || TimeoutPolicy.ACTION_TERMINATE.equals(value)
                || TimeoutPolicy.ACTION_FORCE_COMPLETE.equals(value);
    }

    private Integer integerValue(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new RuntimeValidationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    fieldName + " must be a number");
        }
    }
}
