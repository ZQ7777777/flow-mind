package com.flowmind.platform.core.callback;

import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;
import com.flowmind.platform.api.spi.WorkflowCallbackHandler;
import com.flowmind.platform.persistence.entity.ProcessCallbackLogEntity;
import com.flowmind.platform.persistence.repository.ProcessCallbackLogRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallbackDispatchServiceTest {

    @Test
    void failedCallbackIsMarkedFailedAndAlerted() {
        ProcessCallbackLogRepository repository = mock(ProcessCallbackLogRepository.class);
        WorkflowCallbackHandler handler = mock(WorkflowCallbackHandler.class);
        CallbackFailureAlertService failureAlertService = mock(CallbackFailureAlertService.class);
        ProcessCallbackLogEntity log = log();
        when(repository.findPending(10)).thenReturn(Collections.singletonList(log));
        when(repository.findByEventId("event-1")).thenReturn(log);
        doThrow(new IllegalStateException("callback down")).when(handler)
                .handle(org.mockito.ArgumentMatchers.any(WorkflowEvent.class));

        int dispatched = new CallbackDispatchService(repository, handler, failureAlertService).dispatchPending(10);

        assertEquals(0, dispatched);
        verify(repository).markFailed("event-1", "callback down");
        verify(failureAlertService).alert(log, "callback down");
    }

    private ProcessCallbackLogEntity log() {
        WorkflowEvent event = new WorkflowEvent();
        event.setEventId("event-1");
        event.setOperationId("operation-1");
        event.setInstanceId("instance-1");
        event.setProcessCode("expense");
        event.setEventType(WorkflowEventTypeEnum.PROCESS_STARTED);
        event.setActionType(ActionTypeEnum.START);
        event.setOccurredAt(LocalDateTime.of(2026, 7, 28, 10, 0));
        ProcessCallbackLogEntity log = new ProcessCallbackLogEntity();
        log.setId("callback-1");
        log.setEventId("event-1");
        log.setInstanceId("instance-1");
        log.setOperationId("operation-1");
        log.setEventType(WorkflowEventTypeEnum.PROCESS_STARTED.name());
        log.setActionType(ActionTypeEnum.START.name());
        log.setPayloadJson(new CallbackLogMapper().toPayloadJson(event));
        log.setRetryCount(Integer.valueOf(0));
        return log;
    }
}
