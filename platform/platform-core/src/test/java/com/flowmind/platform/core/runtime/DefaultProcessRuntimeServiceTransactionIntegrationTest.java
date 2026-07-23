package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.request.ApproveTaskRequest;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.core.callback.CallbackLogMapper;
import com.flowmind.platform.core.callback.CallbackOutboxService;
import com.flowmind.platform.core.callback.DefaultCallbackService;
import com.flowmind.platform.core.definition.OperationIdempotencyService;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessOperationRecordEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.HistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessCallbackLogRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.ProcessOperationRecordRepository;
import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import com.flowmind.platform.testsupport.SchemaTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证 B 的运行时编排与 C 的 MANDATORY Outbox 在同一主事务中工作。 */
class DefaultProcessRuntimeServiceTransactionIntegrationTest {

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessInstanceRepository instanceRepository;
    private ActiveTaskRepository activeTaskRepository;
    private HistoryTaskRepository historyTaskRepository;
    private ProcessHistoryTaskRepository processHistoryTaskRepository;
    private ProcessOperationRecordRepository operationRecordRepository;
    private ProcessCallbackLogRepository callbackLogRepository;
    private DataSourceTransactionManager transactionManager;
    private AnnotationConfigApplicationContext context;
    private CallbackService callbackService;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaTestSupport.executeSchema(connection);
        ExistingConnectionDataSource dataSource = new ExistingConnectionDataSource(connection);
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        instanceRepository = new ProcessInstanceRepository(jdbcTemplate);
        activeTaskRepository = new ActiveTaskRepository(jdbcTemplate);
        historyTaskRepository = new HistoryTaskRepository(jdbcTemplate);
        processHistoryTaskRepository = new ProcessHistoryTaskRepository(jdbcTemplate);
        operationRecordRepository = new ProcessOperationRecordRepository(jdbcTemplate);
        callbackLogRepository = new ProcessCallbackLogRepository(jdbcTemplate);

        CallbackOutboxService outboxService = new CallbackOutboxService(callbackLogRepository,
                new CallbackLogMapper());
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class);
        context.registerBean(PlatformTransactionManager.class, () -> transactionManager);
        context.registerBean(DefaultCallbackService.class,
                () -> new DefaultCallbackService(callbackLogRepository, new CallbackLogMapper(), outboxService));
        context.refresh();
        callbackService = context.getBean(CallbackService.class);

        insertDefinition();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (context != null) {
            context.close();
        }
        connection.close();
    }

    @Test
    void approveCommitsRuntimeWritesSuccessResultAndMandatoryOutboxTogether() {
        DefaultProcessRuntimeService service = service(callbackService);
        ApproveTaskRequest request = approveRequest("operation-success", "task-review");

        service.approve(request);

        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "SELECT task_status FROM process_active_task WHERE id = ?", String.class, "task-review"));
        assertEquals(1, count("process_history_task"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM process_active_task WHERE id = 'task-next' AND task_status = 'ACTIVE'",
                Integer.class).intValue());
        assertEquals(2, count("process_callback_log"));
        ProcessOperationRecordEntity operation = operationRecordRepository.findByOperationId("operation-success");
        assertEquals("SUCCESS", operation.getOperationStatus());
        assertEquals(false, operation.getResultJson() == null || operation.getResultJson().trim().isEmpty());
    }

    @Test
    void rollsBackRuntimeAndOutboxWhenRealCallbackHasAppendedThenFails() {
        CallbackService failingAfterOutbox = new CallbackService() {
            @Override
            public void publishCallback(WorkflowEvent event) {
                callbackService.publishCallback(event);
                throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "simulated callback failure");
            }

            @Override
            public PageResult<CallbackLogDTO> queryCallbackLogs(CallbackLogQuery query) {
                return callbackService.queryCallbackLogs(query);
            }
        };
        DefaultProcessRuntimeService service = service(failingAfterOutbox);
        ApproveTaskRequest request = approveRequest("operation-rollback", "task-review");

        assertThrows(RuntimeStateException.class, () -> service.approve(request));

        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT task_status FROM process_active_task WHERE id = ?", String.class, "task-review"));
        assertEquals(0, count("process_history_task"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM process_active_task WHERE id = 'task-next'", Integer.class).intValue());
        assertEquals(0, count("process_callback_log"));
        ProcessOperationRecordEntity operation = operationRecordRepository.findByOperationId("operation-rollback");
        assertEquals("FAILED", operation.getOperationStatus());
        assertEquals(null, operation.getResultJson());
    }

    @Test
    void rollsBackTaskCasWhenHistoryArchivingFails() {
        HistoryTaskWriter failingHistoryWriter = mock(HistoryTaskWriter.class);
        doThrow(new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "simulated history failure"))
                .when(failingHistoryWriter).archiveCompletedTask(any(RuntimeTaskContext.class), eq(ActionTypeEnum.APPROVE),
                anyString(), any(), anyString());
        DefaultProcessRuntimeService service = service(callbackService, failingHistoryWriter, false);

        assertThrows(RuntimeStateException.class,
                () -> service.approve(approveRequest("operation-history-rollback", "task-review")));

        assertFullyRolledBack("operation-history-rollback");
    }

    @Test
    void rollsBackTaskCasAndHistoryWhenNextTaskCreationFails() {
        DefaultProcessRuntimeService service = service(callbackService,
                new HistoryTaskWriter(processHistoryTaskRepository), true);

        assertThrows(RuntimeStateException.class,
                () -> service.approve(approveRequest("operation-next-task-rollback", "task-review")));

        assertFullyRolledBack("operation-next-task-rollback");
    }

    private DefaultProcessRuntimeService service(CallbackService runtimeCallbackService) {
        return service(runtimeCallbackService, new HistoryTaskWriter(processHistoryTaskRepository), false);
    }

    private DefaultProcessRuntimeService service(CallbackService runtimeCallbackService,
                                                  HistoryTaskWriter runtimeHistoryWriter,
                                                  boolean failNextTaskCreation) {
        RuntimeDefinitionLoader definitionLoader = mock(RuntimeDefinitionLoader.class);
        RuntimeRequestValidator requestValidator = mock(RuntimeRequestValidator.class);
        RuntimeStateValidator stateValidator = mock(RuntimeStateValidator.class);
        RuntimeNodeAdvancer nodeAdvancer = mock(RuntimeNodeAdvancer.class);
        UserContext operator = new UserContext("manager", "Manager", "dept-1", "Department");
        ProcessInstanceEntity instance = instanceRepository.findById("instance-1");
        ProcessActiveTaskEntity task = activeTaskRepository.findById("task-review");
        ProcessDefinitionDetailDTO definition = definition();

        when(requestValidator.validateTaskIdentity(any(ApproveTaskRequest.class))).thenReturn(operator);
        when(stateValidator.validateTaskAction(eq("task-review"), eq(Long.valueOf(0L)),
                eq(ActionTypeEnum.APPROVE), eq(operator)))
                .thenReturn(new RuntimeTaskContext(instance, task, ActionTypeEnum.APPROVE, operator));
        when(definitionLoader.loadForInstance(any(ProcessInstanceEntity.class))).thenReturn(definition);
        RuntimeAdvancePreparation preparation = new RuntimeAdvancePreparation(
                Collections.<String, List<String>>emptyMap(), Collections.<String, String>emptyMap());
        when(nodeAdvancer.prepareAdvance(any(ProcessInstanceEntity.class), any(ProcessDefinitionDetailDTO.class),
                eq("next"), isNull(), isNull())).thenReturn(preparation);
        if (failNextTaskCreation) {
            doThrow(new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "simulated next task failure"))
                    .when(nodeAdvancer).advanceToNode(any(ProcessInstanceEntity.class), any(ProcessDefinitionDetailDTO.class),
                    eq("next"), isNull(), isNull(), eq(preparation));
        } else {
            doAnswer(invocation -> {
                ProcessActiveTaskEntity next = new ProcessActiveTaskEntity();
                next.setId("task-next");
                next.setInstanceId("instance-1");
                next.setDefinitionId("definition-1");
                next.setNodeCode("next");
                next.setCandidateUserIds("[\"finance\"]");
                next.setTaskStatus("ACTIVE");
                activeTaskRepository.insert(next);
                RuntimeAdvanceResult result = new RuntimeAdvanceResult();
                result.addCreatedTask(RuntimeModelMapper.toDto(next, null, null));
                return result;
            }).when(nodeAdvancer).advanceToNode(any(ProcessInstanceEntity.class), any(ProcessDefinitionDetailDTO.class),
                    eq("next"), isNull(), isNull(), eq(preparation));
        }

        RuntimeOperationExecutor operationExecutor = new RuntimeOperationExecutor(
                new OperationIdempotencyService(operationRecordRepository));
        return new DefaultProcessRuntimeService(instanceRepository, activeTaskRepository, historyTaskRepository,
                definitionLoader, requestValidator, operationExecutor, nodeAdvancer, null, runtimeCallbackService,
                stateValidator, runtimeHistoryWriter,
                new RuntimeTransactionExecutor(transactionManager));
    }

    private void assertFullyRolledBack(String operationId) {
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT task_status FROM process_active_task WHERE id = ?", String.class, "task-review"));
        assertEquals(0, count("process_history_task"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM process_active_task WHERE id = 'task-next'", Integer.class).intValue());
        assertEquals(0, count("process_callback_log"));
        ProcessOperationRecordEntity operation = operationRecordRepository.findByOperationId(operationId);
        assertEquals("FAILED", operation.getOperationStatus());
        assertEquals(null, operation.getResultJson());
    }

    private ApproveTaskRequest approveRequest(String operationId, String taskId) {
        ApproveTaskRequest request = new ApproveTaskRequest();
        request.setOperationId(operationId);
        request.setTaskId(taskId);
        request.setExpectedTaskVersion(Long.valueOf(0L));
        request.setOperatorUserId("manager");
        request.setComment("approved");
        return request;
    }

    private ProcessDefinitionDetailDTO definition() {
        ProcessNodeDTO review = new ProcessNodeDTO();
        review.setNodeCode("review");
        review.setNodeName("Review");
        review.setNodeType(NodeTypeEnum.USER_TASK);
        review.setApproverRuleType(ApproverRuleTypeEnum.USER);
        ProcessNodeDTO next = new ProcessNodeDTO();
        next.setNodeCode("next");
        next.setNodeName("Next");
        next.setNodeType(NodeTypeEnum.USER_TASK);
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode("review-next");
        edge.setSourceNodeCode("review");
        edge.setTargetNodeCode("next");
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setId("definition-1");
        definition.setProcessCode("transaction-test");
        definition.setProcessName("Transaction Test");
        definition.setVersion(Integer.valueOf(1));
        definition.setNodes(Arrays.asList(review, next));
        definition.setEdges(Collections.singletonList(edge));
        return definition;
    }

    private void insertDefinition() {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                "definition-1", "transaction-test", "Transaction Test", "test", 1, "tester");
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, "
                        + "starter_user_id, starter_user_name, variables_json, instance_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-1", "definition-1", "transaction-test", "Transaction Test", 1,
                "Transaction Test", "starter", "Starter", "{}", "RUNNING");
        jdbcTemplate.update("INSERT INTO process_active_task "
                        + "(id, instance_id, definition_id, node_code, candidate_user_ids, task_status, lock_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "task-review", "instance-1", "definition-1", "review", "[\"manager\"]", "ACTIVE", 0);
    }

    private int count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class).intValue();
    }

    @Configuration
    @EnableTransactionManagement
    static class TransactionConfiguration {
    }
}
