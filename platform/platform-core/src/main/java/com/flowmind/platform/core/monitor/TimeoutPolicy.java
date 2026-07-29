package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.enums.AlertSeverityEnum;

/** Node-level timeout handling policy. */
public class TimeoutPolicy {

    public static final String ACTION_ALERT = "ALERT";
    public static final String ACTION_REMIND = "REMIND";
    public static final String ACTION_JUMP = "JUMP";
    public static final String ACTION_TERMINATE = "TERMINATE";
    public static final String ACTION_FORCE_COMPLETE = "FORCE_COMPLETE";

    private boolean enabled;
    private Integer durationMinutes;
    private AlertSeverityEnum severity = AlertSeverityEnum.MEDIUM;
    private String action = ACTION_ALERT;
    private String targetNodeCode;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public AlertSeverityEnum getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverityEnum severity) {
        this.severity = severity;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetNodeCode() {
        return targetNodeCode;
    }

    public void setTargetNodeCode(String targetNodeCode) {
        this.targetNodeCode = targetNodeCode;
    }
}
