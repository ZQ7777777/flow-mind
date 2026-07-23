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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real SQLite service closed loops for M2 gateway behaviour. */
class M2GatewayWorkflowIntegrationTest {

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private MutableCurrentUserProvider currentUserProvider;
    private RuntimeDefinitionLoader definitionLoader;
    private DefaultProcessRuntimeService runtimeService;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaTestSupport.executeSchema(connection);
        ExistingConnectionDataSource dataSource = new ExistingConnectionDataSource(connection);
        jdbcTemplate = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        ActiveTaskRepository activeTaskRepository = new ActiveTaskRepository(jdbcTemplate);
        ProcessInstanceRepository instanceRepository = new ProcessInstanceRepository(jdbcTemplate);
        TaskGroupRepository taskGroupRepository = new TaskGroupRepository(jdbcTemplate);
        HistoryTaskRepository historyTaskRepository = new HistoryTaskRepository(jdbcTemplate);
        ProcessHistoryTaskRepository processHistoryTaskRepository = new ProcessHistoryTaskRepository(jdbcTemplate);
        ProcessOperationRecordRepository operationRecordRepository = new ProcessOperationRecordRepository(jdbcTemplate);
        ProcessCallbackLogRepository callbackLogRepository = new ProcessCallbackLogRepository(jdbcTemplate);
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                "definition-gateway", "gateway-flow", "Gateway Flow", "test", 1, "tester");

        currentUserProvider = new MutableCurrentUserProvider(user("starter", "Starter"));
        RuntimeRequestValidator requestValidator = new RuntimeRequestValidator(currentUserProvider);
        ApproverResolver resolver = request -> Collections.singletonList(new UserDTO(
                userForNode(request.getNodeCode()), userForNode(request.getNodeCode())));
        RuntimeNodeAdvancer nodeAdvancer = new RuntimeNodeAdvancer(activeTaskRepository, taskGroupRepository,
                instanceRepository, requestValidator, resolver, new SimpleConditionExpressionEvaluator(),
                new ApproverResolveRequestFactory(new RuntimeNodeConfigReader()));
        definitionLoader = mock(RuntimeDefinitionLoader.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentTemplateCheckResult attachmentResult = new AttachmentTemplateCheckResult();
        attachmentResult.setPassed(true);
        when(attachmentService.checkRequiredAttachments(any())).thenReturn(attachmentResult);
        CallbackService callbackService = new DefaultCallbackService(callbackLogRepository, new CallbackLogMapper(),
                new CallbackOutboxService(callbackLogRepository, new CallbackLogMapper()));
        runtimeService = new DefaultProcessRuntimeService(instanceRepository, activeTaskRepository,
                historyTaskRepository, definitionLoader, requestValidator,
                new RuntimeOperationExecutor(new OperationIdempotencyService(operationRecordRepository)),
                nodeAdvancer, attachmentService, callbackService,
                new RuntimeStateValidator(activeTaskRepository, instanceRepository, taskGroupRepository),
                new HistoryTaskWriter(processHistoryTaskRepository), new RuntimeTransactionExecutor(transactionManager));
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void conditionGatewayRoutesHighAndLowAmountThroughRealService() {
        install(conditionDefinition());

        ProcessInstanceDTO high = runtimeService.startAndSubmit(startRequest("condition-high-start", 200));
        currentUserProvider.setCurrent(user("starter", "Starter"));
        runtimeService.submitTask(submitRequest("condition-high-submit", taskId(high.getInstanceId(), "apply"), "starter"));
        assertEquals(1, openTasks(high.getInstanceId(), "high-review"));
        assertEquals(0, openTasks(high.getInstanceId(), "low-review"));
        currentUserProvider.setCurrent(user("manager", "Manager"));
        runtimeService.approve(approveRequest("condition-high-approve", taskId(high.getInstanceId(), "high-review"), "manager"));
        assertCompleted(high.getInstanceId());

        currentUserProvider.setCurrent(user("starter", "Starter"));
        ProcessInstanceDTO low = runtimeService.startAndSubmit(startRequest("condition-low-start", 20));
        runtimeService.submitTask(submitRequest("condition-low-submit", taskId(low.getInstanceId(), "apply"), "starter"));
        assertEquals(0, openTasks(low.getInstanceId(), "high-review"));
        assertEquals(1, openTasks(low.getInstanceId(), "low-review"));
        currentUserProvider.setCurrent(user("junior", "Junior"));
        runtimeService.approve(approveRequest("condition-low-approve", taskId(low.getInstanceId(), "low-review"), "junior"));
        assertCompleted(low.getInstanceId());
    }

    @Test
    void parallelJoinCreatesExactlyOneFollowingTaskAfterLastBranch() {
        install(parallelDefinition());
        ProcessInstanceDTO started = runtimeService.startAndSubmit(startRequest("parallel-start", 1));
        currentUserProvider.setCurrent(user("starter", "Starter"));
        runtimeService.submitTask(submitRequest("parallel-submit", taskId(started.getInstanceId(), "apply"), "starter"));

        assertEquals(1, openTasks(started.getInstanceId(), "branch-a"));
        assertEquals(1, openTasks(started.getInstanceId(), "branch-b"));
        currentUserProvider.setCurrent(user("alice", "Alice"));
        runtimeService.approve(approveRequest("parallel-a", taskId(started.getInstanceId(), "branch-a"), "alice"));
        assertEquals(0, openTasks(started.getInstanceId(), "after-join"));
        assertEquals("ACTIVE", jdbcTemplate.queryForObject("SELECT group_status FROM process_task_group "
                + "WHERE instance_id = ?", String.class, started.getInstanceId()));

        currentUserProvider.setCurrent(user("bob", "Bob"));
        runtimeService.approve(approveRequest("parallel-b", taskId(started.getInstanceId(), "branch-b"), "bob"));
        assertEquals(1, openTasks(started.getInstanceId(), "after-join"));
        assertEquals("COMPLETED", jdbcTemplate.queryForObject("SELECT group_status FROM process_task_group "
                + "WHERE instance_id = ?", String.class, started.getInstanceId()));

        currentUserProvider.setCurrent(user("manager", "Manager"));
        runtimeService.approve(approveRequest("parallel-after", taskId(started.getInstanceId(), "after-join"), "manager"));
        assertCompleted(started.getInstanceId());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_active_task WHERE instance_id = ? "
                + "AND node_code = 'after-join'", Integer.class, started.getInstanceId()).intValue());
    }

    private void install(ProcessDefinitionDetailDTO definition) {
        when(definitionLoader.loadForStart("gateway-flow")).thenReturn(definition);
        when(definitionLoader.loadForInstance(any())).thenReturn(definition);
    }

    private void assertCompleted(String instanceId) {
        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "SELECT instance_status FROM process_instance WHERE id = ?", String.class, instanceId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_active_task WHERE instance_id = ? "
                + "AND task_status IN ('ACTIVE', 'CLAIMED')", Integer.class, instanceId).intValue());
    }

    private int openTasks(String instanceId, String nodeCode) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_active_task WHERE instance_id = ? "
                + "AND node_code = ? AND task_status = 'ACTIVE'", Integer.class, instanceId, nodeCode).intValue();
    }

    private String taskId(String instanceId, String nodeCode) {
        return jdbcTemplate.queryForObject("SELECT id FROM process_active_task WHERE instance_id = ? "
                + "AND node_code = ? AND task_status = 'ACTIVE'", String.class, instanceId, nodeCode);
    }

    private ProcessDefinitionDetailDTO conditionDefinition() {
        return definition(Arrays.asList(node("start", NodeTypeEnum.START, null),
                        node("apply", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.STARTER),
                        node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY, null),
                        node("high-review", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.USER),
                        node("low-review", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.USER),
                        node("end", NodeTypeEnum.END, null)),
                Arrays.asList(edge("start-apply", "start", "apply"), edge("apply-route", "apply", "route"),
                        conditionalEdge("route-high", "route", "high-review", "amount >= 100", false),
                        conditionalEdge("route-low", "route", "low-review", null, true),
                        edge("high-end", "high-review", "end"), edge("low-end", "low-review", "end")));
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
        definition.setId("definition-gateway");
        definition.setProcessCode("gateway-flow");
        definition.setProcessName("Gateway Flow");
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
        return conditionalEdge(code, source, target, null, false);
    }

    private ProcessEdgeDTO conditionalEdge(String code, String source, String target, String expression, boolean defaultEdge) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode(code);
        edge.setSourceNodeCode(source);
        edge.setTargetNodeCode(target);
        edge.setConditionExpression(expression);
        edge.setDefaultEdge(Boolean.valueOf(defaultEdge));
        return edge;
    }

    private StartProcessRequest startRequest(String operationId, int amount) {
        StartProcessRequest request = new StartProcessRequest();
        request.setOperationId(operationId);
        request.setProcessCode("gateway-flow");
        request.setInstanceTitle("Gateway test");
        request.setStarterUserId("starter");
        request.setStarterDeptId("dept-1");
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("amount", Integer.valueOf(amount));
        request.setVariables(variables);
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
        if ("high-review".equals(nodeCode) || "after-join".equals(nodeCode)) {
            return "manager";
        }
        if ("low-review".equals(nodeCode)) {
            return "junior";
        }
        if ("branch-a".equals(nodeCode)) {
            return "alice";
        }
        return "bob";
    }

    private UserContext user(String id, String name) {
        return new UserContext(id, name, "dept-1", "Department");
    }

    private static final class MutableCurrentUserProvider implements CurrentUserProvider {
        private UserContext current;

        private MutableCurrentUserProvider(UserContext current) {
            this.current = current;
        }

        @Override
        public UserContext getCurrentUser() {
            return current;
        }

        private void setCurrent(UserContext current) {
            this.current = current;
        }
    }
}
