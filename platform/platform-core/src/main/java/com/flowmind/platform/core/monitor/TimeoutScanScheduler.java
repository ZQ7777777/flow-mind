package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.request.TimeoutScanRequest;
import com.flowmind.platform.api.service.ProcessMonitorService;
import com.flowmind.platform.core.runtime.SystemOperatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically runs timeout scan as a platform-owned system task.
 */
public class TimeoutScanScheduler implements InitializingBean, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeoutScanScheduler.class);
    public static final String DEFAULT_OPERATOR_USER_ID = "system_timeout";
    public static final long DEFAULT_INITIAL_DELAY_MILLIS = 5000L;
    public static final long DEFAULT_FIXED_DELAY_MILLIS = 10000L;
    public static final int DEFAULT_LIMIT = 50;

    private final ProcessMonitorService monitorService;
    private final boolean enabled;
    private final long initialDelayMillis;
    private final long fixedDelayMillis;
    private final int limit;
    private final String operatorUserId;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executor;

    public TimeoutScanScheduler(ProcessMonitorService monitorService,
                                boolean enabled,
                                long initialDelayMillis,
                                long fixedDelayMillis,
                                int limit,
                                String operatorUserId) {
        if (monitorService == null) {
            throw new IllegalArgumentException("monitorService is required");
        }
        this.monitorService = monitorService;
        this.enabled = enabled;
        this.initialDelayMillis = Math.max(0L, initialDelayMillis);
        this.fixedDelayMillis = fixedDelayMillis <= 0L ? DEFAULT_FIXED_DELAY_MILLIS : fixedDelayMillis;
        this.limit = limit <= 0 ? DEFAULT_LIMIT : limit;
        this.operatorUserId = isBlank(operatorUserId) ? DEFAULT_OPERATOR_USER_ID : operatorUserId.trim();
    }

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "flow-mind-timeout-scan");
                thread.setDaemon(true);
                return thread;
            }
        });
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                scanOnce();
            }
        }, initialDelayMillis, fixedDelayMillis, TimeUnit.MILLISECONDS);
    }

    public void scanOnce() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            SystemOperatorContext.runAs(systemOperator(), new Runnable() {
                @Override
                public void run() {
                    TimeoutScanRequest request = new TimeoutScanRequest();
                    request.setOperationId("timeout-scan:" + UUID.randomUUID().toString());
                    request.setOperatorUserId(operatorUserId);
                    request.setDryRun(Boolean.FALSE);
                    request.setLimit(Integer.valueOf(limit));
                    monitorService.scanTimeoutTasks(request);
                }
            });
        } catch (RuntimeException ex) {
            LOGGER.warn("Timeout scan failed", ex);
        } finally {
            running.set(false);
        }
    }

    @Override
    public void destroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private UserContext systemOperator() {
        return new UserContext(operatorUserId, operatorUserId, "system", "System");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
