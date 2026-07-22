package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.AttachmentTemplateCheckResult;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.AttachmentConfigStatusEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.request.ApproveTaskRequest;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.request.SubmitTaskRequest;
import com.flowmind.platform.api.request.UpdateVariablesRequest;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.HistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M2 运行时服务的动作编排单元测试。
 */
class DefaultProcessRuntimeServiceTest {

    private ProcessInstanceRepository instanceRepository;
    private ActiveTaskRepository activeTaskRepository;
    private HistoryTaskRepository historyTaskRepository;
    private RuntimeDefinitionLoader definitionLoader;
    private RuntimeRequestValidator requestValidator;
    private RuntimeOperationExecutor operationExecutor;
    private RuntimeNodeAdvancer nodeAdvancer;
    private AttachmentService attachmentService;
    private CallbackService callbackService;
    private DefaultProcessRuntimeService service;

    @BeforeEach
    void setUp() {
        instanceRepository = mock(ProcessInstanceRepository.class);
        activeTaskRepository = mock(ActiveTaskRepository.class);
        historyTaskRepository = mock(HistoryTaskRepository.class);
        definitionLoader = mock(RuntimeDefinitionLoader.class);
        requestValidator = mock(RuntimeRequestValidator.class);
        operationExecutor = mock(RuntimeOperationExecutor.class);
        nodeAdvancer = mock(RuntimeNodeAdvancer.class);
        attachmentService = mock(AttachmentService.class);
        callbackService = mock(CallbackService.class);
        service = new DefaultProcessRuntimeService(instanceRepository, activeTaskRepository, historyTaskRepository,
                definitionLoader, requestValidator, operationExecutor, nodeAdvancer, attachmentService,
                callbackService);
    }

    @Test
    void startProcessCreatesOnlyNotStartedInstanceAndPersistsReplayResult() {
        StartProcessRequest request = startRequest("operation-start-only");
        UserContext starter = user("starter", "Starter");
        ProcessDefinitionDetailDTO definition = definition(starterTask("apply"));
        when(requestValidator.validateStart(request)).thenReturn(starter);
        when(operationExecutor.begin(eq(request), eq(RuntimeOperationTypes.START_PROCESS), eq("starter"), isNull(), isNull(),
                any(LocalDateTime.class))).thenReturn(newDecision());
        when(definitionLoader.loadForStart("expense")).thenReturn(definition);
        when(instanceRepository.insert(any(ProcessInstanceEntity.class))).thenReturn(1);

        ProcessInstanceDTO result = service.startProcess(request);

        assertEquals(InstanceStatusEnum.NOT_STARTED, result.getInstanceStatus());
        assertEquals(0, result.getCreatedTasks().size());
        assertEquals(null, result.getStartedAt());
        ArgumentCaptor<ProcessInstanceEntity> instanceCaptor = ArgumentCaptor.forClass(ProcessInstanceEntity.class);
        verify(instanceRepository).insert(instanceCaptor.capture());
        assertEquals("NOT_STARTED", instanceCaptor.getValue().getInstanceStatus());
        assertEquals("Starter", instanceCaptor.getValue().getStarterUserName());
        verify(nodeAdvancer, never()).advanceToNode(any(ProcessInstanceEntity.class), any(ProcessDefinitionDetailDTO.class),
                anyString(), isNull(), isNull());
        verify(operationExecutor).bindTarget(eq(request.getOperationId()), eq(result.getInstanceId()), isNull());
        verify(operationExecutor).markSuccess(request.getOperationId(), result);
    }

    @Test
    void startAndSubmitAdvancesFromStartAndReturnsCreatedTaskSnapshot() {
        StartProcessRequest request = startRequest("operation-start-running");
        UserContext starter = user("starter", "Starter");
        ProcessDefinitionDetailDTO definition = definition(starterTask("apply"));
        ProcessInstanceEntity persisted = runningInstance("instance-1");
        when(requestValidator.validateStart(request)).thenReturn(starter);
        when(operationExecutor.begin(eq(request), eq(RuntimeOperationTypes.START_AND_SUBMIT), eq("starter"), isNull(), isNull(),
                any(LocalDateTime.class))).thenReturn(newDecision());
        when(definitionLoader.loadForStart("expense")).thenReturn(definition);
        when(instanceRepository.insert(any(ProcessInstanceEntity.class))).thenReturn(1);
        when(nodeAdvancer.advanceToNode(any(ProcessInstanceEntity.class), eq(definition), eq("apply"), isNull(), isNull()))
                .thenReturn(new RuntimeAdvanceResult());
        when(instanceRepository.findById(anyString())).thenReturn(persisted);

        ProcessInstanceDTO result = service.startAndSubmit(request);

        assertEquals("instance-1", result.getInstanceId());
        assertEquals(InstanceStatusEnum.RUNNING, result.getInstanceStatus());
        assertTrue(result.getCreatedTasks().isEmpty());
        verify(nodeAdvancer).advanceToNode(any(ProcessInstanceEntity.class), eq(definition), eq("apply"), isNull(), isNull());
        verify(callbackService).publishCallback(any(com.flowmind.platform.api.dto.WorkflowEvent.class));
        verify(operationExecutor).markSuccess(request.getOperationId(), result);
    }

    @Test
    void startProcessFreezesTheActivatedAttachmentConfigurationGroup() {
        StartProcessRequest request = startRequest("operation-start-with-attachments");
        UserContext starter = user("starter", "Starter");
        ProcessDefinitionDetailDTO definition = definition(starterTask("apply"));
        ProcessAttachmentTemplateDTO attachment = new ProcessAttachmentTemplateDTO();
        attachment.setAttachmentConfigId("attachment-group-001");
        attachment.setConfigStatus(AttachmentConfigStatusEnum.ACTIVE);
        definition.setAttachmentTemplates(Collections.singletonList(attachment));
        when(requestValidator.validateStart(request)).thenReturn(starter);
        when(operationExecutor.begin(eq(request), eq(RuntimeOperationTypes.START_PROCESS), eq("starter"), isNull(), isNull(),
                any(LocalDateTime.class))).thenReturn(newDecision());
        when(definitionLoader.loadForStart("expense")).thenReturn(definition);
        when(instanceRepository.insert(any(ProcessInstanceEntity.class))).thenReturn(1);

        service.startProcess(request);

        ArgumentCaptor<ProcessInstanceEntity> instanceCaptor = ArgumentCaptor.forClass(ProcessInstanceEntity.class);
        verify(instanceRepository).insert(instanceCaptor.capture());
        assertEquals("attachment-group-001", instanceCaptor.getValue().getAttachmentConfigId());
    }

    @Test
    void submitTaskSavesAndChecksAttachmentsBeforeTaskCompareAndSetThenArchivesTask() {
        SubmitTaskRequest request = submitRequest("operation-submit", "task-apply");
        request.setVariables(Collections.<String, Object>singletonMap("amount", Integer.valueOf(200)));
        AttachmentUploadItem attachment = new AttachmentUploadItem();
        attachment.setAttachmentCode("receipt");
        request.setAttachments(Collections.singletonList(attachment));
        UserContext starter = user("starter", "Starter");
        ProcessActiveTaskEntity task = activeTask("task-apply", "instance-1", "apply");
        ProcessInstanceEntity instance = runningInstance("instance-1");
        instance.setVariablesJson("{\"currency\":\"CNY\"}");
        ProcessDefinitionDetailDTO definition = definition(starterTask("apply"));
        AttachmentTemplateCheckResult attachmentCheck = new AttachmentTemplateCheckResult();
        attachmentCheck.setPassed(true);
        prepareTaskAction(request, ActionTypeEnum.SEND, starter, task, instance, definition);
        when(attachmentService.checkRequiredAttachments(any())).thenReturn(attachmentCheck);
        when(activeTaskRepository.complete("task-apply", 0L)).thenReturn(1);
        when(instanceRepository.updateVariablesJson(eq("instance-1"), anyString())).thenReturn(1);
        when(historyTaskRepository.insert(any(ProcessHistoryTaskEntity.class))).thenReturn(1);
        when(nodeAdvancer.advanceToNode(eq(instance), eq(definition), eq("end"), isNull(), isNull()))
                .thenReturn(new RuntimeAdvanceResult());
        when(instanceRepository.findById("instance-1")).thenReturn(instance, instance);

        TaskActionResult result = service.submitTask(request);

        assertEquals("operation-submit", result.getOperationId());
        assertEquals(ActionTypeEnum.SEND, result.getArchivedTasks().get(0).getActionType());
        assertEquals(Integer.valueOf(200), result.getInstance().getVariables().get("amount"));
        assertEquals("CNY", result.getInstance().getVariables().get("currency"));
        InOrder inOrder = inOrder(attachmentService, activeTaskRepository);
        inOrder.verify(attachmentService).saveTaskAttachment(any());
        inOrder.verify(attachmentService).checkRequiredAttachments(any());
        inOrder.verify(activeTaskRepository).complete("task-apply", 0L);
        verify(historyTaskRepository).insert(any(ProcessHistoryTaskEntity.class));
        verify(operationExecutor).markSuccess(request.getOperationId(), result);
    }

    @Test
    void approveCompletesNonStarterTaskWithoutWritingVariables() {
        ApproveTaskRequest request = approveRequest("operation-approve", "task-manager");
        UserContext manager = user("manager", "Manager");
        ProcessActiveTaskEntity task = activeTask("task-manager", "instance-1", "manager");
        ProcessInstanceEntity instance = runningInstance("instance-1");
        ProcessDefinitionDetailDTO definition = definition(userTask("manager", ApproverRuleTypeEnum.ROLE));
        prepareTaskAction(request, ActionTypeEnum.APPROVE, manager, task, instance, definition);
        when(activeTaskRepository.complete("task-manager", 0L)).thenReturn(1);
        when(historyTaskRepository.insert(any(ProcessHistoryTaskEntity.class))).thenReturn(1);
        when(nodeAdvancer.advanceToNode(eq(instance), eq(definition), eq("end"), isNull(), isNull()))
                .thenReturn(new RuntimeAdvanceResult());
        when(instanceRepository.findById("instance-1")).thenReturn(instance, instance);

        TaskActionResult result = service.approve(request);

        assertEquals(ActionTypeEnum.APPROVE, result.getArchivedTasks().get(0).getActionType());
        verify(instanceRepository, never()).updateVariablesJson(anyString(), anyString());
        verify(attachmentService, never()).checkRequiredAttachments(any());
        verify(activeTaskRepository).complete("task-manager", 0L);
        verify(operationExecutor).markSuccess(request.getOperationId(), result);
    }

    @Test
    void updateVariablesMergesCurrentSnapshotAndUsesDedicatedIdempotencyAction() {
        UpdateVariablesRequest request = new UpdateVariablesRequest();
        request.setOperationId("operation-variables");
        request.setInstanceId("instance-1");
        request.setOperatorUserId("starter");
        request.setVariables(Collections.<String, Object>singletonMap("amount", Integer.valueOf(300)));
        UserContext starter = user("starter", "Starter");
        ProcessInstanceEntity instance = runningInstance("instance-1");
        instance.setInstanceStatus(InstanceStatusEnum.NOT_STARTED.name());
        instance.setVariablesJson("{\"currency\":\"CNY\",\"amount\":100}");
        when(requestValidator.validateVariableUpdateIdentity(request)).thenReturn(starter);
        when(operationExecutor.begin(eq(request), eq(RuntimeOperationTypes.UPDATE_VARIABLES), eq("starter"),
                eq("instance-1"), isNull(), any(LocalDateTime.class))).thenReturn(newDecision());
        when(instanceRepository.findById("instance-1")).thenReturn(instance);
        when(instanceRepository.updateVariablesJson(eq("instance-1"), anyString())).thenReturn(1);

        ProcessInstanceDTO result = service.updateVariables(request);

        assertEquals(Integer.valueOf(300), result.getVariables().get("amount"));
        assertEquals("CNY", result.getVariables().get("currency"));
        verify(requestValidator).validateVariableUpdate(request, instance, starter);
        verify(operationExecutor).markSuccess(request.getOperationId(), result);
    }

    @Test
    void getInstanceAggregatesStableActiveHistoryAndNonEmptyComments() {
        ProcessInstanceEntity instance = runningInstance("instance-1");
        ProcessDefinitionDetailDTO definition = definition(userTask("manager", ApproverRuleTypeEnum.ROLE));
        ProcessActiveTaskEntity active = activeTask("task-manager", "instance-1", "manager");
        ProcessHistoryTaskEntity history = new ProcessHistoryTaskEntity();
        history.setId("history-1");
        history.setInstanceId("instance-1");
        history.setOperationId("operation-1");
        history.setActiveTaskId("task-apply");
        history.setNodeCode("apply");
        history.setAssigneeUserId("starter");
        history.setAssigneeUserName("Starter");
        history.setHandleType("NORMAL");
        history.setActionType("SEND");
        history.setCommentText("submitted");
        history.setVariablesSnapshot("{}");
        history.setCompletedAt(LocalDateTime.of(2026, 7, 22, 10, 0));
        when(instanceRepository.findById("instance-1")).thenReturn(instance);
        when(definitionLoader.loadForInstance(instance)).thenReturn(definition);
        when(activeTaskRepository.findOpenByInstanceId("instance-1")).thenReturn(Collections.singletonList(active));
        when(historyTaskRepository.findByInstanceId("instance-1")).thenReturn(Collections.singletonList(history));

        ProcessInstanceDetailDTO detail = service.getInstance("instance-1");

        assertEquals(1, detail.getActiveTasks().size());
        assertEquals("Manager", detail.getActiveTasks().get(0).getNodeName());
        assertEquals(1, detail.getHistoryTasks().size());
        assertEquals(1, detail.getComments().size());
        assertEquals("submitted", detail.getComments().get(0).getComment());
        assertFalse(detail.getCreatedTasks() == null);
    }

    @Test
    void successfulSubmitReplayDoesNotReadTerminalTaskOrRepeatSideEffects() {
        SubmitTaskRequest request = submitRequest("operation-submit-replay", "task-apply");
        UserContext starter = user("starter", "Starter");
        OperationIdempotencyDecision replayDecision = new OperationIdempotencyDecision(
                OperationIdempotencyDecisionType.REPLAY_SUCCESS, null);
        TaskActionResult replayed = new TaskActionResult();
        replayed.setOperationId(request.getOperationId());
        replayed.setReplayed(true);
        when(requestValidator.validateTaskIdentity(request)).thenReturn(starter);
        when(operationExecutor.begin(eq(request), eq(RuntimeOperationTypes.SUBMIT_TASK), eq("starter"), isNull(),
                eq("task-apply"), any(LocalDateTime.class))).thenReturn(replayDecision);
        when(operationExecutor.replayTaskAction(replayDecision)).thenReturn(replayed);

        TaskActionResult result = service.submitTask(request);

        assertTrue(result.isReplayed());
        verify(activeTaskRepository, never()).findById(anyString());
        verify(activeTaskRepository, never()).complete(anyString(), anyLong());
        verify(historyTaskRepository, never()).insert(any(ProcessHistoryTaskEntity.class));
        verify(callbackService, never()).publishCallback(any(com.flowmind.platform.api.dto.WorkflowEvent.class));
    }

    @Test
    void successfulApproveReplayDoesNotReadTerminalTaskOrRepeatSideEffects() {
        ApproveTaskRequest request = approveRequest("operation-approve-replay", "task-manager");
        UserContext manager = user("manager", "Manager");
        OperationIdempotencyDecision replayDecision = new OperationIdempotencyDecision(
                OperationIdempotencyDecisionType.REPLAY_SUCCESS, null);
        TaskActionResult replayed = new TaskActionResult();
        replayed.setOperationId(request.getOperationId());
        replayed.setReplayed(true);
        when(requestValidator.validateTaskIdentity(request)).thenReturn(manager);
        when(operationExecutor.begin(eq(request), eq(RuntimeOperationTypes.APPROVE_TASK), eq("manager"), isNull(),
                eq("task-manager"), any(LocalDateTime.class))).thenReturn(replayDecision);
        when(operationExecutor.replayTaskAction(replayDecision)).thenReturn(replayed);

        TaskActionResult result = service.approve(request);

        assertTrue(result.isReplayed());
        verify(activeTaskRepository, never()).findById(anyString());
        verify(activeTaskRepository, never()).complete(anyString(), anyLong());
        verify(historyTaskRepository, never()).insert(any(ProcessHistoryTaskEntity.class));
        verify(callbackService, never()).publishCallback(any(com.flowmind.platform.api.dto.WorkflowEvent.class));
    }

    private void prepareTaskAction(com.flowmind.platform.api.request.TaskOperationRequest request,
                                   ActionTypeEnum actionType,
                                   UserContext operator,
                                   ProcessActiveTaskEntity task,
                                   ProcessInstanceEntity instance,
                                   ProcessDefinitionDetailDTO definition) {
        when(requestValidator.validateTaskIdentity(request)).thenReturn(operator);
        when(operationExecutor.begin(eq(request), eq(actionType.name()), eq(operator.getUserId()), isNull(),
                eq(task.getId()), any(LocalDateTime.class))).thenReturn(newDecision());
        when(activeTaskRepository.findById(task.getId())).thenReturn(task);
        when(instanceRepository.findById(instance.getId())).thenReturn(instance);
        when(definitionLoader.loadForInstance(instance)).thenReturn(definition);
    }

    private OperationIdempotencyDecision newDecision() {
        return new OperationIdempotencyDecision(OperationIdempotencyDecisionType.NEW, null);
    }

    private StartProcessRequest startRequest(String operationId) {
        StartProcessRequest request = new StartProcessRequest();
        request.setOperationId(operationId);
        request.setProcessCode("expense");
        request.setInstanceTitle("Expense request");
        request.setStarterUserId("starter");
        request.setStarterDeptId("dept-1");
        request.setVariables(Collections.<String, Object>singletonMap("amount", Integer.valueOf(100)));
        return request;
    }

    private SubmitTaskRequest submitRequest(String operationId, String taskId) {
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setOperationId(operationId);
        request.setTaskId(taskId);
        request.setExpectedTaskVersion(Long.valueOf(0L));
        request.setOperatorUserId("starter");
        request.setComment("submitted");
        return request;
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

    private ProcessInstanceEntity runningInstance(String instanceId) {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId(instanceId);
        instance.setDefinitionId("definition-1");
        instance.setProcessCode("expense");
        instance.setProcessName("Expense");
        instance.setVersion(Integer.valueOf(1));
        instance.setInstanceTitle("Expense request");
        instance.setStarterUserId("starter");
        instance.setStarterUserName("Starter");
        instance.setStarterDeptId("dept-1");
        instance.setCurrentNodeCodes("[]");
        instance.setVariablesJson("{}");
        instance.setInstanceStatus(InstanceStatusEnum.RUNNING.name());
        instance.setStartedAt(LocalDateTime.of(2026, 7, 22, 9, 0));
        return instance;
    }

    private ProcessActiveTaskEntity activeTask(String taskId, String instanceId, String nodeCode) {
        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setId(taskId);
        task.setInstanceId(instanceId);
        task.setDefinitionId("definition-1");
        task.setNodeCode(nodeCode);
        task.setCandidateUserIds("[\"starter\",\"manager\"]");
        task.setTaskStatus("ACTIVE");
        task.setLockVersion(Long.valueOf(0L));
        task.setCreatedAt(LocalDateTime.of(2026, 7, 22, 9, 0));
        return task;
    }

    private ProcessDefinitionDetailDTO definition(ProcessNodeDTO taskNode) {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setId("definition-1");
        definition.setProcessCode("expense");
        definition.setProcessName("Expense");
        definition.setVersion(Integer.valueOf(1));
        ProcessNodeDTO start = new ProcessNodeDTO();
        start.setNodeCode("start");
        start.setNodeName("Start");
        start.setNodeType(NodeTypeEnum.START);
        ProcessNodeDTO end = new ProcessNodeDTO();
        end.setNodeCode("end");
        end.setNodeName("End");
        end.setNodeType(NodeTypeEnum.END);
        definition.setNodes(Arrays.asList(start, taskNode, end));
        ProcessEdgeDTO startEdge = edge("edge-start", "start", taskNode.getNodeCode());
        ProcessEdgeDTO endEdge = edge("edge-end", taskNode.getNodeCode(), "end");
        definition.setEdges(Arrays.asList(startEdge, endEdge));
        return definition;
    }

    private ProcessNodeDTO starterTask(String nodeCode) {
        return userTask(nodeCode, ApproverRuleTypeEnum.STARTER);
    }

    private ProcessNodeDTO userTask(String nodeCode, ApproverRuleTypeEnum ruleType) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(nodeCode);
        node.setNodeName("apply".equals(nodeCode) ? "Apply" : "Manager");
        node.setNodeType(NodeTypeEnum.USER_TASK);
        node.setApproverRuleType(ruleType);
        node.setMultiInstanceMode(MultiInstanceModeEnum.SINGLE);
        return node;
    }

    private ProcessEdgeDTO edge(String edgeCode, String source, String target) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode(edgeCode);
        edge.setSourceNodeCode(source);
        edge.setTargetNodeCode(target);
        return edge;
    }

    private UserContext user(String id, String name) {
        return new UserContext(id, name, "dept-1", "Department");
    }
}
