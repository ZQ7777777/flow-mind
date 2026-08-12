package com.flowmind.platform.core.monitor;

import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeJsonCodec;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/** Reads the minimal M6 reminder policy JSON. */
@Component
public class ReminderPolicyReader {

    public ReminderPolicy read(ProcessNodeEntity node) {
        ReminderPolicy policy = new ReminderPolicy();
        Map<String, Object> config;
        try {
            config = RuntimeJsonCodec.readObjectMap(node == null ? null : node.getReminderConfig());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeValidationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    "reminderConfig must be a JSON object");
        }
        if (config.isEmpty()) {
            return policy;
        }
        policy.setEnabled(Boolean.TRUE.equals(config.get("enabled"))
                || "true".equalsIgnoreCase(String.valueOf(config.get("enabled"))));
        Object beforeDueMinutes = config.get("beforeDueMinutes");
        if (beforeDueMinutes != null) {
            int value = number(beforeDueMinutes, "beforeDueMinutes");
            if (value <= 0) {
                throw new RuntimeValidationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                        "reminder beforeDueMinutes must be positive");
            }
            policy.setBeforeDueMinutes(Integer.valueOf(value));
        }
        Object maxCount = config.get("maxCount");
        if (maxCount != null) {
            int value = number(maxCount, "maxCount");
            if (value < 0) {
                throw new RuntimeValidationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                        "reminder maxCount must be non-negative");
            }
            policy.setMaxCount(Integer.valueOf(value));
        }
        Object messageTemplate = config.get("messageTemplate");
        if (messageTemplate != null) {
            policy.setMessageTemplate(String.valueOf(messageTemplate));
        }
        return policy;
    }

    private int number(Object value, String fieldName) {
        try {
            return new BigDecimal(String.valueOf(value)).intValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new RuntimeValidationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    fieldName + " must be an integer");
        }
    }
}
