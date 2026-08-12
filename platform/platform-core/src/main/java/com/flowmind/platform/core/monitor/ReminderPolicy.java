package com.flowmind.platform.core.monitor;

/** Minimal automatic reminder policy for timeout governance. */
public class ReminderPolicy {

    private boolean enabled;
    /** 到期前触发自动提醒的分钟数，默认 30 分钟。 */
    private Integer beforeDueMinutes = Integer.valueOf(30);
    private Integer maxCount = Integer.valueOf(1);
    private String messageTemplate;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getBeforeDueMinutes() {
        return beforeDueMinutes;
    }

    public void setBeforeDueMinutes(Integer beforeDueMinutes) {
        this.beforeDueMinutes = beforeDueMinutes;
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
