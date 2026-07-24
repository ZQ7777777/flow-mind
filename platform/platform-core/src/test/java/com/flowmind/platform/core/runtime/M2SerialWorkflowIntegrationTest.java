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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** M2 串行审批的真实 SQLite 闭环验收。 */
class M2SerialWorkflowIntegrationTest {

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private MutableCurrentUserProvider currentUserProvider;
    private RuntimeDefinitionLoader definitionLoader;
    private DefaultProcessRuntimeService runtimeService;
    private ActiveTaskRepository activeTaskRepository;
    private ApproverResolver approverResolver;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaTestSupport.executeSchema(connection);
        ExistingConnectionDataSource dataSource = new ExistingConnectionDataSource(connection);
        jdbcTemplate = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        activeTaskRepository = spy(new ActiveTaskRepository(jdbcTemplate));
        ProcessInstanceRepository instanceRepository = new ProcessInstanceRepository(jdbcTemplate);
        TaskGroupRepository taskGroupRepository = new TaskGroupRepository(jdbcTemplate);
        HistoryTaskRepository historyTaskRepository = new HistoryTaskRepository(jdbcTemplate);
        ProcessHistoryTaskRepository processHistoryTaskRepository = new ProcessHistoryTaskRepository(jdbcTemplate);
        ProcessOperationRecordRepository operationRecordRepository = new ProcessOperationRecordRepository(jdbcTemplate);
        ProcessCallbackLogRepository callbackLogRepository = new ProcessCallbackLogRepository(jdbcTemplate);
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                "definition-serial", "expense", "Expense", "test", 1, "tester");

        currentUserProvider = new MutableCurrentUserProvider(user("starter", "Starter"));
        RuntimeRequestValidator requestValidator = new RuntimeRequestValidator(currentUserProvider);
        approverResolver = mock(ApproverResolver.class);
        when(approverResolver.resolveApprovers(any())).thenAnswer(invocation -> {
            com.flowmind.platform.api.request.ApproverResolveRequest request = invocation.getArgument(0);
            return Collections.singletonList(new UserDTO(approverFor(request.getNodeCode()),
                    approverFor(request.getNodeCode())));
        });
        RuntimeNodeAdvancer nodeAdvancer = new RuntimeNodeAdvancer(activeTaskRepository, taskGroupRepository,
                instanceRepository, requestValidator, approverResolver, new SimpleConditionExpressionEvaluator(),
                new ApproverResolveRequestFactory(new RuntimeNodeConfigReader()));
        definitionLoader = mock(RuntimeDefinitionLoader.class);
        ProcessDefinitionDetailDTO definition = serialDefinition();
        when(definitionLoader.loadForStart("expense")).thenReturn(definition);
        when(definitionLoader.loadForInstance(any())).thenReturn(definition);
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
    void startSubmitAndTwoApprovalsCompleteSerialWorkflowWithHistoryVariablesAndCallbacks() {
        StartProcessRequest start = new StartProcessRequest();
        start.setOperationId("op-start");
        start.setProcessCode("expense");
        start.setInstanceTitle("Expense #1");
        start.setStarterUserId("starter");
        start.setStarterDeptId("dept-1");
        start.setVariables(new LinkedHashMap<String, Object>());
        start.getVariables().put("amount", Integer.valueOf(100));
        start.getVariables().put("memo", "hotel");
        ProcessInstanceDTO started = runtimeService.startAndSubmit(start);

        currentUserProvider.setCurrent(user("manager", "Manager"));
        String managerTaskId = openTaskId("manager-review");
        runtimeService.approve(approveRequest("op-manager", managerTaskId, "manager"));

        currentUserProvider.setCurrent(user("finance", "Finance"));
        String financeTaskId = openTaskId("finance-review");
        runtimeService.approve(approveRequest("op-finance", financeTaskId, "finance"));

        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "SELECT instance_status FROM process_instance WHERE id = ?", String.class, started.getInstanceId()));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_active_task WHERE instance_id = ? "
                + "AND task_status IN ('ACTIVE', 'CLAIMED')", Integer.class, started.getInstanceId()).intValue());
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_history_task WHERE instance_id = ?",
                Integer.class, started.getInstanceId()).intValue());
        assertEquals(7, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_callback_log WHERE instance_id = ?",
                Integer.class, started.getInstanceId()).intValue());
        assertEquals("hotel", runtimeService.getInstance(started.getInstanceId()).getVariables().get("memo"));
        assertEquals(2, runtimeService.getInstance(started.getInstanceId()).getComments().size());
        assertTrue(runtimeService.getInstance(started.getInstanceId()).getActiveTasks().isEmpty());
    }

    @Test
    void startAndSubmitAdvancesToManagerAndDoesNotCompleteWhenApprovalResolutionFails() {
        ProcessInstanceDTO started = runtimeService.startAndSubmit(startRequest("op-order-start"));
        String managerTaskId = openTaskId("manager-review");
        clearInvocations(approverResolver, activeTaskRepository);
        doThrow(new IllegalStateException("resolver unavailable"))
                .when(approverResolver).resolveApprovers(any());
        currentUserProvider.setCurrent(user("manager", "Manager"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeStateException.class,
                () -> runtimeService.approve(approveRequest("op-order-failure", managerTaskId, "manager")));

        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT task_status FROM process_active_task WHERE id = ?", String.class, managerTaskId));
        org.mockito.Mockito.verify(activeTaskRepository, org.mockito.Mockito.never()).complete(managerTaskId, 0L);
    }

    private String openTaskId(String nodeCode) {
        return jdbcTemplate.queryForObject("SELECT id FROM process_active_task WHERE node_code = ? AND task_status = 'ACTIVE'",
                String.class, nodeCode);
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

    private StartProcessRequest startRequest(String operationId) {
        StartProcessRequest request = new StartProcessRequest();
        request.setOperationId(operationId);
        request.setProcessCode("expense");
        request.setInstanceTitle("Expense #1");
        request.setStarterUserId("starter");
        request.setStarterDeptId("dept-1");
        request.setVariables(new LinkedHashMap<String, Object>());
        request.getVariables().put("amount", Integer.valueOf(100));
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

    private ProcessDefinitionDetailDTO serialDefinition() {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setId("definition-serial");
        definition.setProcessCode("expense");
        definition.setProcessName("Expense");
        definition.setVersion(Integer.valueOf(1));
        definition.setNodes(Arrays.asList(node("start", NodeTypeEnum.START, null),
                node("apply", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.STARTER),
                node("manager-review", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.USER),
                node("finance-review", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.USER),
                node("end", NodeTypeEnum.END, null)));
        definition.setEdges(Arrays.asList(edge("start-apply", "start", "apply"),
                edge("apply-manager", "apply", "manager-review"),
                edge("manager-finance", "manager-review", "finance-review"),
                edge("finance-end", "finance-review", "end")));
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

    private String approverFor(String nodeCode) {
        if ("apply".equals(nodeCode)) {
            return "starter";
        }
        if ("manager-review".equals(nodeCode)) {
            return "manager";
        }
        return "finance";
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
