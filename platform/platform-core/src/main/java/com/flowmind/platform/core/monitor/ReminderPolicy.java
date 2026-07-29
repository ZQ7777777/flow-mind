package com.flowmind.platform.core.monitor;

/** Minimal automatic reminder policy for timeout governance. */
public class ReminderPolicy {

    private boolean enabled;
    private Integer maxCount = Integer.valueOf(1);
    private String messageTemplate;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(Integer maxCount) {
        this.maxCount = maxCount;
    }

    public String getMessageTemplate() {
        return messageTemplate;
    }

    public void setMessageTemplate(String messageTemplate) {
        this.messageTemplate = messageTemplate;
    }
}
