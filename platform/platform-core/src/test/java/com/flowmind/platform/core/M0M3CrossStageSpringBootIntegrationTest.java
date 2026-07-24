package com.flowmind.platform.core;

import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.AttachmentDownloadDTO;
import com.flowmind.platform.api.dto.AttachmentQuery;
import com.flowmind.platform.api.dto.AttachmentTemplateCheckResult;
import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.OperationResult;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessAttachmentConfigDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.request.ApproveTaskRequest;
import com.flowmind.platform.api.request.CheckAttachmentRequest;
import com.flowmind.platform.api.request.CreateProcessDefinitionRequest;
import com.flowmind.platform.api.request.DefinitionOperationRequest;
import com.flowmind.platform.api.request.DeleteAttachmentRequest;
import com.flowmind.platform.api.request.DeleteProcessInstanceRequest;
import com.flowmind.platform.api.request.DownloadAttachmentRequest;
import com.flowmind.platform.api.request.SaveInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveProcessGraphRequest;
import com.flowmind.platform.api.request.SaveTaskAttachmentRequest;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.request.SubmitTaskRequest;
import com.flowmind.platform.api.request.TerminateProcessRequest;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.spi.ApproverResolver;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.core.callback.CallbackLogMapper;
import com.flowmind.platform.core.callback.CallbackOutboxService;
import com.flowmind.platform.core.callback.DefaultCallbackService;
import com.flowmind.platform.core.definition.DefaultProcessDefinitionService;
import com.flowmind.platform.core.definition.OperationIdempotencyService;
import com.flowmind.platform.core.definition.ProcessDefinitionAttachmentConfigManager;
import com.flowmind.platform.core.definition.ProcessDefinitionCache;
import com.flowmind.platform.core.definition.ProcessFormFieldDefinitionManager;
import com.flowmind.platform.core.runtime.ApproverResolveRequestFactory;
import com.flowmind.platform.core.runtime.DefaultProcessRuntimeService;
import com.flowmind.platform.core.runtime.InstanceTaskCancellationService;
import com.flowmind.platform.core.runtime.RuntimeDefinitionLoader;
import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeNodeAdvancer;
import com.flowmind.platform.core.runtime.RuntimeNodeConfigReader;
import com.flowmind.platform.core.runtime.RuntimeOperationExecutor;
import com.flowmind.platform.core.runtime.RuntimeRequestValidator;
import com.flowmind.platform.core.runtime.RuntimeStateException;
import com.flowmind.platform.core.runtime.RuntimeStateValidator;
import com.flowmind.platform.core.runtime.RuntimeTransactionExecutor;
import com.flowmind.platform.core.runtime.SimpleConditionExpressionEvaluator;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.core.validation.ProcessDefinitionAttachmentConfigValidator;
import com.flowmind.platform.core.validation.ProcessFormFieldValidator;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.HistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessAttachmentTemplateRepository;
import com.flowmind.platform.persistence.repository.ProcessCallbackLogRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionAttachmentConfigRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionRepository;
import com.flowmind.platform.persistence.repository.ProcessEdgeRepository;
import com.flowmind.platform.persistence.repository.ProcessFormFieldRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceDeletionRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.ProcessNodeRepository;
import com.flowmind.platform.persistence.repository.ProcessOperationRecordRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import com.flowmind.platform.testsupport.SchemaTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 《02-M0-M3 验收用例》中 E2E-001～E2E-005、E2E-007～E2E-008 的 Spring Boot 集成测试。
 *
 * <p>定义、运行时、查询、回调和 SQLite 均使用生产实现；只有外部用户目录、
 * 附件存储和委托关系使用测试替身。</p>
 */
@SpringBootTest(classes = M0M3CrossStageSpringBootIntegrationTest.TestApplication.class)
class M0M3CrossStageSpringBootIntegrationTest {

    private static final String PROCESS_CODE = "it-deposit";
    private static final String TEST_OPERATION_PREFIX = "e2e";
    private static final String DATABASE_FILE = resolveIdentifierDatabase();
    private static final String JDBC_URL = "jdbc:sqlite:" + DATABASE_FILE + "?busy_timeout=10000";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ProcessDefinitionService definitionService;
    @Autowired
    private ProcessRuntimeService runtimeService;
    @Autowired
    private MutableCurrentUserProvider currentUserProvider;
    @Autowired
    private ProcessInstanceRepository instanceRepository;
    @Autowired
    private ActiveTaskRepository activeTaskRepository;
    @Autowired
    private HistoryTaskRepository historyTaskRepository;
    @Autowired
    private ProcessHistoryTaskRepository processHistoryTaskRepository;
    @Autowired
    private ProcessOperationRecordRepository operationRecordRepository;
    @Autowired
    private TaskGroupRepository taskGroupRepository;
    @Autowired
    private ProcessDefinitionRepository definitionRepository;
    @Autowired
    private ProcessInstanceDeletionRepository instanceDeletionRepository;
    @Autowired
    private InstanceTaskCancellationService cancellationService;
    @Autowired
    private RuntimeDefinitionLoader definitionLoader;
    @Autowired
    private RuntimeNodeAdvancer nodeAdvancer;
    @Autowired
    private RuntimeRequestValidator requestValidator;
    @Autowired
    private RuntimeStateValidator stateValidator;
    @Autowired
    private CallbackService callbackService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetDatabase() {
        deleteTestOwnedData();
        insertAttachmentTemplate();
        currentUserProvider.setCurrent(user("starter", "Starter"));
    }

    @Test
    void e2e001MainFlowCompletesFromDefinitionToRuntimeAndQuery() {
        ProcessDefinitionDTO definition = createAndActivateDefinition("e2e001", PROCESS_CODE);

        ProcessInstanceDTO instance = runtimeService.startAndSubmit(startRequest("e2e001-start", PROCESS_CODE));
        approveNode(instance.getInstanceId(), "manager", "manager", "e2e001-manager");
        approveNode(instance.getInstanceId(), "finance", "finance", "e2e001-finance");

        ProcessInstanceDetailDTO detail = runtimeService.getInstance(instance.getInstanceId());
        assertEquals(InstanceStatusEnum.COMPLETED, detail.getInstanceStatus());
        assertEquals(definition.getId(), detail.getDefinitionId());
        assertEquals(Integer.valueOf(1), detail.getVersion());
        assertEquals("1000.00", String.valueOf(detail.getVariables().get("amount")));
        assertEquals(3, detail.getHistoryTasks().size());
        assertEquals(2, detail.getComments().size());
        assertTrue(detail.getActiveTasks().isEmpty());
        assertEquals(7, countByInstance("process_callback_log", instance.getInstanceId()));
        assertEquals(7, countTestOperations());
    }

    @Test
    void e2e002RepeatedRequestsReplayWithoutDuplicateBusinessRows() {
        CreateProcessDefinitionRequest create = createRequest("e2e002-create", PROCESS_CODE);
        ProcessDefinitionDTO first = definitionService.createDefinition(create);
        ProcessDefinitionDTO createReplay = definitionService.createDefinition(create);
        assertEquals(first.getId(), createReplay.getId());

        SaveProcessGraphRequest graph = depositGraph("e2e002-save");
        definitionService.saveGraph(first.getId(), graph);
        definitionService.saveGraph(first.getId(), graph);
        DefinitionOperationRequest publish = lifecycleRequest(first.getId(), "e2e002-publish");
        definitionService.publish(publish);
        definitionService.publish(publish);
        DefinitionOperationRequest activate = lifecycleRequest(first.getId(), "e2e002-activate");
        definitionService.activate(activate);
        definitionService.activate(activate);

        StartProcessRequest start = startRequest("e2e002-start", PROCESS_CODE);
        ProcessInstanceDTO instance = runtimeService.startAndSubmit(start);
        ProcessInstanceDTO startReplay = runtimeService.startAndSubmit(start);
        assertEquals(instance.getInstanceId(), startReplay.getInstanceId());

        ApproveTaskRequest manager = approveRequest("e2e002-manager",
                openTaskId(instance.getInstanceId(), "manager"), "manager");
        currentUserProvider.setCurrent(user("manager", "Manager"));
        runtimeService.approve(manager);
        runtimeService.approve(manager);
        ApproveTaskRequest finance = approveRequest("e2e002-finance",
                openTaskId(instance.getInstanceId(), "finance"), "finance");
        currentUserProvider.setCurrent(user("finance", "Finance"));
        runtimeService.approve(finance);
        runtimeService.approve(finance);

        assertEquals(1, countTestDefinitions());
        assertEquals(5, countTestDefinitionChildren("process_node"));
        assertEquals(4, countTestDefinitionChildren("process_edge"));
        assertEquals(1, countTestInstances());
        assertEquals(3, countByInstance("process_history_task", instance.getInstanceId()));
        assertEquals(7, countByInstance("process_callback_log", instance.getInstanceId()));
        assertEquals(7, countTestOperations());
    }

    @Test
    void e2e003ConcurrentManagerApprovalCreatesOneFinanceTask() throws Exception {
        createAndActivateDefinition("e2e003", PROCESS_CODE);
        ProcessInstanceDTO instance = runtimeService.startAndSubmit(startRequest("e2e003-start", PROCESS_CODE));
        final String managerTaskId = openTaskId(instance.getInstanceId(), "manager");
        currentUserProvider.setCurrent(user("manager", "Manager"));

        List<Boolean> outcomes = concurrently(
                approveAttempt("e2e003-manager-a", managerTaskId),
                approveAttempt("e2e003-manager-b", managerTaskId));

        assertEquals(1, successes(outcomes));
        assertEquals(1, countOpenTasks(instance.getInstanceId(), "finance"));
        assertEquals(2, countByInstance("process_history_task", instance.getInstanceId()));
        assertEquals(5, countByInstance("process_callback_log", instance.getInstanceId()));
    }

    @Test
    void e2e004CallbackFailureRollsBackHistoryNextTaskAndOutboxThenAllowsRetry() {
        createAndActivateDefinition("e2e004", PROCESS_CODE);
        ProcessInstanceDTO instance = runtimeService.startAndSubmit(startRequest("e2e004-start", PROCESS_CODE));
        String managerTaskId = openTaskId(instance.getInstanceId(), "manager");
        int callbacksBefore = countByInstance("process_callback_log", instance.getInstanceId());
        currentUserProvider.setCurrent(user("manager", "Manager"));

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
        ProcessRuntimeService failingService = runtimeService(failingAfterOutbox);

        assertThrows(RuntimeStateException.class,
                () -> failingService.approve(approveRequest("e2e004-manager-fail", managerTaskId, "manager")));
        assertEquals("ACTIVE", taskStatus(managerTaskId));
        assertEquals(1, countByInstance("process_history_task", instance.getInstanceId()));
        assertEquals(0, countOpenTasks(instance.getInstanceId(), "finance"));
        assertEquals(callbacksBefore, countByInstance("process_callback_log", instance.getInstanceId()));

        runtimeService.approve(approveRequest("e2e004-manager-retry", managerTaskId, "manager"));
        assertEquals(1, countOpenTasks(instance.getInstanceId(), "finance"));
        assertEquals("FAILED", operationStatus("e2e004-manager-fail"));
        assertEquals("SUCCESS", operationStatus("e2e004-manager-retry"));
    }

    @Test
    void e2e005RunningInstanceKeepsV1WhileNewInstanceUsesV2() {
        ProcessDefinitionDTO v1 = createAndActivateDefinition("e2e005-v1", PROCESS_CODE);
        ProcessInstanceDTO instanceA = runtimeService.startAndSubmit(startRequest("e2e005-start-a", PROCESS_CODE));

        ProcessDefinitionDTO v2 = createAndActivateDefinition("e2e005-v2", PROCESS_CODE);
        ProcessInstanceDTO instanceB = runtimeService.startAndSubmit(startRequest("e2e005-start-b", PROCESS_CODE));

        assertNotEquals(v1.getId(), v2.getId());
        assertEquals(Integer.valueOf(1), runtimeService.getInstance(instanceA.getInstanceId()).getVersion());
        assertEquals(v1.getId(), runtimeService.getInstance(instanceA.getInstanceId()).getDefinitionId());
        assertEquals(Integer.valueOf(2), runtimeService.getInstance(instanceB.getInstanceId()).getVersion());
        assertEquals(v2.getId(), runtimeService.getInstance(instanceB.getInstanceId()).getDefinitionId());

        assertEquals(1, countOpenTasks(instanceA.getInstanceId(), "manager"));
    }

    @Test
    void e2e006TerminateRunningInstanceCommitsReplaysAndRollsBackAtomically() {
        createAndActivateDefinition("e2e007", PROCESS_CODE);
        ProcessInstanceDTO instance = startAndReachManager("e2e007");
        String instanceId = instance.getInstanceId();
        String managerTaskId = openTaskId(instanceId, "manager");
        int historyBefore = countByInstance("process_history_task", instanceId);
        int callbacksBefore = countByInstance("process_callback_log", instanceId);
        TerminateProcessRequest request = terminateRequest(
                "e2e007-terminate", instanceId, "stopped for E2E-007");
        currentUserProvider.setCurrent(user("admin", "Administrator"));

        ProcessInstanceDTO terminated = runtimeService.terminate(request);

        assertEquals(InstanceStatusEnum.TERMINATED, terminated.getInstanceStatus());
        assertTrue(terminated.getCurrentNodeCodes().isEmpty());
        assertEquals("CANCELED", taskStatus(managerTaskId));
        assertEquals(historyBefore + 1, countByInstance("process_history_task", instanceId));
        assertEquals(callbacksBefore + 1, countByInstance("process_callback_log", instanceId));
        assertEquals("TERMINATE", valueByOperation("process_history_task", "action_type",
                "e2e007-terminate"));
        assertEquals("TERMINATE", valueByOperation("process_audit_log", "action_type",
                "e2e007-terminate"));
        assertEquals("PROCESS_TERMINATED", valueByOperation("process_callback_log", "event_type",
                "e2e007-terminate"));
        assertEquals("SUCCESS", operationStatus("e2e007-terminate"));

        ProcessInstanceDTO replay = runtimeService.terminate(request);

        assertEquals(InstanceStatusEnum.TERMINATED, replay.getInstanceStatus());
        assertEquals(historyBefore + 1, countByInstance("process_history_task", instanceId));
        assertEquals(callbacksBefore + 1, countByInstance("process_callback_log", instanceId));
        assertEquals(1, countByOperation("process_audit_log", "e2e007-terminate"));
        assertEquals(1, countByOperation("process_callback_log", "e2e007-terminate"));

        ProcessInstanceDTO rollbackInstance = startAndReachManager("e2e007-rollback");
        String rollbackInstanceId = rollbackInstance.getInstanceId();
        String rollbackTaskId = openTaskId(rollbackInstanceId, "manager");
        int rollbackHistoryBefore = countByInstance("process_history_task", rollbackInstanceId);
        int rollbackCallbacksBefore = countByInstance("process_callback_log", rollbackInstanceId);
        CallbackService failingAfterOutbox = failingAfterOutbox();
        ProcessRuntimeService failingService = runtimeService(failingAfterOutbox, instanceDeletionRepository);
        currentUserProvider.setCurrent(user("admin", "Administrator"));

        assertThrows(RuntimeStateException.class, () -> failingService.terminate(
                terminateRequest("e2e007-terminate-rollback", rollbackInstanceId,
                        "must be rolled back")));

        assertEquals(InstanceStatusEnum.RUNNING,
                runtimeService.getInstance(rollbackInstanceId).getInstanceStatus());
        assertEquals("ACTIVE", taskStatus(rollbackTaskId));
        assertEquals(rollbackHistoryBefore,
                countByInstance("process_history_task", rollbackInstanceId));
        assertEquals(rollbackCallbacksBefore,
                countByInstance("process_callback_log", rollbackInstanceId));
        assertEquals(0, countByOperation("process_audit_log", "e2e007-terminate-rollback"));
        assertEquals(0, countByOperation("process_callback_log", "e2e007-terminate-rollback"));
        assertEquals("FAILED", operationStatus("e2e007-terminate-rollback"));
    }

    @Test
    void e2e007DeleteInstanceCascadesRetainsEvidenceReplaysAndRollsBackAtomically() {
        createAndActivateDefinition("e2e008", PROCESS_CODE);
        ProcessInstanceDTO instance = startAndReachManager("e2e008");
        String instanceId = instance.getInstanceId();
        DeleteProcessInstanceRequest request = deleteRequest("e2e008-delete", instanceId);
        currentUserProvider.setCurrent(user("admin", "Administrator"));

        OperationResult first = runtimeService.deleteInstance(request);

        assertTrue(first.isDeleted());
        assertEquals(false, first.isReplayed());
        assertEquals(0, countById("process_instance", instanceId));
        assertEquals(0, countByInstance("process_active_task", instanceId));
        assertEquals(0, countByInstance("process_task_group", instanceId));
        assertEquals(0, countByInstance("process_history_task", instanceId));
        assertEquals(0, countByInstance("process_attachment", instanceId));
        assertEquals(1, countByOperation("process_audit_log", "e2e008-delete"));
        assertEquals(1, countByOperation("process_callback_log", "e2e008-delete"));
        assertEquals(1, countNullInstanceByOperation("process_audit_log", "e2e008-delete"));
        assertEquals(1, countNullInstanceByOperation("process_callback_log", "e2e008-delete"));
        assertEquals("1", valueByOperation("process_audit_log",
                "json_extract(detail_json, '$.targetDeleted')", "e2e008-delete"));
        assertEquals("HARD", valueByOperation("process_callback_log",
                "json_extract(payload_json, '$.deleteMode')", "e2e008-delete"));
        assertEquals("PROCESS_CANCELED", valueByOperation("process_callback_log", "event_type",
                "e2e008-delete"));
        assertEquals("SUCCESS", operationStatus("e2e008-delete"));

        OperationResult replay = runtimeService.deleteInstance(request);

        assertTrue(replay.isDeleted());
        assertTrue(replay.isReplayed());
        assertEquals(1, countByOperation("process_audit_log", "e2e008-delete"));
        assertEquals(1, countByOperation("process_callback_log", "e2e008-delete"));
        assertEquals(1, countByOperation("process_operation_record", "e2e008-delete"));

        ProcessInstanceDTO rollbackInstance = startAndReachManager("e2e008-rollback");
        String rollbackInstanceId = rollbackInstance.getInstanceId();
        String rollbackTaskId = openTaskId(rollbackInstanceId, "manager");
        int rollbackHistoryBefore = countByInstance("process_history_task", rollbackInstanceId);
        int rollbackCallbacksBefore = countByInstance("process_callback_log", rollbackInstanceId);
        ProcessInstanceDeletionRepository failingDeletion = new ProcessInstanceDeletionRepository(jdbcTemplate) {
            @Override
            public int deleteRuntimeData(String targetInstanceId) {
                throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION,
                        "simulated cascade delete failure");
            }
        };
        ProcessRuntimeService failingService = runtimeService(callbackService, failingDeletion);
        currentUserProvider.setCurrent(user("admin", "Administrator"));

        assertThrows(RuntimeStateException.class, () -> failingService.deleteInstance(
                deleteRequest("e2e008-delete-rollback", rollbackInstanceId)));

        assertEquals(InstanceStatusEnum.RUNNING,
                runtimeService.getInstance(rollbackInstanceId).getInstanceStatus());
        assertEquals("ACTIVE", taskStatus(rollbackTaskId));
        assertEquals(0, countByInstance("process_task_group", rollbackInstanceId));
        assertEquals(rollbackHistoryBefore,
                countByInstance("process_history_task", rollbackInstanceId));
        assertEquals(rollbackCallbacksBefore,
                countByInstance("process_callback_log", rollbackInstanceId));
        assertEquals(0, countByOperation("process_audit_log", "e2e008-delete-rollback"));
        assertEquals(0, countByOperation("process_callback_log", "e2e008-delete-rollback"));
        assertEquals("FAILED", operationStatus("e2e008-delete-rollback"));
    }

    private ProcessDefinitionDTO createAndActivateDefinition(String prefix, String processCode) {
        ProcessDefinitionDTO definition =
                definitionService.createDefinition(createRequest(prefix + "-create", processCode));
        definitionService.saveGraph(definition.getId(), depositGraph(prefix + "-save"));
        definitionService.publish(lifecycleRequest(definition.getId(), prefix + "-publish"));
        return definitionService.activate(lifecycleRequest(definition.getId(), prefix + "-activate"));
    }

    private void submitStarterTask(ProcessInstanceDTO instance, String operationId) {
        currentUserProvider.setCurrent(user("starter", "Starter"));
        runtimeService.submitTask(submitRequest(operationId,
                openTaskId(instance.getInstanceId(), "apply"), "starter"));
    }

    private void approveNode(String instanceId, String nodeCode, String userId, String operationId) {
        currentUserProvider.setCurrent(user(userId, Character.toUpperCase(userId.charAt(0)) + userId.substring(1)));
        runtimeService.approve(approveRequest(operationId, openTaskId(instanceId, nodeCode), userId));
    }

    private ProcessInstanceDTO startAndReachManager(String prefix) {
        currentUserProvider.setCurrent(user("starter", "Starter"));
        ProcessInstanceDTO instance = runtimeService.startAndSubmit(
                startRequest(prefix + "-start", PROCESS_CODE));
        return instance;
    }

    private ProcessRuntimeService runtimeService(CallbackService runtimeCallbackService) {
        return runtimeService(runtimeCallbackService, instanceDeletionRepository);
    }

    private ProcessRuntimeService runtimeService(CallbackService runtimeCallbackService,
                                                 ProcessInstanceDeletionRepository runtimeDeletionRepository) {
        return new DefaultProcessRuntimeService(instanceRepository, activeTaskRepository, historyTaskRepository,
                definitionLoader, requestValidator,
                new RuntimeOperationExecutor(new OperationIdempotencyService(operationRecordRepository)),
                nodeAdvancer, new PassingAttachmentService(), runtimeCallbackService, stateValidator,
                new HistoryTaskWriter(processHistoryTaskRepository),
                new RuntimeTransactionExecutor(transactionManager), cancellationService,
                runtimeDeletionRepository, definitionRepository);
    }

    private CallbackService failingAfterOutbox() {
        return new CallbackService() {
            @Override
            public void publishCallback(WorkflowEvent event) {
                callbackService.publishCallback(event);
                throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION,
                        "simulated callback failure after outbox");
            }

            @Override
            public PageResult<CallbackLogDTO> queryCallbackLogs(CallbackLogQuery query) {
                return callbackService.queryCallbackLogs(query);
            }
        };
    }

    private Callable<Boolean> approveAttempt(final String operationId, final String taskId) {
        return new Callable<Boolean>() {
            @Override
            public Boolean call() {
                try {
                    runtimeService.approve(approveRequest(operationId, taskId, "manager"));
                    return Boolean.TRUE;
                } catch (RuntimeException ex) {
                    return Boolean.FALSE;
                }
            }
        };
    }

    private List<Boolean> concurrently(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> one = executor.submit(awaitStart(ready, start, first));
            Future<Boolean> two = executor.submit(awaitStart(ready, start, second));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return Arrays.asList(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
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

    private int successes(List<Boolean> outcomes) {
        int count = 0;
        for (Boolean outcome : outcomes) {
            if (Boolean.TRUE.equals(outcome)) {
                count++;
            }
        }
        return count;
    }

    private void deleteTestOwnedData() {
        String testInstances = "SELECT id FROM process_instance WHERE process_code = ?";
        jdbcTemplate.update("DELETE FROM process_active_task WHERE instance_id IN (" + testInstances + ")",
                PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_task_group WHERE instance_id IN (" + testInstances + ")",
                PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_history_task WHERE instance_id IN (" + testInstances + ")",
                PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_read_record WHERE instance_id IN (" + testInstances + ")",
                PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_attachment WHERE instance_id IN (" + testInstances + ")",
                PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_reminder_record WHERE instance_id IN (" + testInstances + ")",
                PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_alert_record WHERE instance_id IN (" + testInstances + ")",
                PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_callback_log WHERE instance_id IN (" + testInstances + ")",
                PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_audit_log WHERE instance_id IN (" + testInstances + ")",
                PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_callback_log WHERE operation_id LIKE ?",
                TEST_OPERATION_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM process_audit_log WHERE operation_id LIKE ?",
                TEST_OPERATION_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM process_instance WHERE process_code = ?", PROCESS_CODE);

        String testDefinitions = "SELECT id FROM process_definition WHERE process_code = ?";
        jdbcTemplate.update("DELETE FROM process_definition_attachment_config WHERE definition_id IN ("
                + testDefinitions + ")", PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_form_field WHERE definition_id IN (" + testDefinitions + ")",
                PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_edge WHERE definition_id IN (" + testDefinitions + ")",
                PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_node WHERE definition_id IN (" + testDefinitions + ")",
                PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_audit_log WHERE target_id IN (" + testDefinitions + ")",
                PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_operation_record WHERE operation_id LIKE ?",
                TEST_OPERATION_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM process_definition WHERE process_code = ?", PROCESS_CODE);
        jdbcTemplate.update("DELETE FROM process_attachment_template WHERE id = ?", "e2e-template-001");
    }

    private void insertAttachmentTemplate() {
        jdbcTemplate.update("INSERT INTO process_attachment_template "
                        + "(id, attachment_code, template_version, attachment_name, allowed_extensions, "
                        + "max_size_bytes, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "e2e-template-001", "e2e-receipt", Integer.valueOf(1), "E2E bank receipt", "[\"pdf\"]",
                Long.valueOf(1024L * 1024L), "operator", "operator");
    }

    private String openTaskId(String instanceId, String nodeCode) {
        return jdbcTemplate.queryForObject("SELECT id FROM process_active_task WHERE instance_id = ? "
                        + "AND node_code = ? AND task_status = 'ACTIVE'",
                String.class, instanceId, nodeCode);
    }

    private String taskStatus(String taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT task_status FROM process_active_task WHERE id = ?", String.class, taskId);
    }

    private String operationStatus(String operationId) {
        return jdbcTemplate.queryForObject(
                "SELECT operation_status FROM process_operation_record WHERE operation_id = ?",
                String.class, operationId);
    }

    private int countTestDefinitions() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM process_definition WHERE process_code = ?",
                Integer.class, PROCESS_CODE).intValue();
    }

    private int countTestDefinitionChildren(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
                        + " WHERE definition_id IN (SELECT id FROM process_definition WHERE process_code = ?)",
                Integer.class, PROCESS_CODE).intValue();
    }

    private int countTestInstances() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM process_instance WHERE process_code = ?",
                Integer.class, PROCESS_CODE).intValue();
    }

    private int countTestOperations() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM process_operation_record WHERE operation_id LIKE ?",
                Integer.class, TEST_OPERATION_PREFIX + "%").intValue();
    }

    private int countByInstance(String table, String instanceId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE instance_id = ?",
                Integer.class, instanceId).intValue();
    }

    private int countById(String table, String id) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?",
                Integer.class, id).intValue();
    }

    private int countByOperation(String table, String operationId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE operation_id = ?",
                Integer.class, operationId).intValue();
    }

    private int countNullInstanceByOperation(String table, String operationId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
                        + " WHERE operation_id = ? AND instance_id IS NULL",
                Integer.class, operationId).intValue();
    }

    private String valueByOperation(String table, String expression, String operationId) {
        return jdbcTemplate.queryForObject("SELECT CAST(" + expression + " AS TEXT) FROM " + table
                        + " WHERE operation_id = ?",
                String.class, operationId);
    }

    private int countOpenTasks(String instanceId, String nodeCode) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_active_task WHERE instance_id = ? "
                        + "AND node_code = ? AND task_status = 'ACTIVE'",
                Integer.class, instanceId, nodeCode).intValue();
    }

    private static CreateProcessDefinitionRequest createRequest(String operationId, String processCode) {
        CreateProcessDefinitionRequest request = new CreateProcessDefinitionRequest();
        request.setOperationId(operationId);
        request.setOperatorUserId("operator");
        request.setProcessCode(processCode);
        request.setProcessName("Deposit application");
        request.setSystemCode("fund");
        return request;
    }

    private static DefinitionOperationRequest lifecycleRequest(String definitionId, String operationId) {
        DefinitionOperationRequest request = new DefinitionOperationRequest();
        request.setDefinitionId(definitionId);
        request.setOperationId(operationId);
        request.setOperatorUserId("operator");
        return request;
    }

    private static SaveProcessGraphRequest depositGraph(String operationId) {
        SaveProcessGraphRequest request = new SaveProcessGraphRequest();
        request.setOperationId(operationId);
        request.setOperatorUserId("operator");
        request.setNodes(Arrays.asList(
                node("start", "Start", NodeTypeEnum.START, null, 10),
                node("apply", "Application", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.STARTER, 20),
                node("manager", "Manager approval", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.USER, 30),
                node("finance", "Finance approval", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.USER, 40),
                node("end", "End", NodeTypeEnum.END, null, 50)));
        request.setEdges(Arrays.asList(
                edge("start-apply", "start", "apply", 10),
                edge("apply-manager", "apply", "manager", 20),
                edge("manager-finance", "manager", "finance", 30),
                edge("finance-end", "finance", "end", 40)));

        ProcessFormFieldDTO amount = new ProcessFormFieldDTO();
        amount.setFieldCode("amount");
        amount.setFieldName("Deposit amount");
        amount.setFieldType("number");
        amount.setControlType("number");
        amount.setRequired(Boolean.TRUE);
        amount.setSortOrder(Integer.valueOf(10));
        request.setFormFields(Collections.singletonList(amount));

        ProcessAttachmentConfigDTO attachment = new ProcessAttachmentConfigDTO();
        attachment.setAttachmentConfigId("e2e-attachment-group-" + operationId);
        attachment.setAttachmentTemplateId("e2e-template-001");
        attachment.setAttachmentCode("e2e-receipt");
        attachment.setRequired(Boolean.TRUE);
        attachment.setMinCount(Integer.valueOf(1));
        attachment.setMaxCount(Integer.valueOf(2));
        attachment.setApplicableNodeCodes(Collections.singletonList("apply"));
        attachment.setSortOrder(Integer.valueOf(10));
        request.setAttachmentConfigs(Collections.singletonList(attachment));
        return request;
    }

    private static ProcessNodeDTO node(String code,
                                       String name,
                                       NodeTypeEnum type,
                                       ApproverRuleTypeEnum rule,
                                       int sortOrder) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeName(name);
        node.setNodeType(type);
        node.setSortOrder(Integer.valueOf(sortOrder));
        if (NodeTypeEnum.USER_TASK.equals(type)) {
            node.setApproverRuleType(rule);
            node.setMultiInstanceMode(MultiInstanceModeEnum.SINGLE);
            node.setApproverRuleConfig(ApproverRuleTypeEnum.STARTER.equals(rule)
                    ? null : "{\"userIds\":[\"" + code + "\"]}");
        }
        return node;
    }

    private static ProcessEdgeDTO edge(String code, String source, String target, int sortOrder) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode(code);
        edge.setSourceNodeCode(source);
        edge.setTargetNodeCode(target);
        edge.setDefaultEdge(Boolean.FALSE);
        edge.setSortOrder(Integer.valueOf(sortOrder));
        return edge;
    }

    private static StartProcessRequest startRequest(String operationId, String processCode) {
        StartProcessRequest request = new StartProcessRequest();
        request.setOperationId(operationId);
        request.setProcessCode(processCode);
        request.setInstanceTitle("Deposit #1001");
        request.setBusinessKey("DEPOSIT-1001-" + operationId);
        request.setStarterUserId("starter");
        request.setStarterDeptId("dept-1");
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("amount", "1000.00");
        request.setVariables(variables);
        return request;
    }

    private static SubmitTaskRequest submitRequest(String operationId, String taskId, String userId) {
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setOperationId(operationId);
        request.setTaskId(taskId);
        request.setExpectedTaskVersion(Long.valueOf(0L));
        request.setOperatorUserId(userId);
        request.setComment("application submitted");
        request.setVariables(Collections.<String, Object>singletonMap("accountName", "ACME"));
        return request;
    }

    private static ApproveTaskRequest approveRequest(String operationId, String taskId, String userId) {
        ApproveTaskRequest request = new ApproveTaskRequest();
        request.setOperationId(operationId);
        request.setTaskId(taskId);
        request.setExpectedTaskVersion(Long.valueOf(0L));
        request.setOperatorUserId(userId);
        request.setComment("approved");
        return request;
    }

    private static TerminateProcessRequest terminateRequest(String operationId,
                                                            String instanceId,
                                                            String comment) {
        TerminateProcessRequest request = new TerminateProcessRequest();
        request.setOperationId(operationId);
        request.setInstanceId(instanceId);
        request.setOperatorUserId("admin");
        request.setComment(comment);
        return request;
    }

    private static DeleteProcessInstanceRequest deleteRequest(String operationId, String instanceId) {
        DeleteProcessInstanceRequest request = new DeleteProcessInstanceRequest();
        request.setOperationId(operationId);
        request.setInstanceId(instanceId);
        request.setOperatorUserId("admin");
        return request;
    }

    private static UserContext user(String id, String name) {
        return new UserContext(id, name, "dept-1", "Department");
    }

    private static String resolveIdentifierDatabase() {
        File current = new File(System.getProperty("user.dir")).getAbsoluteFile();
        for (int depth = 0; depth < 8 && current != null; depth++) {
            File candidate = new File(current, "identifier.sqlite");
            if (candidate.isFile()) {
                try {
                    return candidate.getCanonicalPath().replace('\\', '/');
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Cannot resolve identifier.sqlite path", ex);
                }
            }
            current = current.getParentFile();
        }
        throw new IllegalStateException("identifier.sqlite was not found from user.dir or its parents");
    }

    @SpringBootConfiguration
    @EnableTransactionManagement
    static class TestApplication {

        @Bean
        DataSource dataSource() throws Exception {
            try (Connection connection = DriverManager.getConnection(JDBC_URL);
                 Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA busy_timeout = 10000");
                SchemaTestSupport.executeSchema(connection);
            }
            return new DriverManagerDataSource(JDBC_URL);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean ProcessDefinitionRepository definitionRepository(JdbcTemplate jdbc) {
            return new ProcessDefinitionRepository(jdbc);
        }
        @Bean ProcessNodeRepository nodeRepository(JdbcTemplate jdbc) {
            return new ProcessNodeRepository(jdbc);
        }
        @Bean ProcessEdgeRepository edgeRepository(JdbcTemplate jdbc) {
            return new ProcessEdgeRepository(jdbc);
        }
        @Bean ProcessFormFieldRepository formFieldRepository(JdbcTemplate jdbc) {
            return new ProcessFormFieldRepository(jdbc);
        }
        @Bean ProcessAttachmentTemplateRepository attachmentTemplateRepository(JdbcTemplate jdbc) {
            return new ProcessAttachmentTemplateRepository(jdbc);
        }
        @Bean ProcessDefinitionAttachmentConfigRepository attachmentConfigRepository(JdbcTemplate jdbc) {
            return new ProcessDefinitionAttachmentConfigRepository(jdbc);
        }
        @Bean ProcessOperationRecordRepository operationRecordRepository(JdbcTemplate jdbc) {
            return new ProcessOperationRecordRepository(jdbc);
        }
        @Bean ProcessInstanceRepository instanceRepository(JdbcTemplate jdbc) {
            return new ProcessInstanceRepository(jdbc);
        }
        @Bean ActiveTaskRepository activeTaskRepository(JdbcTemplate jdbc) {
            return new ActiveTaskRepository(jdbc);
        }
        @Bean TaskGroupRepository taskGroupRepository(JdbcTemplate jdbc) {
            return new TaskGroupRepository(jdbc);
        }
        @Bean HistoryTaskRepository historyTaskRepository(JdbcTemplate jdbc) {
            return new HistoryTaskRepository(jdbc);
        }
        @Bean ProcessHistoryTaskRepository processHistoryTaskRepository(JdbcTemplate jdbc) {
            return new ProcessHistoryTaskRepository(jdbc);
        }
        @Bean ProcessCallbackLogRepository callbackLogRepository(JdbcTemplate jdbc) {
            return new ProcessCallbackLogRepository(jdbc);
        }
        @Bean ProcessInstanceDeletionRepository instanceDeletionRepository(JdbcTemplate jdbc) {
            return new ProcessInstanceDeletionRepository(jdbc);
        }

        @Bean
        ProcessFormFieldDefinitionManager formFieldManager(ProcessFormFieldRepository repository) {
            return new ProcessFormFieldDefinitionManager(repository, new ProcessFormFieldValidator());
        }

        @Bean
        ProcessDefinitionAttachmentConfigManager attachmentConfigManager(
                ProcessDefinitionAttachmentConfigRepository configs,
                ProcessAttachmentTemplateRepository templates) {
            return new ProcessDefinitionAttachmentConfigManager(configs, templates,
                    new ProcessDefinitionAttachmentConfigValidator(templates));
        }

        @Bean
        ProcessDefinitionCache definitionCache() {
            return new ProcessDefinitionCache();
        }

        @Bean
        ProcessDefinitionService definitionService(ProcessDefinitionRepository definitions,
                                                   ProcessNodeRepository nodes,
                                                   ProcessEdgeRepository edges,
                                                   ProcessFormFieldDefinitionManager fields,
                                                   ProcessDefinitionAttachmentConfigManager attachments,
                                                   ProcessOperationRecordRepository operations,
                                                   ProcessDefinitionCache cache) {
            return new DefaultProcessDefinitionService(definitions, nodes, edges, fields,
                    attachments, operations, cache);
        }

        @Bean
        MutableCurrentUserProvider currentUserProvider() {
            return new MutableCurrentUserProvider(user("starter", "Starter"));
        }

        @Bean
        ApproverResolver approverResolver() {
            return request -> {
                String userId = "apply".equals(request.getNodeCode())
                        ? request.getStarterUserId() : request.getNodeCode();
                return Collections.singletonList(new UserDTO(userId, userId));
            };
        }

        @Bean
        RuntimeDefinitionLoader definitionLoader(ProcessDefinitionRepository definitions,
                                                 ProcessDefinitionService service,
                                                 ProcessDefinitionCache cache) {
            return new RuntimeDefinitionLoader(definitions, service, cache);
        }

        @Bean
        RuntimeRequestValidator runtimeRequestValidator(MutableCurrentUserProvider users) {
            return new RuntimeRequestValidator(users);
        }

        @Bean
        RuntimeNodeAdvancer runtimeNodeAdvancer(ActiveTaskRepository tasks,
                                               TaskGroupRepository groups,
                                               ProcessInstanceRepository instances,
                                               RuntimeRequestValidator validator,
                                               ApproverResolver resolver) {
            return new RuntimeNodeAdvancer(tasks, groups, instances, validator, resolver,
                    new SimpleConditionExpressionEvaluator(),
                    new ApproverResolveRequestFactory(new RuntimeNodeConfigReader()));
        }

        @Bean
        AttachmentService attachmentService() {
            return new PassingAttachmentService();
        }

        @Bean
        CallbackService callbackService(ProcessCallbackLogRepository callbacks) {
            CallbackLogMapper mapper = new CallbackLogMapper();
            return new DefaultCallbackService(callbacks, mapper, new CallbackOutboxService(callbacks, mapper));
        }

        @Bean
        HistoryTaskWriter historyTaskWriter(ProcessHistoryTaskRepository histories) {
            return new HistoryTaskWriter(histories);
        }

        @Bean
        RuntimeStateValidator runtimeStateValidator(ActiveTaskRepository tasks,
                                                    ProcessInstanceRepository instances,
                                                    TaskGroupRepository groups) {
            return new RuntimeStateValidator(tasks, instances, groups);
        }

        @Bean
        RuntimeTransactionExecutor runtimeTransactionExecutor(PlatformTransactionManager transactionManager) {
            return new RuntimeTransactionExecutor(transactionManager);
        }

        @Bean
        InstanceTaskCancellationService cancellationService(ActiveTaskRepository tasks,
                                                            TaskGroupRepository groups,
                                                            HistoryTaskWriter histories) {
            return new InstanceTaskCancellationService(tasks, groups, histories);
        }

        @Bean
        ProcessRuntimeService runtimeService(ProcessInstanceRepository instances,
                                             ActiveTaskRepository tasks,
                                             HistoryTaskRepository legacyHistories,
                                             RuntimeDefinitionLoader definitions,
                                             RuntimeRequestValidator validator,
                                             ProcessOperationRecordRepository operations,
                                             RuntimeNodeAdvancer advancer,
                                             AttachmentService attachments,
                                             CallbackService callbacks,
                                             RuntimeStateValidator stateValidator,
                                             HistoryTaskWriter histories,
                                             RuntimeTransactionExecutor transactions,
                                             InstanceTaskCancellationService cancellation,
                                             ProcessInstanceDeletionRepository deletion,
                                             ProcessDefinitionRepository definitionRepository) {
            return new DefaultProcessRuntimeService(instances, tasks, legacyHistories, definitions, validator,
                    new RuntimeOperationExecutor(new OperationIdempotencyService(operations)), advancer,
                    attachments, callbacks, stateValidator, histories, transactions, cancellation, deletion,
                    definitionRepository);
        }

    }

    static final class MutableCurrentUserProvider implements CurrentUserProvider {
        private volatile UserContext current;

        MutableCurrentUserProvider(UserContext current) {
            this.current = current;
        }

        @Override
        public UserContext getCurrentUser() {
            return current;
        }

        void setCurrent(UserContext current) {
            this.current = current;
        }
    }

    static final class PassingAttachmentService implements AttachmentService {
        @Override
        public AttachmentDTO saveInstanceAttachment(SaveInstanceAttachmentRequest request) {
            return new AttachmentDTO();
        }

        @Override
        public AttachmentDTO saveTaskAttachment(SaveTaskAttachmentRequest request) {
            return new AttachmentDTO();
        }

        @Override
        public AttachmentDownloadDTO downloadAttachment(DownloadAttachmentRequest request) {
            throw new UnsupportedOperationException("not required by M0-M3 E2E");
        }

        @Override
        public List<AttachmentDTO> queryAttachments(AttachmentQuery query) {
            return new ArrayList<AttachmentDTO>();
        }

        @Override
        public void deleteAttachment(DeleteAttachmentRequest request) {
            throw new UnsupportedOperationException("not required by M0-M3 E2E");
        }

        @Override
        public AttachmentTemplateCheckResult checkRequiredAttachments(CheckAttachmentRequest request) {
            AttachmentTemplateCheckResult result = new AttachmentTemplateCheckResult();
            result.setPassed(true);
            return result;
        }
    }
}
