package com.flowmind.platform.core.callback;

import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.CallbackStatusEnum;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;
import com.flowmind.platform.api.spi.WorkflowCallbackHandler;
import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import com.flowmind.platform.persistence.entity.ProcessCallbackLogEntity;
import com.flowmind.platform.persistence.repository.ProcessCallbackLogRepository;
import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import com.flowmind.platform.testsupport.SchemaTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CallbackOutboxServiceTest {

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessCallbackLogRepository callbackLogRepository;
    private CallbackLogMapper callbackLogMapper;
    private CallbackOutboxService outboxService;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaTestSupport.executeSchema(connection);
        jdbcTemplate = new JdbcTemplate(new ExistingConnectionDataSource(connection));
        callbackLogRepository = new ProcessCallbackLogRepository(jdbcTemplate);
        callbackLogMapper = new CallbackLogMapper();
        outboxService = new CallbackOutboxService(callbackLogRepository, callbackLogMapper);
        insertDefinitionAndInstance();
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void appendPendingIsIdempotentForSameEventPayload() {
        WorkflowEvent event = event("op-001:TASK_COMPLETED:1", WorkflowEventTypeEnum.TASK_COMPLETED);

        ProcessCallbackLogEntity first = outboxService.appendPending(event);
        ProcessCallbackLogEntity replay = outboxService.appendPending(event);

        assertEquals(first.getEventId(), replay.getEventId());
        assertEquals(1, callbackLogRepository.findPending(10).size());
    }

    @Test
    void appendPendingRejectsSameEventIdWithDifferentPayload() {
        WorkflowEvent first = event("op-001:TASK_COMPLETED:1", WorkflowEventTypeEnum.TASK_COMPLETED);
        WorkflowEvent changed = event("op-001:TASK_COMPLETED:1", WorkflowEventTypeEnum.TASK_CREATED);
        changed.setOperationId("op-001");
        changed.setEventType(WorkflowEventTypeEnum.TASK_COMPLETED);
        changed.setProcessCode("changed-code");
        outboxService.appendPending(first);

        RuntimeValidationException ex = assertThrows(RuntimeValidationException.class,
                () -> outboxService.appendPending(changed));

        assertEquals(RuntimeErrorCodes.CALLBACK_EVENT_CONFLICT, ex.getErrorCode());
    }

    @Test
    void callbackServiceUpdatesSuccessAndFailedStatusWithoutThrowingFailure() {
        WorkflowEvent success = event("op-001:TASK_COMPLETED:1", WorkflowEventTypeEnum.TASK_COMPLETED);
        WorkflowEvent failed = event("op-002:TASK_COMPLETED:1", WorkflowEventTypeEnum.TASK_COMPLETED);
        outboxService.appendPending(success);
        outboxService.appendPending(failed);
        DefaultCallbackService successService = new DefaultCallbackService(callbackLogRepository,
                callbackLogMapper, Collections.<WorkflowCallbackHandler>emptyList());
        DefaultCallbackService failedService = new DefaultCallbackService(callbackLogRepository,
                callbackLogMapper, Arrays.<WorkflowCallbackHandler>asList(new WorkflowCallbackHandler() {
                    @Override
                    public void handle(WorkflowEvent event) {
                        throw new IllegalStateException("remote failed");
                    }
                }));

        successService.publishCallback(success);
        failedService.publishCallback(failed);

        assertEquals(CallbackStatusEnum.SUCCESS.name(),
                callbackLogRepository.findByEventId(success.getEventId()).getCallbackStatus());
        ProcessCallbackLogEntity failedLog = callbackLogRepository.findByEventId(failed.getEventId());
        assertEquals(CallbackStatusEnum.FAILED.name(), failedLog.getCallbackStatus());
        assertEquals(Integer.valueOf(1), failedLog.getRetryCount());
        assertEquals("remote failed", failedLog.getLastError());
    }

    @Test
    void queryCallbackLogsFiltersByStatusAndMapsDto() {
        WorkflowEvent event = event("op-001:TASK_COMPLETED:1", WorkflowEventTypeEnum.TASK_COMPLETED);
        outboxService.appendPending(event);
        CallbackLogQuery query = new CallbackLogQuery();
        query.setInstanceId("instance-1");
        query.setCallbackStatus(CallbackStatusEnum.PENDING);

        CallbackLogDTO dto = new DefaultCallbackService(callbackLogRepository, callbackLogMapper,
                Collections.<WorkflowCallbackHandler>emptyList())
                .queryCallbackLogs(query).getRecords().get(0);

        assertEquals(event.getEventId(), dto.getEventId());
        assertEquals(WorkflowEventTypeEnum.TASK_COMPLETED, dto.getEventType());
        assertEquals(ActionTypeEnum.APPROVE, dto.getActionType());
    }

    private WorkflowEvent event(String eventId, WorkflowEventTypeEnum eventType) {
        WorkflowEvent event = new WorkflowEvent();
        event.setEventId(eventId);
        event.setOperationId(eventId.substring(0, eventId.indexOf(':')));
        event.setEventType(eventType);
        event.setProcessCode("callback-test");
        event.setInstanceId("instance-1");
        event.setActionType(ActionTypeEnum.APPROVE);
        event.setOccurredAt(LocalDateTime.of(2026, 7, 22, 10, 0));
        return event;
    }

    private void insertDefinitionAndInstance() {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "definition-1", "callback-test", "Callback Test", "test", 1, "test");
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, "
                        + "starter_user_id, starter_user_name, instance_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-1", "definition-1", "callback-test", "Callback Test", 1,
                "Callback Test Instance", "starter", "Starter", "RUNNING");
    }
}
