package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.dto.ProcessNodeDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Calculates task due time from a node timeout policy. */
@Component
public class TimeoutDueDateCalculator {

    private final TimeoutPolicyReader timeoutPolicyReader;

    public TimeoutDueDateCalculator(TimeoutPolicyReader timeoutPolicyReader) {
        this.timeoutPolicyReader = timeoutPolicyReader;
    }

    public LocalDateTime calculate(ProcessNodeDTO node, LocalDateTime createdAt) {
        TimeoutPolicy policy = timeoutPolicyReader.read(node);
        if (!policy.isEnabled() || policy.getDurationMinutes() == null || createdAt == null) {
            return null;
        }
        return createdAt.plusMinutes(policy.getDurationMinutes().longValue());
    }
}
