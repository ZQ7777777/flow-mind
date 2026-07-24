package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.AttachmentTemplateCheckResult;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.request.ApproveTaskRequest;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.request.SubmitTaskRequest;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.spi.ApproverResolver;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.core.callback.CallbackLogMapper;
import com.flowmind.platform.core.callback.CallbackOutboxService;
import com.flowmind.platform.core.callback.DefaultCallbackService;
import com.flowmind.platform.core.definition.OperationIdempotencyService;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.HistoryTaskRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real multi-connection SQLite checks for M2 task and parallel-join races. */
class M2RuntimeConcurrencyIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private WorkflowClient bootstrap;
    private WorkflowClient first;
    private WorkflowClient second;

    @AfterEach
    void tearDown() throws Exception {
        close(second);
        close(first);
        close(bootstrap);
    }

    @Test
    void concurrentApprovalsOfOneTaskProduceOneHistoryAndOneNextTask() throws Exception {
        ProcessDefinitionDetailDTO definition = serialDefinition();
        String jdbcUrl = initializeDatabase();
        bootstrap = client(jdbcUrl, user("starter", "Starter"), definition);
        ProcessInstanceDTO instance = bootstrap.service.startAndSubmit(startRequest("same-task-start"));
        String reviewTaskId = taskId(bootstrap.jdbcTemplate, instance.getInstanceId(), "review");
        first = client(jdbcUrl, user("manager", "Manager"), definition);
        second = client(jdbcUrl, user("manager", "Manager"), definition);

        List<Boolean> outcomes = concurrently(
                approve(first.service, "same-task-a", reviewTaskId, "manager"),
                approve(second.service, "same-task-b", reviewTaskId, "manager"));

        assertEquals(1, successes(outcomes));
        assertEquals("COMPLETED", status(bootstrap.jdbcTemplate, reviewTaskId));
        assertEquals(2, count(bootstrap.jdbcTemplate, "process_history_task", instance.getInstanceId()));
        assertEquals(1, openTasks(bootstrap.jdbcTemplate, instance.getInstanceId(), "final-review"));
        assertEquals(5, count(bootstrap.jdbcTemplate, "process_callback_log", instance.getInstanceId()));
    }

    @Test
    void concurrentFinalParallelBranchesCreateTheFollowingTaskOnlyOnce() throws Exception {
        ProcessDefinitionDetailDTO definition = parallelDefinition();
        String jdbcUrl = initializeDatabase();
        bootstrap = client(jdbcUrl, user("starter", "Starter"), definition);
        ProcessInstanceDTO instance = bootstrap.service.startAndSubmit(startRequest("parallel-race-start"));
        String branchATaskId = taskId(bootstrap.jdbcTemplate, instance.getInstanceId(), "branch-a");
        String branchBTaskId = taskId(bootstrap.jdbcTemplate, instance.getInstanceId(), "branch-b");
        first = client(jdbcUrl, user("alice", "Alice"), definition);
        second = client(jdbcUrl, user("bob", "Bob"), definition);

        List<Boolean> outcomes = concurrently(
                approve(first.service, "parallel-race-a", branchATaskId, "alice"),
                approve(second.service, "parallel-race-b", branchBTaskId, "bob"));

        assertEquals(2, successes(outcomes), "both concurrent branches must complete successfully");

        assertEquals(1, openTasks(bootstrap.jdbcTemplate, instance.getInstanceId(), "after-join"));
        assertEquals(1, totalTasks(bootstrap.jdbcTemplate, instance.getInstanceId(), "after-join"));
        assertEquals("COMPLETED", bootstrap.jdbcTemplate.queryForObject("SELECT group_status FROM process_task_group "
                + "WHERE instance_id = ?", String.class, instance.getInstanceId()));
        assertEquals(3, count(bootstrap.jdbcTemplate, "process_history_task", instance.getInstanceId()));
        assertEquals(7, count(bootstrap.jdbcTemplate, "process_callback_log", instance.getInstanceId()));
    }

    private List<Boolean> concurrently(Callable<Boolean> firstAction, Callable<Boolean> secondAction) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstFuture = executor.submit(awaitStart(ready, start, firstAction));
            Future<Boolean> secondFuture = executor.submit(awaitStart(ready, start, secondAction));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return Arrays.asList(firstFuture.get(20, TimeUnit.SECONDS), secondFuture.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Boolean> awaitStart(final CountDownLatch ready,
                                         final CountDownLatch start,
                                         final Callable<Boolean> action) {
        return new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return action.call();
            }
        };
    }

    private Callable<Boolean> approve(final DefaultProcessRuntimeService service,
                                      final String operationId,
                                      final String taskId,
                                      final String userId) {
        return new Callable<Boolean>() {
            @Override
            public Boolean call() {
                try {
                    service.approve(approveRequest(operationId, taskId, userId));
                    return Boolean.TRUE;
                } catch (RuntimeException ex) {
                    return Boolean.FALSE;
                }
            }
        };
    }

    private int successes(List<Boolean> outcomes) {
        int successes = 0;
        for (Boolean outcome : outcomes) {
            if (Boolean.TRUE.equals(outcome)) {
                successes++;
            }
        }
        return successes;
    }

    private String initializeDatabase() throws Exception {
        String file = temporaryDirectory.resolve("runtime-concurrency.db").toAbsolutePath().toString().replace('\\', '/');
        String jdbcUrl = "jdbc:sqlite:" + file;
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA busy_timeout = 10000");
            }
            SchemaTestSupport.executeSchema(connection);
            new JdbcTemplate(new ExistingConnectionDataSource(connection)).update("INSERT INTO process_definition "
                            + "(id, process_code, process_name, system_code, version, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                    "definition-concurrency", "concurrency-flow", "Concurrency Flow", "test", 1, "tester");
        }
        return jdbcUrl;
    }

    private WorkflowClient client(String jdbcUrl, UserContext currentUser, ProcessDefinitionDetailDTO definition) throws Exception {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 10000");
        }
        ExistingConnectionDataSource dataSource = new ExistingConnectionDataSource(connection);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        ActiveTaskRepository activeTaskRepository = new ActiveTaskRepository(jdbcTemplate);
        ProcessInstanceRepository instanceRepository = new ProcessInstanceRepository(jdbcTemplate);
        TaskGroupRepository taskGroupRepository = new TaskGroupRepository(jdbcTemplate);
        HistoryTaskRepository historyTaskRepository = new HistoryTaskRepository(jdbcTemplate);
        ProcessHistoryTaskRepository processHistoryTaskRepository = new ProcessHistoryTaskRepository(jdbcTemplate);
        ProcessOperationRecordRepository operationRecordRepository = new ProcessOperationRecordRepository(jdbcTemplate);
        ProcessCallbackLogRepository callbackLogRepository = new ProcessCallbackLogRepository(jdbcTemplate);
        RuntimeRequestValidator requestValidator = new RuntimeRequestValidator(new FixedCurrentUserProvider(currentUser));
        ApproverResolver resolver = request -> Collections.singletonList(new UserDTO(
                userForNode(request.getNodeCode()), userForNode(request.getNodeCode())));
        RuntimeNodeAdvancer nodeAdvancer = new RuntimeNodeAdvancer(activeTaskRepository, taskGroupRepository,
                instanceRepository, requestValidator, resolver, new SimpleConditionExpressionEvaluator(),
                new ApproverResolveRequestFactory(new RuntimeNodeConfigReader()));
        RuntimeDefinitionLoader definitionLoader = mock(RuntimeDefinitionLoader.class);
        when(definitionLoader.loadForStart("concurrency-flow")).thenReturn(definition);
        when(definitionLoader.loadForInstance(any())).thenReturn(definition);
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentTemplateCheckResult attachmentResult = new AttachmentTemplateCheckResult();
        attachmentResult.setPassed(true);
        when(attachmentService.checkRequiredAttachments(any())).thenReturn(attachmentResult);
        CallbackService callbackService = new DefaultCallbackService(callbackLogRepository, new CallbackLogMapper(),
                new CallbackOutboxService(callbackLogRepository, new CallbackLogMapper()));
        DefaultProcessRuntimeService service = new DefaultProcessRuntimeService(instanceRepository, activeTaskRepository,
                historyTaskRepository, definitionLoader, requestValidator,
                new RuntimeOperationExecutor(new OperationIdempotencyService(operationRecordRepository)),
                nodeAdvancer, attachmentService, callbackService,
                new RuntimeStateValidator(activeTaskRepository, instanceRepository, taskGroupRepository),
                new HistoryTaskWriter(processHistoryTaskRepository), new RuntimeTransactionExecutor(transactionManager));
        return new WorkflowClient(connection, jdbcTemplate, service);
    }

    private ProcessDefinitionDetailDTO serialDefinition() {
        return definition(Arrays.asList(node("start", NodeTypeEnum.START, null),
                        node("apply", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.STARTER),
                        node("review", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.USER),
                        node("final-review", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.USER),
                        node("end", NodeTypeEnum.END, null)),
                Arrays.asList(edge("start-apply", "start", "apply"), edge("apply-review", "apply", "review"),
                        edge("review-final", "review", "final-review"), edge("final-end", "final-review", "end")));
    }

    private ProcessDefinitionDetailDTO parallelDefinition() {
        ProcessNodeDTO split = node("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY, null);
        split.setPairedGatewayCode("join");
        ProcessNodeDTO join = node("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY, null);
        join.setPairedGatewayCode("split");
        return definition(Arrays.asList(node("start", NodeTypeEnum.START, null),
                        node("apply", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.STARTER), split,
                        node("branch-a", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.USER),
                        node("branch-b", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.USER), join,
                        node("after-join", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.USER),
                        node("end", NodeTypeEnum.END, null)),
                Arrays.asList(edge("start-apply", "start", "apply"), edge("apply-split", "apply", "split"),
                        edge("split-a", "split", "branch-a"), edge("split-b", "split", "branch-b"),
                        edge("a-join", "branch-a", "join"), edge("b-join", "branch-b", "join"),
                        edge("join-after", "join", "after-join"), edge("after-end", "after-join", "end")));
    }

    private ProcessDefinitionDetailDTO definition(List<ProcessNodeDTO> nodes, List<ProcessEdgeDTO> edges) {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setId("definition-concurrency");
        definition.setProcessCode("concurrency-flow");
        definition.setProcessName("Concurrency Flow");
        definition.setVersion(Integer.valueOf(1));
        definition.setNodes(nodes);
        definition.setEdges(edges);
        return definition;
    }

    private ProcessNodeDTO node(String code, NodeTypeEnum type, ApproverRuleTypeEnum rule) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeName(code);
        node.setNodeType(type);
        node.setApproverRuleType(rule);
        if (NodeTypeEnum.USER_TASK.equals(type)) {
            node.setMultiInstanceMode(MultiInstanceModeEnum.SINGLE);
            node.setApproverRuleConfig(ApproverRuleTypeEnum.STARTER.equals(rule) ? null : "{\"user\":\"fixed\"}");
        }
        return node;
    }

    private ProcessEdgeDTO edge(String code, String source, String target) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode(code);
        edge.setSourceNodeCode(source);
        edge.setTargetNodeCode(target);
        return edge;
    }

    private StartProcessRequest startRequest(String operationId) {
        StartProcessRequest request = new StartProcessRequest();
        request.setOperationId(operationId);
        request.setProcessCode("concurrency-flow");
        request.setInstanceTitle("Concurrency test");
        request.setStarterUserId("starter");
        request.setStarterDeptId("dept-1");
        request.setVariables(new LinkedHashMap<String, Object>());
        return request;
    }

    private SubmitTaskRequest submitRequest(String operationId, String taskId, String userId) {
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setOperationId(operationId);
        request.setTaskId(taskId);
        request.setExpectedTaskVersion(Long.valueOf(0L));
        request.setOperatorUserId(userId);
        request.setComment("submitted");
        return request;
    }

    private ApproveTaskRequest approveRequest(String operationId, String taskId, String userId) {
        ApproveTaskRequest request = new ApproveTaskRequest();
        request.setOperationId(operationId);
        request.setTaskId(taskId);
        request.setExpectedTaskVersion(Long.valueOf(0L));
        request.setOperatorUserId(userId);
        request.setComment("approved");
        return request;
    }

    private String userForNode(String nodeCode) {
        if ("apply".equals(nodeCode)) {
            return "starter";
        }
        if ("branch-a".equals(nodeCode)) {
            return "alice";
        }
        if ("branch-b".equals(nodeCode)) {
            return "bob";
        }
        return "manager";
    }

    private int count(JdbcTemplate jdbcTemplate, String tableName, String instanceId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE instance_id = ?",
                Integer.class, instanceId).intValue();
    }

    private int openTasks(JdbcTemplate jdbcTemplate, String instanceId, String nodeCode) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_active_task WHERE instance_id = ? "
                + "AND node_code = ? AND task_status = 'ACTIVE'", Integer.class, instanceId, nodeCode).intValue();
    }

    private int totalTasks(JdbcTemplate jdbcTemplate, String instanceId, String nodeCode) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_active_task WHERE instance_id = ? "
                + "AND node_code = ?", Integer.class, instanceId, nodeCode).intValue();
    }

    private String taskId(JdbcTemplate jdbcTemplate, String instanceId, String nodeCode) {
        return jdbcTemplate.queryForObject("SELECT id FROM process_active_task WHERE instance_id = ? "
                + "AND node_code = ? AND task_status = 'ACTIVE'", String.class, instanceId, nodeCode);
    }

    private String status(JdbcTemplate jdbcTemplate, String taskId) {
        return jdbcTemplate.queryForObject("SELECT task_status FROM process_active_task WHERE id = ?", String.class, taskId);
    }

    private UserContext user(String id, String name) {
        return new UserContext(id, name, "dept-1", "Department");
    }

    private void close(WorkflowClient client) throws SQLException {
        if (client != null) {
            client.connection.close();
        }
    }

    private static final class FixedCurrentUserProvider implements CurrentUserProvider {
        private final UserContext currentUser;

        private FixedCurrentUserProvider(UserContext currentUser) {
            this.currentUser = currentUser;
        }

        @Override
        public UserContext getCurrentUser() {
            return currentUser;
        }
    }

    private static final class WorkflowClient {
        private final Connection connection;
        private final JdbcTemplate jdbcTemplate;
        private final DefaultProcessRuntimeService service;

        private WorkflowClient(Connection connection, JdbcTemplate jdbcTemplate, DefaultProcessRuntimeService service) {
            this.connection = connection;
            this.jdbcTemplate = jdbcTemplate;
            this.service = service;
        }
    }
}
