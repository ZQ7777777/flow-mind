package com.flowmind.platform.core.runtime;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 运行时主事务执行器。
 *
 * <p>该组件与 {@link DefaultProcessRuntimeService} 分离，确保事务不会因同类内部调用而失效。
 * 幂等记录的建租约在进入本组件前完成；任务 CAS、变量、历史、后续任务、Outbox 和成功结果
 * 则全部在同一 {@link TransactionTemplate} 中提交。</p>
 */
@Component
public class RuntimeTransactionExecutor {

    private static final int SQLITE_BUSY_MAX_ATTEMPTS = 3;
    private static final long SQLITE_BUSY_RETRY_DELAY_MILLIS = 50L;

    private final TransactionTemplate transactionTemplate;

    public RuntimeTransactionExecutor(PlatformTransactionManager transactionManager) {
        if (transactionManager == null) {
            throw new IllegalArgumentException("transactionManager is required");
        }
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 在独立的运行时主事务中执行工作。 */
    public <T> T execute(final RuntimeTransactionWork<T> work) {
        if (work == null) {
            throw new IllegalArgumentException("work is required");
        }
        for (int attempt = 1; attempt <= SQLITE_BUSY_MAX_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> work.execute());
            } catch (DataAccessException ex) {
                if (!isSqliteBusy(ex) || attempt == SQLITE_BUSY_MAX_ATTEMPTS) {
                    throw ex;
                }
                waitBeforeRetry(attempt);
            }
        }
        throw new IllegalStateException("unreachable runtime transaction retry state");
    }

    private boolean isSqliteBusy(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("SQLITE_BUSY") || message.contains("database is locked"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(SQLITE_BUSY_RETRY_DELAY_MILLIS * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
