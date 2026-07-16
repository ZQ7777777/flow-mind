package com.flowmind.platform.core;

import com.flowmind.platform.api.dto.OperationRecordDTO;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.OperationStatusEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyAcceptanceBaselineTest {

    @Test
    void sameOperationAndRequestHashReplaysSuccessResult() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
        InMemoryOperationStore store = new InMemoryOperationStore(now);

        OperationDecision first = store.begin("operation-001", "hash-a", ActionTypeEnum.APPROVE, now);
        store.markSuccess("operation-001", "{\"targetId\":\"task-001\"}", now.plusSeconds(1));
        OperationDecision replay = store.begin("operation-001", "hash-a", ActionTypeEnum.APPROVE, now.plusSeconds(2));

        assertEquals(OperationDecisionType.NEW, first.getType());
        assertEquals(OperationDecisionType.REPLAY_SUCCESS, replay.getType());
        assertEquals("{\"targetId\":\"task-001\"}", replay.getRecord().getResultJson());
    }

    @Test
    void sameOperationWithDifferentRequestHashIsRejectedAsConflict() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
        InMemoryOperationStore store = new InMemoryOperationStore(now);

        store.begin("operation-001", "hash-a", ActionTypeEnum.APPROVE, now);
        OperationDecision conflict = store.begin("operation-001", "hash-b", ActionTypeEnum.APPROVE, now.plusSeconds(1));

        assertEquals(OperationDecisionType.CONFLICT, conflict.getType());
    }

    @Test
    void unexpiredProcessingLeaseReturnsInProgress() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
        InMemoryOperationStore store = new InMemoryOperationStore(now);

        store.begin("operation-001", "hash-a", ActionTypeEnum.APPROVE, now);
        OperationDecision inProgress = store.begin("operation-001", "hash-a", ActionTypeEnum.APPROVE, now.plusMinutes(1));

        assertEquals(OperationDecisionType.IN_PROGRESS, inProgress.getType());
    }

    @Test
    void expiredProcessingLeaseCanBeTakenOverBySameRequest() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
        InMemoryOperationStore store = new InMemoryOperationStore(now);

        store.begin("operation-001", "hash-a", ActionTypeEnum.APPROVE, now);
        OperationDecision takeover =
                store.begin("operation-001", "hash-a", ActionTypeEnum.APPROVE, now.plusMinutes(6));

        assertEquals(OperationDecisionType.TAKE_OVER, takeover.getType());
        assertEquals(OperationStatusEnum.PROCESSING, takeover.getRecord().getOperationStatus());
        assertEquals(now.plusMinutes(11), takeover.getRecord().getProcessingExpiresAt());
    }

    @Test
    void failedOperationReturnsRecordedErrorForSameRequest() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
        InMemoryOperationStore store = new InMemoryOperationStore(now);

        store.begin("operation-001", "hash-a", ActionTypeEnum.APPROVE, now);
        store.markFailed("operation-001", "FLOW_TASK_CONCURRENT_MODIFIED", now.plusSeconds(1));
        OperationDecision failed = store.begin("operation-001", "hash-a", ActionTypeEnum.APPROVE, now.plusSeconds(2));

        assertEquals(OperationDecisionType.REPLAY_FAILED, failed.getType());
        assertEquals("FLOW_TASK_CONCURRENT_MODIFIED", failed.getRecord().getErrorCode());
    }

    @Test
    void rolledBackTransactionDoesNotLeaveSuccessResult() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
        InMemoryOperationStore store = new InMemoryOperationStore(now);

        store.begin("operation-001", "hash-a", ActionTypeEnum.APPROVE, now);
        store.rollback("operation-001");

        OperationDecision retry = store.begin("operation-001", "hash-a", ActionTypeEnum.APPROVE, now.plusSeconds(1));

        assertEquals(OperationDecisionType.NEW, retry.getType());
        assertFalse(store.containsSuccess("operation-001"));
    }

    @Test
    void concurrentTaskActionProducesHistoryNextTaskAndCallbackOnlyOnce() throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final TaskSideEffectFixture fixture = new TaskSideEffectFixture();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(action(start, fixture));
            Future<Boolean> second = executor.submit(action(start, fixture));

            start.countDown();

            int successes = (first.get().booleanValue() ? 1 : 0) + (second.get().booleanValue() ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, fixture.getHistoryCount());
            assertEquals(1, fixture.getNextTaskCount());
            assertEquals(1, fixture.getCallbackCount());
        } finally {
            executor.shutdownNow();
        }
    }

    private static Callable<Boolean> action(final CountDownLatch start, final TaskSideEffectFixture fixture) {
        return new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                start.await();
                return Boolean.valueOf(fixture.completeTaskOnce());
            }
        };
    }

    private enum OperationDecisionType {
        NEW,
        REPLAY_SUCCESS,
        REPLAY_FAILED,
        IN_PROGRESS,
        TAKE_OVER,
        CONFLICT
    }

    private static final class OperationDecision {
        private final OperationDecisionType type;
        private final OperationRecordDTO record;

        private OperationDecision(OperationDecisionType type, OperationRecordDTO record) {
            this.type = type;
            this.record = record;
        }

        private OperationDecisionType getType() {
            return type;
        }

        private OperationRecordDTO getRecord() {
            return record;
        }
    }

    private static final class InMemoryOperationStore {
        private static final long PROCESSING_LEASE_MINUTES = 5L;
        private static final long RETENTION_HOURS = 24L;

        private final Map<String, OperationRecordDTO> records = new LinkedHashMap<String, OperationRecordDTO>();

        private InMemoryOperationStore(LocalDateTime ignored) {
        }

        private synchronized OperationDecision begin(String operationId, String requestHash, ActionTypeEnum actionType,
                                                     LocalDateTime now) {
            OperationRecordDTO existing = records.get(operationId);
            if (existing == null) {
                OperationRecordDTO created = new OperationRecordDTO();
                created.setOperationRecordId("record-" + (records.size() + 1));
                created.setOperationId(operationId);
                created.setRequestHash(requestHash);
                created.setActionType(actionType);
                created.setOperationStatus(OperationStatusEnum.PROCESSING);
                created.setProcessingExpiresAt(now.plusMinutes(PROCESSING_LEASE_MINUTES));
                created.setExpiresAt(now.plusHours(RETENTION_HOURS));
                created.setCreatedAt(now);
                created.setUpdatedAt(now);
                records.put(operationId, created);
                return new OperationDecision(OperationDecisionType.NEW, created);
            }
            if (!existing.getRequestHash().equals(requestHash)) {
                return new OperationDecision(OperationDecisionType.CONFLICT, existing);
            }
            if (OperationStatusEnum.SUCCESS.equals(existing.getOperationStatus())) {
                return new OperationDecision(OperationDecisionType.REPLAY_SUCCESS, existing);
            }
            if (OperationStatusEnum.FAILED.equals(existing.getOperationStatus())) {
                return new OperationDecision(OperationDecisionType.REPLAY_FAILED, existing);
            }
            if (existing.getProcessingExpiresAt().isAfter(now)) {
                return new OperationDecision(OperationDecisionType.IN_PROGRESS, existing);
            }
            existing.setProcessingExpiresAt(now.plusMinutes(PROCESSING_LEASE_MINUTES));
            existing.setUpdatedAt(now);
            return new OperationDecision(OperationDecisionType.TAKE_OVER, existing);
        }

        private synchronized void markSuccess(String operationId, String resultJson, LocalDateTime now) {
            OperationRecordDTO record = records.get(operationId);
            record.setOperationStatus(OperationStatusEnum.SUCCESS);
            record.setResultJson(resultJson);
            record.setUpdatedAt(now);
        }

        private synchronized void markFailed(String operationId, String errorCode, LocalDateTime now) {
            OperationRecordDTO record = records.get(operationId);
            record.setOperationStatus(OperationStatusEnum.FAILED);
            record.setErrorCode(errorCode);
            record.setUpdatedAt(now);
        }

        private synchronized void rollback(String operationId) {
            records.remove(operationId);
        }

        private synchronized boolean containsSuccess(String operationId) {
            OperationRecordDTO record = records.get(operationId);
            return record != null && OperationStatusEnum.SUCCESS.equals(record.getOperationStatus());
        }
    }

    private static final class TaskSideEffectFixture {
        private boolean taskCompleted;
        private int historyCount;
        private int nextTaskCount;
        private int callbackCount;

        private synchronized boolean completeTaskOnce() {
            if (taskCompleted) {
                return false;
            }
            taskCompleted = true;
            historyCount++;
            nextTaskCount++;
            callbackCount++;
            return true;
        }

        private int getHistoryCount() {
            return historyCount;
        }

        private int getNextTaskCount() {
            return nextTaskCount;
        }

        private int getCallbackCount() {
            return callbackCount;
        }
    }
}
