package com.flowmind.platform.core.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 提交事务后定期派发回调 Outbox。
 *
 * @author FlowMind
 * @since 1.0.0
 */
public class CallbackDispatchScheduler implements InitializingBean, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(CallbackDispatchScheduler.class);
    private final CallbackDispatchService dispatchService;
    private final long initialDelayMs;
    private final long fixedDelayMs;
    private final int limit;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executor;

    public CallbackDispatchScheduler(CallbackDispatchService dispatchService,
                                     long initialDelayMs,
                                     long fixedDelayMs,
                                     int limit) {
        if (dispatchService == null) {
            throw new IllegalArgumentException("dispatchService is required");
        }
        this.dispatchService = dispatchService;
        this.initialDelayMs = Math.max(0L, initialDelayMs);
        this.fixedDelayMs = fixedDelayMs <= 0L ? 1000L : fixedDelayMs;
        this.limit = limit <= 0 ? 50 : limit;
    }

    @Override
    public void afterPropertiesSet() {
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "flow-mind-callback-dispatch");
                thread.setDaemon(true);
                return thread;
            }
        });
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                dispatchOnce();
            }
        }, initialDelayMs, fixedDelayMs, TimeUnit.MILLISECONDS);
    }

    public void dispatchOnce() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            dispatchService.dispatchPending(limit);
        } catch (RuntimeException ex) {
            LOGGER.warn("Callback dispatch scan failed", ex);
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
}
