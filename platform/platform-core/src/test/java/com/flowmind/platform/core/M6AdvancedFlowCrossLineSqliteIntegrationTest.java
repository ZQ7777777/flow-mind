package com.flowmind.platform.core;

import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.request.ApproveTaskRequest;
import com.flowmind.platform.api.request.CreateProcessDefinitionRequest;
import com.flowmind.platform.api.request.DefinitionOperationRequest;
import com.flowmind.platform.api.request.SaveProcessGraphRequest;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.request.TimeoutScanRequest;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.spi.WorkflowCallbackHandler;
import com.flowmind.platform.core.audit.DefaultAuditLogWriter;
import com.flowmind.platform.core.callback.CallbackDispatchService;
import com.flowmind.platform.core.callback.CallbackFailureAlertService;
import com.flowmind.platform.core.definition.OperationIdempotencyService;
import com.flowmind.platform.core.monitor.ActionExceptionAlertWriter;
import com.flowmind.platform.core.monitor.DefaultProcessMonitorService;
import com.flowmind.platform.core.monitor.ReminderDeduplicationGuard;
import com.flowmind.platform.core.monitor.ReminderPolicyReader;
import com.flowmind.platform.core.monitor.TimeoutPolicyReader;
import com.flowmind.platform.core.runtime.AdminPermissionGuard;
import com.flowmind.platform.core.runtime.RuntimeOperationExecutor;
import com.flowmind.platform.core.runtime.RuntimeRequestValidator;
import com.flowmind.platform.core.runtime.RuntimeTransactionExecutor;
import com.flowmind.platform.mock.RecordingMessagePublisher;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.AlertRecordRepository;
import com.flowmind.platform.persistence.repository.ProcessAuditLogRepository;
import com.flowmind.platform.persistence.repository.ProcessCallbackLogRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.ProcessNodeRepository;
import com.flowmind.platform.persistence.repository.ProcessOperationRecordRepository;
import com.flowmind.platform.persistence.repository.ReminderRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-line M6 acceptance using production definition/runtime logic and SQLite persistence.
 *
 * @author FlowMind
 * @since 2026-07-29
 */
@SpringBootTest(classes = M0M3CrossStageSpringBootIntegrationTest.TestApplication.class)
@ActiveProfiles("m0m3-cross-stage")
class M6AdvancedFlowCrossLineSqliteIntegrationTest {

    private static final String PROCESS_CODE = "m6-cross-line";
    private static final String OPERATION_PREFIX = "m6-cross-";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ProcessDefinitionService definitionService;
    @Autowired
    private ProcessRuntimeService runtimeService;
    @Autowired
    private M0M3CrossStageSpringBootIntegrationTest.MutableCurrentUserProvider currentUserProvider;
    @Autowired
    private ActiveTaskRepository activeTaskRepository;
    @Autowired
    private ProcessInstanceRepository instanceRepository;
    @Autowired
    private ProcessNodeRepository processNodeRepository;
    @Autowired
    private ProcessOperationRecordRepository operationRecordRepository;
    @Autowired
    private ProcessCallbackLogRepository callbackLogRepository;
    @Autowired
    private RuntimeRequestValidator requestValidator;
    @Autowired
    private RuntimeTransactionExecutor transactionExecutor;

    @BeforeEach
    void cleanOwnedRows() {
        String instances = "SELECT id FROM process_instance WHERE process_code = ?";
        deleteByInstances("process_active_task", instances);
        deleteByInstances("process_task_group", instances);
        deleteByInstances("process_history_task", instances);
        deleteByInstances("process_reminder_record", instances);
        deleteByInstances("process_alert_record", instances);
        deleteByInstances("process_callback_log", instances);
        deleteByInstances("process_audit_log", instances);
        jdbcTemplate.update("DELETE FROM process_instance WHERE process_code = ?", PROCESS_CODE);

        String definitions = "SELECT id FROM process_definition WHERE process_code = ?";
        jdbcTemplate.update("DELETE FROM process_edge WHERE definition_id IN (" + definitions + ")", PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_node WHERE definition_id IN (" + definitions + ")", PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_operation_record WHERE operation_id LIKE ?",
                OPERATION_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM process_definition WHERE process_code = ?", PROCESS_CODE);
        currentUserProvider.setCurrent(user("starter"));
    }

    @AfterEach
    void removeOwnedRows() {
        cleanOwnedRows();
    }

    @Test
    void advancedFlowCrossesConditionOrSignCountersignParallelTimeoutAndCallbackGovernance() {
        ProcessDefinitionDTO definition = definitionService.createDefinition(createRequest());
        definitionService.saveGraph(definition.getId(), graphRequest());
        definitionService.publish(lifecycle(definition.getId(), OPERATION_PREFIX + "publish"));
        definitionService.activate(lifecycle(definition.getId(), OPERATION_PREFIX + "activate"));

        ProcessInstanceDTO instance = runtimeService.startAndSubmit(startRequest());
        String instanceId = instance.getInstanceId();

        assertEquals(2, openTaskCount(instanceId, "or-review"));
        approve("or-a", taskId(instanceId, "or-review", "or-a"), OPERATION_PREFIX + "or");
        assertEquals(0, openTaskCount(instanceId, "or-review"));
        assertEquals(1, statusCount(instanceId, "or-review", "CANCELED"));

        assertEquals(2, openTaskCount(instanceId, "counter-review"));
        approve("counter-a", taskId(instanceId, "counter-review", "counter-a"),
                OPERATION_PREFIX + "counter-a");
        assertEquals(0, openTaskCount(instanceId, "branch-a"));
        approve("counter-b", taskId(instanceId, "counter-review", "counter-b"),
                OPERATION_PREFIX + "counter-b");

        assertEquals(1, openTaskCount(instanceId, "branch-a"));
        assertEquals(1, openTaskCount(instanceId, "branch-b"));
        approve("branch-a", taskId(instanceId, "branch-a", "branch-a"), OPERATION_PREFIX + "branch-a");
        assertEquals(0, openTaskCount(instanceId, "timed-review"));
        approve("branch-b", taskId(instanceId, "branch-b", "branch-b"), OPERATION_PREFIX + "branch-b");

        String timedTaskId = taskId(instanceId, "timed-review", "timed-review");
        LocalDateTime dueAt = jdbcTemplate.queryForObject(
                "SELECT due_at FROM process_active_task WHERE id = ?",
                (resultSet, rowNum) -> resultSet.getTimestamp(1).toLocalDateTime(), timedTaskId);
        assertNotNull(dueAt);

        DefaultProcessMonitorService monitor = monitorService();
        TimeoutScanRequest scan = new TimeoutScanRequest();
        scan.setDryRun(Boolean.FALSE);
        scan.setScanAt(dueAt.plusMinutes(1));
        scan.setLimit(Integer.valueOf(10));
        scan.setOperatorUserId("admin");
        monitor.scanTimeoutTasks(scan);
        assertEquals(1, alertCount(instanceId, "TASK_TIMEOUT"));

        approve("timed-review", timedTaskId, OPERATION_PREFIX + "timed");
        assertEquals(InstanceStatusEnum.COMPLETED, runtimeService.getInstance(instanceId).getInstanceStatus());
        assertEquals(3, taskGroupCount(instanceId));
        assertEquals(3, completedTaskGroupCount(instanceId));

        int pendingCallbacks = callbackStatusCount(instanceId, "PENDING");
        jdbcTemplate.update("UPDATE process_callback_log SET created_at = '0001-01-01 00:00:00' "
                + "WHERE instance_id = ? AND callback_status = 'PENDING'", instanceId);
        CallbackDispatchService dispatcher = new CallbackDispatchService(callbackLogRepository,
                new WorkflowCallbackHandler() {
                    @Override
                    public void handle(com.flowmind.platform.api.dto.WorkflowEvent event) {
                        throw new IllegalStateException("simulated downstream outage");
                    }
                },
                new CallbackFailureAlertService(new AlertRecordRepository(jdbcTemplate)));
        assertEquals(0, dispatcher.dispatchPending(pendingCallbacks));
        assertTrue(alertCount(instanceId, "CALLBACK_FAILED") > 0);
        assertEquals(0, callbackStatusCount(instanceId, "PENDING"));
        assertTrue(callbackStatusCount(instanceId, "FAILED") > 0);
    }

    private DefaultProcessMonitorService monitorService() {
        ReminderRecordRepository reminders = new ReminderRecordRepository(jdbcTemplate);
        AlertRecordRepository alerts = new AlertRecordRepository(jdbcTemplate);
        return new DefaultProcessMonitorService(activeTaskRepository, instanceRepository, reminders, alerts,
                requestValidator,
                new RuntimeOperationExecutor(new OperationIdempotencyService(operationRecordRepository)),
                transactionExecutor,
                new DefaultAuditLogWriter(new ProcessAuditLogRepository(jdbcTemplate)),
                new RecordingMessagePublisher(),
                processNodeRepository,
                new TimeoutPolicyReader(),
                new ReminderPolicyReader(),
                new ReminderDeduplicationGuard(reminders),
                null,
                new ActionExceptionAlertWriter(alerts),
                new AdminPermissionGuard());
    }

    private void approve(String userId, String taskId, String operationId) {
        currentUserProvider.setCurrent(user(userId));
        ApproveTaskRequest request = new ApproveTaskRequest();
        request.setOperationId(operationId);
        request.setTaskId(taskId);
        request.setExpectedTaskVersion(Long.valueOf(0L));
        request.setOperatorUserId(userId);
        request.setComment("M6 cross-line approval");
        runtimeService.approve(request);
    }

    private CreateProcessDefinitionRequest createRequest() {
        CreateProcessDefinitionRequest request = new CreateProcessDefinitionRequest();
        request.setOperationId(OPERATION_PREFIX + "create");
        request.setOperatorUserId("operator");
        request.setProcessCode(PROCESS_CODE);
        request.setProcessName("M6 Cross-line Flow");
        request.setSystemCode("test");
        return request;
    }

    private SaveProcessGraphRequest graphRequest() {
        ProcessNodeDTO split = node("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY, null);
        split.setPairedGatewayCode("join");
        ProcessNodeDTO join = node("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY, null);
        join.setPairedGatewayCode("split");
        ProcessNodeDTO orReview = node("or-review", NodeTypeEnum.USER_TASK, MultiInstanceModeEnum.OR_SIGN);
        ProcessNodeDTO counterReview =
                node("counter-review", NodeTypeEnum.USER_TASK, MultiInstanceModeEnum.COUNTERSIGN);
        ProcessNodeDTO timedReview = node("timed-review", NodeTypeEnum.USER_TASK, MultiInstanceModeEnum.SINGLE);
        timedReview.setTimeoutConfig("{\"enabled\":true,\"durationMinutes\":0,"
                + "\"severity\":\"HIGH\",\"action\":\"ALERT\"}");

        SaveProcessGraphRequest request = new SaveProcessGraphRequest();
        request.setOperationId(OPERATION_PREFIX + "graph");
        request.setOperatorUserId("operator");
        request.setNodes(Arrays.asList(
                node("start", NodeTypeEnum.START, null),
                node("apply", NodeTypeEnum.USER_TASK, MultiInstanceModeEnum.SINGLE),
                node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY, null),
                orReview,
                counterReview,
                split,
                node("branch-a", NodeTypeEnum.USER_TASK, MultiInstanceModeEnum.SINGLE),
                node("branch-b", NodeTypeEnum.USER_TASK, MultiInstanceModeEnum.SINGLE),
                join,
                timedReview,
                node("end", NodeTypeEnum.END, null)));
        request.setEdges(Arrays.asList(
                edge("start-apply", "start", "apply"),
                edge("apply-route", "apply", "route"),
                conditionalEdge("route-or", "route", "or-review", "amount >= 100", false),
                conditionalEdge("route-end", "route", "end", null, true),
                edge("or-counter", "or-review", "counter-review"),
                edge("counter-split", "counter-review", "split"),
                edge("split-a", "split", "branch-a"),
                edge("split-b", "split", "branch-b"),
                edge("a-join", "branch-a", "join"),
                edge("b-join", "branch-b", "join"),
                edge("join-timed", "join", "timed-review"),
                edge("timed-end", "timed-review", "end")));
        request.setFormFields(Collections.emptyList());
        request.setAttachmentConfigs(Collections.emptyList());
        return request;
    }

    private ProcessNodeDTO node(String code, NodeTypeEnum type, MultiInstanceModeEnum mode) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeName(code);
        node.setNodeType(type);
        node.setSortOrder(Integer.valueOf(10));
        if (NodeTypeEnum.USER_TASK.equals(type)) {
            node.setApproverRuleType("apply".equals(code)
                    ? ApproverRuleTypeEnum.STARTER : ApproverRuleTypeEnum.USER);
            node.setApproverRuleConfig("apply".equals(code)
                    ? null : "{\"userIds\":[\"" + code + "\"]}");
            node.setMultiInstanceMode(mode);
        }
        return node;
    }

    private ProcessEdgeDTO edge(String code, String source, String target) {
        return conditionalEdge(code, source, target, null, false);
    }

    private ProcessEdgeDTO conditionalEdge(String code,
                                           String source,
                                           String target,
                                           String expression,
                                           boolean defaultEdge) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode(code);
        edge.setSourceNodeCode(source);
        edge.setTargetNodeCode(target);
        edge.setConditionExpression(expression);
        edge.setDefaultEdge(Boolean.valueOf(defaultEdge));
        edge.setSortOrder(Integer.valueOf(10));
        return edge;
    }

    private DefinitionOperationRequest lifecycle(String definitionId, String operationId) {
        DefinitionOperationRequest request = new DefinitionOperationRequest();
        request.setDefinitionId(definitionId);
        request.setOperationId(operationId);
        request.setOperatorUserId("operator");
        return request;
    }

    private StartProcessRequest startRequest() {
        StartProcessRequest request = new StartProcessRequest();
        request.setOperationId(OPERATION_PREFIX + "start");
        request.setProcessCode(PROCESS_CODE);
        request.setInstanceTitle("M6 Cross-line Instance");
        request.setBusinessKey("M6-CROSS-LINE-001");
        request.setStarterUserId("starter");
        request.setStarterDeptId("dept-1");
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("amount", Integer.valueOf(1000));
        request.setVariables(variables);
        return request;
    }

    private String taskId(String instanceId, String nodeCode, String candidate) {
        return jdbcTemplate.queryForObject("SELECT id FROM process_active_task WHERE instance_id = ? "
                        + "AND node_code = ? AND task_status = 'ACTIVE' AND candidate_user_ids LIKE ?",
                String.class, instanceId, nodeCode, "%\"" + candidate + "\"%");
    }

    private int openTaskCount(String instanceId, String nodeCode) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_active_task WHERE instance_id = ? "
                        + "AND node_code = ? AND task_status IN ('ACTIVE', 'CLAIMED')",
                Integer.class, instanceId, nodeCode).intValue();
    }

    private int statusCount(String instanceId, String nodeCode, String status) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_active_task WHERE instance_id = ? "
                        + "AND node_code = ? AND task_status = ?",
                Integer.class, instanceId, nodeCode, status).intValue();
    }

    private int taskGroupCount(String instanceId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM process_task_group WHERE instance_id = ?",
                Integer.class, instanceId).intValue();
    }

    private int completedTaskGroupCount(String instanceId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_task_group WHERE instance_id = ? "
                        + "AND group_status = 'COMPLETED'",
                Integer.class, instanceId).intValue();
    }

    private int alertCount(String instanceId, String type) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_alert_record WHERE instance_id = ? "
                        + "AND alert_type = ?",
                Integer.class, instanceId, type).intValue();
    }

    private int callbackStatusCount(String instanceId, String status) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_callback_log WHERE instance_id = ? "
                        + "AND callback_status = ?",
                Integer.class, instanceId, status).intValue();
    }

    private void deleteByInstances(String table, String instanceQuery) {
        jdbcTemplate.update("DELETE FROM " + table + " WHERE instance_id IN (" + instanceQuery + ")",
                PROCESS_CODE);
    }

    private UserContext user(String userId) {
        return new UserContext(userId, userId, "dept-1", "Department");
    }
}
