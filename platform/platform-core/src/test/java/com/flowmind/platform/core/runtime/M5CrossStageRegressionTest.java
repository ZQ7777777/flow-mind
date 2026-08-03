package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.AuditLogQuery;
import com.flowmind.platform.api.dto.CompletedTaskQuery;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TodoTaskQuery;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.request.AddSignRequest;
import com.flowmind.platform.api.request.RejectTaskRequest;
import com.flowmind.platform.api.request.TransferTaskRequest;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.spi.ApproverResolver;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.api.spi.OrganizationProvider;
import com.flowmind.platform.core.audit.DefaultAuditLogWriter;
import com.flowmind.platform.core.callback.CallbackLogMapper;
import com.flowmind.platform.core.callback.CallbackOutboxService;
import com.flowmind.platform.core.callback.DefaultCallbackService;
import com.flowmind.platform.core.definition.OperationIdempotencyService;
import com.flowmind.platform.core.query.DefaultTaskQueryService;
import com.flowmind.platform.core.query.ProcessTraceAssembler;
import com.flowmind.platform.core.query.RuntimeQueryAssembler;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.HistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessAuditLogRepository;
import com.flowmind.platform.persistence.repository.ProcessCallbackLogRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.ProcessOperationRecordRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import com.flowmind.platform.testsupport.SchemaTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M5 A/B/C 跨线回归：A 线动作规则驱动 B 线增强动作，C 线查询统一读取其结果。
 */
class M5CrossStageRegressionTest {

    @TempDir
    Path temporaryDirectory;

    private Connection firstConnection;
    private Connection secondConnection;

    @AfterEach
    void closeConnections() throws Exception {
        if (secondConnection != null) {
            secondConnection.close();
        }
        if (firstConnection != null) {
            firstConnection.close();
        }
    }

    @Test
    void transferAndConfiguredRejectAreVisibleToTodoCompletedAuditAndCallbackQueries() throws Exception {
        firstConnection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaTestSupport.executeSchema(firstConnection);
        WorkflowFixture fixture = fixture(firstConnection, user("reviewer", "Reviewer"));
        fixture.seedWithApplyHistory();

        fixture.coordinator.transfer(transfer("transfer-1", "review-task", 0L,
                "reviewer", "receiver"));

        fixture.currentUser.set(user("receiver", "Receiver"));
        assertEquals(1L, fixture.queryService.queryTodoTasks(new TodoTaskQuery()).getTotal().longValue());
        assertEquals("receiver", fixture.queryService.queryTodoTasks(new TodoTaskQuery())
                .getRecords().get(0).getAssigneeUserId());

        RejectTaskRequest reject = new RejectTaskRequest();
        reject.setOperationId("reject-1");
        reject.setTaskId("review-task");
        reject.setExpectedTaskVersion(Long.valueOf(1L));
        reject.setOperatorUserId("receiver");
        reject.setTargetNodeCode("apply");
        reject.setComment("return for correction");
        fixture.coordinator.reject(reject);

        assertEquals(1L, fixture.queryService.queryCompletedTasks(new CompletedTaskQuery()).getTotal().longValue());
        fixture.currentUser.set(user("starter", "Starter"));
        assertEquals("apply", fixture.queryService.queryTodoTasks(new TodoTaskQuery())
                .getRecords().get(0).getNodeCode());

        AuditLogQuery auditQuery = new AuditLogQuery();
        auditQuery.setInstanceId("instance-m5");
        assertEquals(2L, fixture.auditRepository.count(auditQuery));
        List<String> auditActions = Arrays.asList(
                fixture.auditRepository.query(auditQuery).get(0).getActionType(),
                fixture.auditRepository.query(auditQuery).get(1).getActionType());
        assertTrue(auditActions.contains(ActionTypeEnum.TRANSFER.name()));
        assertTrue(auditActions.contains(ActionTypeEnum.REJECT.name()));
        assertTrue(fixture.callbackRepository.count(callbackQuery("instance-m5")) >= 3L);
        assertNotNull(fixture.callbackRepository.findByEventId(
                "reject-1:PROCESS_REJECTED:review-task"));
    }

    @Test
    void concurrentTransfersProduceOneHistoryAuditAndCallbackOutcome() throws Exception {
        String database = temporaryDirectory.resolve("m5-concurrency.db").toAbsolutePath().toString().replace('\\', '/');
        String jdbcUrl = "jdbc:sqlite:" + database;
        try (Connection bootstrap = DriverManager.getConnection(jdbcUrl)) {
            try (Statement statement = bootstrap.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA busy_timeout = 10000");
            }
            SchemaTestSupport.executeSchema(bootstrap);
            fixture(bootstrap, user("reviewer", "Reviewer")).seed();
        }
        firstConnection = open(jdbcUrl);
        secondConnection = open(jdbcUrl);
        final WorkflowFixture first = fixture(firstConnection, user("reviewer", "Reviewer"));
        final WorkflowFixture second = fixture(secondConnection, user("reviewer", "Reviewer"));

        JdbcTemplate jdbc = first.jdbc;
        List<Integer> casResults = concurrently(
                () -> Integer.valueOf(first.activeTaskRepository.transfer(
                        "review-task", 0L, "receiver", "Receiver")),
                () -> Integer.valueOf(second.activeTaskRepository.transfer(
                        "review-task", 0L, "backup", "Backup")));
        assertEquals(1, casResults.get(0).intValue() + casResults.get(1).intValue());
        jdbc.update("UPDATE process_active_task SET assignee_user_id = 'reviewer', "
                + "assignee_user_name = 'Reviewer', lock_version = 0 WHERE id = 'review-task'");

        first.coordinator.transfer(transfer("transfer-a", "review-task", 0L, "reviewer", "receiver"));
        RuntimeValidationException stale = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeValidationException.class,
                () -> second.coordinator.transfer(
                        transfer("transfer-b", "review-task", 0L, "reviewer", "backup")));
        assertEquals(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED, stale.getErrorCode());
        assertEquals(1, count(jdbc, "process_history_task"));
        assertEquals(1, count(jdbc, "process_audit_log"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM process_callback_log "
                + "WHERE event_type = 'TASK_TRANSFERRED'", Integer.class).intValue());
        assertEquals(1L, jdbc.queryForObject("SELECT lock_version FROM process_active_task "
                + "WHERE id = 'review-task'", Long.class).longValue());
    }

    @Test
    void addSignCreatesQueryableTodosAndUnifiedAudit() throws Exception {
        firstConnection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaTestSupport.executeSchema(firstConnection);
        WorkflowFixture fixture = fixture(firstConnection, user("reviewer", "Reviewer"));
        fixture.seed();

        AddSignRequest request = new AddSignRequest();
        request.setOperationId("add-sign-1");
        request.setTaskId("review-task");
        request.setExpectedTaskVersion(Long.valueOf(0L));
        request.setOperatorUserId("reviewer");
        request.setAddSignUserIds(Arrays.asList("receiver", "backup"));
        request.setComment("additional review");
        fixture.coordinator.addSign(request);

        fixture.currentUser.set(user("receiver", "Receiver"));
        assertEquals(1L, fixture.queryService.queryTodoTasks(new TodoTaskQuery()).getTotal().longValue());
        fixture.currentUser.set(user("backup", "Backup"));
        assertEquals(1L, fixture.queryService.queryTodoTasks(new TodoTaskQuery()).getTotal().longValue());
        AuditLogQuery auditQuery = new AuditLogQuery();
        auditQuery.setInstanceId("instance-m5");
        assertEquals(ActionTypeEnum.ADD_SIGN.name(),
                fixture.auditRepository.query(auditQuery).get(0).getActionType());
        assertNotNull(fixture.callbackRepository.findByEventId(
                "add-sign-1:TASK_ADDED_SIGN:review-task"));
    }

    private WorkflowFixture fixture(Connection connection, UserContext initialUser) {
        ExistingConnectionDataSource dataSource = new ExistingConnectionDataSource(connection);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        ActiveTaskRepository tasks = new ActiveTaskRepository(jdbc);
        ProcessInstanceRepository instances = new ProcessInstanceRepository(jdbc);
        ProcessHistoryTaskRepository histories = new ProcessHistoryTaskRepository(jdbc);
        HistoryTaskRepository historyQueries = new HistoryTaskRepository(jdbc);
        TaskGroupRepository groups = new TaskGroupRepository(jdbc);
        ProcessOperationRecordRepository operations = new ProcessOperationRecordRepository(jdbc);
        ProcessCallbackLogRepository callbacks = new ProcessCallbackLogRepository(jdbc);
        ProcessAuditLogRepository audits = new ProcessAuditLogRepository(jdbc);
        MutableCurrentUserProvider currentUser = new MutableCurrentUserProvider(initialUser);
        RuntimeRequestValidator validator = new RuntimeRequestValidator(currentUser);
        RuntimeDefinitionLoader definitionLoader = mock(RuntimeDefinitionLoader.class);
        ProcessDefinitionDetailDTO definition = definition();
        when(definitionLoader.loadForInstance(any())).thenReturn(definition);
        ApproverResolver resolver = request -> Collections.singletonList(
                userDto("apply".equals(request.getNodeCode()) ? "starter" : "reviewer",
                        "apply".equals(request.getNodeCode()) ? "Starter" : "Reviewer"));
        RuntimeNodeAdvancer advancer = new RuntimeNodeAdvancer(tasks, groups, instances, validator, resolver,
                new SimpleConditionExpressionEvaluator(),
                new ApproverResolveRequestFactory(new RuntimeNodeConfigReader()));
        CallbackService callbackService = new DefaultCallbackService(callbacks, new CallbackLogMapper(),
                new CallbackOutboxService(callbacks, new CallbackLogMapper()));
        OrganizationProvider organization = organization();
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("organizationProvider", organization);
        EnhancedTaskActionCoordinator coordinator = new EnhancedTaskActionCoordinator(instances, tasks, histories,
                groups, definitionLoader, validator,
                new RuntimeOperationExecutor(new OperationIdempotencyService(operations)), advancer,
                new RuntimeStateValidator(tasks, instances, groups), new HistoryTaskWriter(histories),
                new RuntimeTransactionExecutor(transactionManager), callbackService,
                factory.getBeanProvider(OrganizationProvider.class), new DefaultAuditLogWriter(audits));
        DefaultTaskQueryService queryService = new DefaultTaskQueryService(histories, tasks, instances,
                new ProcessTraceAssembler(), new RuntimeQueryAssembler(), currentUser);
        return new WorkflowFixture(jdbc, tasks, coordinator, queryService, currentUser, audits, callbacks);
    }

    private ProcessDefinitionDetailDTO definition() {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setId("definition-m5");
        definition.setProcessCode("m5-flow");
        definition.setProcessName("M5 Flow");
        definition.setVersion(Integer.valueOf(1));
        ProcessNodeDTO apply = node("apply", ApproverRuleTypeEnum.STARTER, null);
        ProcessNodeDTO review = node("review", ApproverRuleTypeEnum.USER,
                "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"apply\"]}}}");
        definition.setNodes(Arrays.asList(apply, review));
        definition.setEdges(Collections.emptyList());
        return definition;
    }

    private ProcessNodeDTO node(String code, ApproverRuleTypeEnum rule, String listenerConfig) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeName(code);
        node.setNodeType(NodeTypeEnum.USER_TASK);
        node.setApproverRuleType(rule);
        node.setApproverRuleConfig(rule == ApproverRuleTypeEnum.USER ? "{\"user\":\"fixed\"}" : null);
        node.setMultiInstanceMode(MultiInstanceModeEnum.SINGLE);
        node.setListenerConfig(listenerConfig);
        return node;
    }

    private OrganizationProvider organization() {
        OrganizationProvider provider = mock(OrganizationProvider.class);
        when(provider.findUser("receiver")).thenReturn(Optional.of(userDto("receiver", "Receiver")));
        when(provider.findUser("backup")).thenReturn(Optional.of(userDto("backup", "Backup")));
        return provider;
    }

    private UserDTO userDto(String id, String name) {
        return new UserDTO(id, name);
    }

    private TransferTaskRequest transfer(String operationId, String taskId, long version,
                                            String operator, String target) {
        TransferTaskRequest request = new TransferTaskRequest();
        request.setOperationId(operationId);
        request.setTaskId(taskId);
        request.setExpectedTaskVersion(Long.valueOf(version));
        request.setOperatorUserId(operator);
        request.setTargetUserId(target);
        request.setComment("transfer");
        return request;
    }

    private com.flowmind.platform.api.dto.CallbackLogQuery callbackQuery(String instanceId) {
        com.flowmind.platform.api.dto.CallbackLogQuery query =
                new com.flowmind.platform.api.dto.CallbackLogQuery();
        query.setInstanceId(instanceId);
        return query;
    }

    private List<Integer> concurrently(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = executor.submit(awaitStart(ready, start, first));
            Future<Integer> b = executor.submit(awaitStart(ready, start, second));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return Arrays.asList(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Integer> awaitStart(final CountDownLatch ready, final CountDownLatch start,
                                         final Callable<Integer> action) {
        return new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return action.call();
            }
        };
    }

    private int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class).intValue();
    }

    private Connection open(String jdbcUrl) throws Exception {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 10000");
        }
        return connection;
    }

    private UserContext user(String id, String name) {
        return new UserContext(id, name, "dept-1", "Department");
    }

    private static final class MutableCurrentUserProvider implements CurrentUserProvider {
        private UserContext user;

        private MutableCurrentUserProvider(UserContext user) {
            this.user = user;
        }

        private void set(UserContext user) {
            this.user = user;
        }

        @Override
        public UserContext getCurrentUser() {
            return user;
        }
    }

    private static final class WorkflowFixture {
        private final JdbcTemplate jdbc;
        private final ActiveTaskRepository activeTaskRepository;
        private final EnhancedTaskActionCoordinator coordinator;
        private final DefaultTaskQueryService queryService;
        private final MutableCurrentUserProvider currentUser;
        private final ProcessAuditLogRepository auditRepository;
        private final ProcessCallbackLogRepository callbackRepository;

        private WorkflowFixture(JdbcTemplate jdbc, ActiveTaskRepository activeTaskRepository,
                                EnhancedTaskActionCoordinator coordinator,
                                DefaultTaskQueryService queryService, MutableCurrentUserProvider currentUser,
                                ProcessAuditLogRepository auditRepository,
                                ProcessCallbackLogRepository callbackRepository) {
            this.jdbc = jdbc;
            this.activeTaskRepository = activeTaskRepository;
            this.coordinator = coordinator;
            this.queryService = queryService;
            this.currentUser = currentUser;
            this.auditRepository = auditRepository;
            this.callbackRepository = callbackRepository;
        }

        private void seed() {
            seed(false);
        }

        private void seedWithApplyHistory() {
            seed(true);
        }

        private void seed(boolean includeApplyHistory) {
            jdbc.update("INSERT INTO process_definition "
                            + "(id, process_code, process_name, system_code, version, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    "definition-m5", "m5-flow", "M5 Flow", "test", 1, "tester");
            jdbc.update("INSERT INTO process_node "
                            + "(id, definition_id, node_code, node_name, node_type, approver_rule_type, "
                            + "multi_instance_mode, listener_config, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "node-apply", "definition-m5", "apply", "apply", "USER_TASK", "STARTER",
                    "SINGLE", null, 1);
            jdbc.update("INSERT INTO process_node "
                            + "(id, definition_id, node_code, node_name, node_type, approver_rule_type, "
                            + "approver_rule_config, multi_instance_mode, listener_config, sort_order) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "node-review", "definition-m5", "review", "review", "USER_TASK", "USER",
                    "{\"user\":\"fixed\"}", "SINGLE",
                    "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"apply\"]}}}", 2);
            jdbc.update("INSERT INTO process_instance "
                            + "(id, definition_id, process_code, process_name, version, instance_title, "
                            + "starter_user_id, starter_user_name, current_node_codes, variables_json, "
                            + "instance_status, started_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "instance-m5", "definition-m5", "m5-flow", "M5 Flow", 1, "M5 acceptance",
                    "starter", "Starter", "[\"review\"]", "{}", "RUNNING", LocalDateTime.now().toString());
            if (includeApplyHistory) {
                jdbc.update("INSERT INTO process_history_task "
                                + "(id, instance_id, operation_id, active_task_id, node_code, assignee_user_id, "
                                + "assignee_user_name, handle_type, action_type, comment_text, variables_snapshot, "
                                + "started_at, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        "history-apply", "instance-m5", "start-1", "apply-task", "apply", "starter",
                        "Starter", "NORMAL", ActionTypeEnum.SEND.name(), "submitted", "{}",
                        LocalDateTime.now().minusMinutes(2).toString(), LocalDateTime.now().minusMinutes(1).toString());
            }
            jdbc.update("INSERT INTO process_active_task "
                            + "(id, instance_id, definition_id, node_code, assignee_user_id, assignee_user_name, "
                            + "task_status, lock_version, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "review-task", "instance-m5", "definition-m5", "review", "reviewer", "Reviewer",
                    "ACTIVE", 0, LocalDateTime.now().toString());
        }
    }
}
