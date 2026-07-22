package com.flowmind.platform.core.callback;

import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import com.flowmind.platform.persistence.entity.ProcessCallbackLogEntity;
import com.flowmind.platform.persistence.repository.ProcessCallbackLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 回调 Outbox 写入服务。只负责事务内落库，不调用外部回调 SPI。
 */
@Service
public class CallbackOutboxService {

    private final ProcessCallbackLogRepository callbackLogRepository;
    private final CallbackLogMapper callbackLogMapper;

    public CallbackOutboxService(ProcessCallbackLogRepository callbackLogRepository,
                                 CallbackLogMapper callbackLogMapper) {
        this.callbackLogRepository = callbackLogRepository;
        this.callbackLogMapper = callbackLogMapper;
    }

    public ProcessCallbackLogEntity appendPending(WorkflowEvent event) {
        validate(event);
        ProcessCallbackLogEntity candidate = callbackLogMapper.toPendingEntity(event);
        ProcessCallbackLogEntity existing = callbackLogRepository.findByEventId(event.getEventId());
        if (existing != null) {
            if (sameBusinessEvent(existing, candidate)) {
                return existing;
            }
            throw new RuntimeValidationException(RuntimeErrorCodes.CALLBACK_EVENT_CONFLICT,
                    "callback eventId already exists with different payload");
        }
        try {
            callbackLogRepository.insertPending(candidate);
            return candidate;
        } catch (DataIntegrityViolationException ex) {
            existing = callbackLogRepository.findByEventId(event.getEventId());
            if (existing != null && sameBusinessEvent(existing, candidate)) {
                return existing;
            }
            if (existing != null) {
                throw new RuntimeValidationException(RuntimeErrorCodes.CALLBACK_EVENT_CONFLICT,
                        "callback eventId already exists with different payload", ex);
            }
            throw ex;
        }
    }

    public List<ProcessCallbackLogEntity> appendPendingBatch(List<WorkflowEvent> events) {
        List<ProcessCallbackLogEntity> results = new ArrayList<ProcessCallbackLogEntity>();
        if (events == null) {
            return results;
        }
        for (WorkflowEvent event : events) {
            results.add(appendPending(event));
        }
        return results;
    }

    private boolean sameBusinessEvent(ProcessCallbackLogEntity existing, ProcessCallbackLogEntity candidate) {
        return equals(existing.getOperationId(), candidate.getOperationId())
                && equals(existing.getInstanceId(), candidate.getInstanceId())
                && equals(existing.getEventType(), candidate.getEventType())
                && equals(existing.getActionType(), candidate.getActionType())
                && equals(existing.getPayloadJson(), candidate.getPayloadJson());
    }

    private boolean equals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private void validate(WorkflowEvent event) {
        if (event == null || event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("eventId must not be empty");
        }
        if (event.getOperationId() == null || event.getOperationId().trim().isEmpty()) {
            throw new IllegalArgumentException("operationId must not be empty");
        }
        if (event.getEventType() == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        if (event.getActionType() == null) {
            throw new IllegalArgumentException("actionType must not be null");
        }
    }
}
