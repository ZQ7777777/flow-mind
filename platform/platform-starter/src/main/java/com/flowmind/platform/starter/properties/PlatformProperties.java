package com.flowmind.platform.starter.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flow-mind.platform")
public class PlatformProperties {
    private boolean enabled = true;
    private final Sqlite sqlite = new Sqlite();
    private final Mock mock = new Mock();
    private final Callback callback = new Callback();
    private final Attachment attachment = new Attachment();
    private final TimeoutScan timeoutScan = new TimeoutScan();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Sqlite getSqlite() {
        return sqlite;
    }

    public Mock getMock() {
        return mock;
    }

    public Callback getCallback() {
        return callback;
    }

    public Attachment getAttachment() {
        return attachment;
    }

    public TimeoutScan getTimeoutScan() {
        return timeoutScan;
    }

    public static class Sqlite {
        private String path = "./data/flow-mind.db";

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class Mock {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Callback {
        private boolean asyncEnabled = true;

        public boolean isAsyncEnabled() {
            return asyncEnabled;
        }

        public void setAsyncEnabled(boolean asyncEnabled) {
            this.asyncEnabled = asyncEnabled;
        }
    }

    public static class Attachment {
        private String localStorageDir = "./data/attachments";

        public String getLocalStorageDir() {
            return localStorageDir;
        }

        public void setLocalStorageDir(String localStorageDir) {
            this.localStorageDir = localStorageDir;
        }
    }

    public static class TimeoutScan {
        private boolean enabled = true;
        private long initialDelayMs = 5000L;
        private long fixedDelayMs = 10000L;
        private int limit = 50;
        private String operatorUserId = "system_timeout";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public String getOperatorUserId() {
            return operatorUserId;
        }

        public void setOperatorUserId(String operatorUserId) {
            this.operatorUserId = operatorUserId;
        }
    }
}
