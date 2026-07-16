package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.CallbackStatusEnum;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowEventContractTest {

    @Test
    void workflowEventExpressesCallbackPayloadRequiredByContract() {
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("amount", 100);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 15, 10, 0);

        UserContext operator = new UserContext("user-001", "Operator", "dept-001", "Department");
        HistoryTaskDTO archivedTask = new HistoryTaskDTO();
        archivedTask.setHistoryTaskId("history-001");
        TaskDTO createdTask = new TaskDTO();
        createdTask.setTaskId("task-002");

        WorkflowEvent event = new WorkflowEvent();
        event.setEventId("operation-001:TASK_COMPLETED:1");
        event.setOperationId("operation-001");
        event.setEventType(WorkflowEventTypeEnum.TASK_COMPLETED);
        event.setProcessCode("process-code-001");
        event.setInstanceId("instance-001");
        event.setActionType(ActionTypeEnum.APPROVE);
        event.setOperator(operator);
        event.setArchivedTasks(Collections.singletonList(archivedTask));
        event.setCreatedTasks(Collections.singletonList(createdTask));
        event.setVariables(variables);
        event.setOccurredAt(occurredAt);

        assertEquals("operation-001:TASK_COMPLETED:1", event.getEventId());
        assertEquals("operation-001", event.getOperationId());
        assertEquals(WorkflowEventTypeEnum.TASK_COMPLETED, event.getEventType());
        assertEquals("TASK_COMPLETED", event.getEventTypeCode());
        assertEquals("process-code-001", event.getProcessCode());
        assertEquals("instance-001", event.getInstanceId());
        assertEquals(ActionTypeEnum.APPROVE, event.getActionType());
        assertEquals(operator, event.getOperator());
        assertEquals(Collections.singletonList(archivedTask), event.getArchivedTasks());
        assertEquals(Collections.singletonList(createdTask), event.getCreatedTasks());
        assertEquals(variables, event.getVariables());
        assertEquals(occurredAt, event.getOccurredAt());
    }

    @Test
    void workflowEventAcceptsStringEventTypeForSerializationAdapters() {
        WorkflowEvent event = new WorkflowEvent();

        event.setEventType("PROCESS_STARTED");

        assertEquals(WorkflowEventTypeEnum.PROCESS_STARTED, event.getEventType());
        assertEquals("PROCESS_STARTED", event.getEventTypeCode());
    }

    @Test
    void callbackLogExpressesPersistedCallbackFields() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 15, 10, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 15, 10, 1);
        CallbackLogDTO log = new CallbackLogDTO();

        log.setCallbackLogId("callback-log-001");
        log.setEventId("operation-001:TASK_CREATED:1");
        log.setInstanceId("instance-001");
        log.setOperationId("operation-001");
        log.setEventType("TASK_CREATED");
        log.setActionType(ActionTypeEnum.START);
        log.setPayloadJson("{\"eventId\":\"operation-001:TASK_CREATED:1\"}");
        log.setCallbackStatus(CallbackStatusEnum.FAILED);
        log.setRetryCount(Integer.valueOf(2));
        log.setLastError("timeout");
        log.setCreatedAt(createdAt);
        log.setUpdatedAt(updatedAt);
        log.setCompletedAt(updatedAt);

        assertEquals("callback-log-001", log.getCallbackLogId());
        assertEquals("operation-001:TASK_CREATED:1", log.getEventId());
        assertEquals("instance-001", log.getInstanceId());
        assertEquals("operation-001", log.getOperationId());
        assertEquals(WorkflowEventTypeEnum.TASK_CREATED, log.getEventType());
        assertEquals("TASK_CREATED", log.getEventTypeCode());
        assertEquals(ActionTypeEnum.START, log.getActionType());
        assertEquals("{\"eventId\":\"operation-001:TASK_CREATED:1\"}", log.getPayloadJson());
        assertEquals(CallbackStatusEnum.FAILED, log.getCallbackStatus());
        assertEquals(Integer.valueOf(2), log.getRetryCount());
        assertEquals("timeout", log.getLastError());
        assertEquals("timeout", log.getErrorMessage());
        assertEquals(createdAt, log.getCreatedAt());
        assertEquals(updatedAt, log.getUpdatedAt());
        assertEquals(updatedAt, log.getCompletedAt());
    }

    @Test
    void callbackLogEventIdCanBeUsedAsIdempotencyKey() {
        CallbackLogDTO first = new CallbackLogDTO();
        first.setEventId("operation-001:TASK_CREATED:1");
        first.setCallbackStatus(CallbackStatusEnum.PENDING);

        CallbackLogDTO replay = new CallbackLogDTO();
        replay.setEventId("operation-001:TASK_CREATED:1");
        replay.setCallbackStatus(CallbackStatusEnum.SUCCESS);

        Map<String, CallbackLogDTO> logsByEventId = new LinkedHashMap<String, CallbackLogDTO>();
        logsByEventId.put(first.getEventId(), first);
        logsByEventId.put(replay.getEventId(), replay);

        assertEquals(1, logsByEventId.size());
        assertEquals(CallbackStatusEnum.SUCCESS,
                logsByEventId.get("operation-001:TASK_CREATED:1").getCallbackStatus());
    }
}
