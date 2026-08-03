package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.AlertSeverityEnum;
import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeoutPolicyReaderTest {

    @Test
    void calculatesDueAtFromEnabledTimeoutPolicy() {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setTimeoutConfig("{\"enabled\":true,\"durationMinutes\":30,\"severity\":\"HIGH\",\"action\":\"ALERT\"}");
        TimeoutPolicyReader reader = new TimeoutPolicyReader();
        TimeoutDueDateCalculator calculator = new TimeoutDueDateCalculator(reader);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 28, 10, 0);

        TimeoutPolicy policy = reader.read(node);

        assertEquals(Integer.valueOf(30), policy.getDurationMinutes());
        assertEquals(AlertSeverityEnum.HIGH, policy.getSeverity());
        assertEquals(LocalDateTime.of(2026, 7, 28, 10, 30), calculator.calculate(node, createdAt));
    }

    @Test
    void disabledOrMissingTimeoutDoesNotSetDueAt() {
        ProcessNodeDTO node = new ProcessNodeDTO();
        TimeoutPolicyReader reader = new TimeoutPolicyReader();
        TimeoutDueDateCalculator calculator = new TimeoutDueDateCalculator(reader);

        assertFalse(reader.read(node).isEnabled());
        assertNull(calculator.calculate(node, LocalDateTime.of(2026, 7, 28, 10, 0)));
    }

    @Test
    void invalidTimeoutPolicyRaisesStableError() {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setTimeoutConfig("{\"enabled\":true,\"durationMinutes\":-1}");

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> new TimeoutPolicyReader().read(node));

        assertEquals(RuntimeErrorCodes.NODE_CONFIG_INVALID, error.getErrorCode());
    }

    @Test
    void normalizesFrontendTimeoutActionAliases() {
        TimeoutPolicyReader reader = new TimeoutPolicyReader();
        ProcessNodeDTO warning = new ProcessNodeDTO();
        warning.setTimeoutConfig("{\"enabled\":true,\"durationMinutes\":5,\"action\":\"WARNING\"}");
        ProcessNodeDTO forceCompete = new ProcessNodeDTO();
        forceCompete.setTimeoutConfig("{\"enabled\":true,\"durationMinutes\":5,\"action\":\"froce_compete\"}");

        assertEquals(TimeoutPolicy.ACTION_ALERT, reader.read(warning).getAction());
        assertEquals(TimeoutPolicy.ACTION_FORCE_COMPLETE, reader.read(forceCompete).getAction());
    }
}
