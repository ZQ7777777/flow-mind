package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.request.ForceCompleteRequest;
import com.flowmind.platform.api.request.JumpNodeRequest;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M3 管理员命令服务的单元测试。 */
class DefaultAdminProcessServiceTest {

    private ProcessInstanceRepository instanceRepository;
    private RuntimeDefinitionLoader definitionLoader;
    private RuntimeRequestValidator requestValidator;
    private RuntimeOperationExecutor operationExecutor;
    private RuntimeNodeAdvancer nodeAdvancer;
    private InstanceTaskCancellationService taskCancellationService;
    private ProcessDefinitionRepository definitionRepository;
    private CallbackService callbackService;
    private RuntimeTransactionExecutor transactionExecutor;
    private DefaultAdminProcessService service;

    @BeforeEach
    void setUp() {
        instanceRepository = mock(ProcessInstanceRepository.class);
        definitionLoader = mock(RuntimeDefinitionLoader.class);
        requestValidator = mock(RuntimeRequestValidator.class);
        operationExecutor = mock(RuntimeOperationExecutor.class);
        nodeAdvancer = mock(RuntimeNodeAdvancer.class);
        taskCancellationService = mock(InstanceTaskCancellationService.class);
        definitionRepository = mock(ProcessDefinitionRepository.class);
        callbackService = mock(CallbackService.class);
        transactionExecutor = mock(RuntimeTransactionExecutor.class);
        doAnswer(invocation -> ((RuntimeTransactionWork<?>) invocation.getArgument(0)).execute())
                .when(transactionExecutor).execute(any(RuntimeTransactionWork.class));
        service = new DefaultAdminProcessService(instanceRepository, definitionLoader, requestValidator,
                operationExecutor, nodeAdvancer, taskCancellationService, definitionRepository, callbackService,
                transactionExecutor);
    }

    @Test
    void jumpCancelsOpenWorkThenCreatesTargetNodeTasks() {
        JumpNodeRequest request = jumpRequest("operation-jump", "review");
        UserContext operator = operator();
        ProcessInstanceEntity instance = runningInstance();
        ProcessDefinitionDetailDTO definition = definition(NodeTypeEnum.USER_TASK);
        RuntimeAdvancePreparation preparation = new RuntimeAdvancePreparation(
                Collections.<String, List<String>>emptyMap(), Collections.<String, String>emptyMap());
        RuntimeAdvanceResult advanceResult = new RuntimeAdvanceResult();
        TaskDTO created = new TaskDTO();
        created.setTaskId("task-review");
        created.setInstanceId("instance-1");
        created.setNodeCode("review");
        advanceResult.addCreatedTask(created);
        when(requestValidator.validateInstanceOperationIdentity(request, "instance-1", "admin"))
                .thenReturn(operator);
        when(operationExecutor.begin(eq(request), eq(RuntimeOperationTypes.JUMP), eq("admin"), eq("instance-1"),
                isNull(), any(LocalDateTime.class))).thenReturn(newDecision());
        when(instanceRepository.findById("instance-1")).thenReturn(instance);
        when(definitionLoader.loadForInstance(instance)).thenReturn(definition);
        when(nodeAdvancer.prepareAdvance(instance, definition, "review", null, null)).thenReturn(preparation);
        when(nodeAdvancer.advanceToNode(instance, definition, "review", null, null, preparation))
                .thenReturn(advanceResult);
        when(taskCancellationService.cancelOpenWork(eq(instance), eq(operator), eq(ActionTypeEnum.JUMP),
                eq("manual correction"), eq("operation-jump"), any(Map.class)))
                .thenReturn(Collections.emptyList());
        when(definitionRepository.insertAuditLog(anyString(), eq("instance-1"), eq("operation-jump"), anyString(),
                eq("instance-1"), eq(ActionTypeEnum.JUMP.name()), eq("admin"), anyString(),
                any(LocalDateTime.class))).thenReturn(1);

        TaskActionResult result = service.jumpToNode(request);

        assertEquals("operation-jump", result.getOperationId());
        assertEquals(1, result.getCreatedTasks().size());
        assertFalse(result.isReplayed());
        verify(taskCancellationService).cancelOpenWork(eq(instance), eq(operator), eq(ActionTypeEnum.JUMP),
                eq("manual correction"), eq("operation-jump"), any(Map.class));
        verify(callbackService).publishCallback(any(com.flowmind.platform.api.dto.WorkflowEvent.class));
        verify(operationExecutor).markSuccess("operation-jump", result);
    }

    @Test
    void forceCompleteCancelsOpenWorkAndCompletesOnlyRunningInstance() {
        ForceCompleteRequest request = forceCompleteRequest("operation-force-complete");
        UserContext operator = operator();
        ProcessInstanceEntity instance = runningInstance();
        when(requestValidator.validateInstanceOperationIdentity(request, "instance-1", "admin"))
                .thenReturn(operator);
        when(operationExecutor.begin(eq(request), eq(RuntimeOperationTypes.FORCE_COMPLETE), eq("admin"),
                eq("instance-1"), isNull(), any(LocalDateTime.class))).thenReturn(newDecision());
        when(instanceRepository.findById("instance-1")).thenReturn(instance);
        when(taskCancellationService.cancelOpenWork(eq(instance), eq(operator), eq(ActionTypeEnum.FORCE_COMPLETE),
                eq("close by administrator"), eq("operation-force-complete"), any(Map.class)))
                .thenReturn(Collections.emptyList());
        when(instanceRepository.forceComplete(eq("instance-1"), any(LocalDateTime.class))).thenReturn(1);
        when(definitionRepository.insertAuditLog(anyString(), eq("instance-1"), eq("operation-force-complete"),
                anyString(), eq("instance-1"), eq(ActionTypeEnum.FORCE_COMPLETE.name()), eq("admin"),
                anyString(), any(LocalDateTime.class))).thenReturn(1);

        ProcessInstanceDTO result = service.forceComplete(request);

        assertEquals(InstanceStatusEnum.COMPLETED, result.getInstanceStatus());
        assertTrue(result.getCurrentNodeCodes().isEmpty());
        verify(taskCancellationService).cancelOpenWork(eq(instance), eq(operator), eq(ActionTypeEnum.FORCE_COMPLETE),
                eq("close by administrator"), eq("operation-force-complete"), any(Map.class));
        verify(callbackService).publishCallback(any(com.flowmind.platform.api.dto.WorkflowEvent.class));
        verify(operationExecutor).markSuccess("operation-force-complete", result);
    }

    @Test
    void malformedVariablesMarkAdminOperationAsDeterministicFailure() {
        ForceCompleteRequest request = forceCompleteRequest("operation-invalid-variables");
        UserContext operator = operator();
        ProcessInstanceEntity instance = runningInstance();
        instance.setVariablesJson("[]");
        when(requestValidator.validateInstanceOperationIdentity(request, "instance-1", "admin"))
                .thenReturn(operator);
        when(operationExecutor.begin(eq(request), eq(RuntimeOperationTypes.FORCE_COMPLETE), eq("admin"),
                eq("instance-1"), isNull(), any(LocalDateTime.class))).thenReturn(newDecision());
        when(instanceRepository.findById("instance-1")).thenReturn(instance);

        RuntimeStateException error = assertThrows(RuntimeStateException.class, () -> service.forceComplete(request));

        assertEquals(RuntimeErrorCodes.DEFINITION_INVALID, error.getErrorCode());
        verify(operationExecutor).markDeterministicFailure("operation-invalid-variables",
                RuntimeErrorCodes.DEFINITION_INVALID);
        verify(taskCancellationService, never()).cancelOpenWork(any(ProcessInstanceEntity.class), any(UserContext.class),
                any(ActionTypeEnum.class), anyString(), anyString(), any(Map.class));
    }

    @Test
    void jumpRejectsParallelJoinAsDirectTargetBeforeCancellingWork() {
        JumpNodeRequest request = jumpRequest("operation-invalid-jump", "review");
        UserContext operator = operator();
        ProcessInstanceEntity instance = runningInstance();
        when(requestValidator.validateInstanceOperationIdentity(request, "instance-1", "admin"))
                .thenReturn(operator);
        when(operationExecutor.begin(eq(request), eq(RuntimeOperationTypes.JUMP), eq("admin"), eq("instance-1"),
                isNull(), any(LocalDateTime.class))).thenReturn(newDecision());
        when(instanceRepository.findById("instance-1")).thenReturn(instance);
        when(definitionLoader.loadForInstance(instance)).thenReturn(definition(NodeTypeEnum.PARALLEL_JOIN_GATEWAY));

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> service.jumpToNode(request));

        assertEquals(RuntimeErrorCodes.INVALID_ACTION, error.getErrorCode());
        verify(taskCancellationService, never()).cancelOpenWork(any(ProcessInstanceEntity.class), any(UserContext.class),
                any(ActionTypeEnum.class), anyString(), anyString(), any(Map.class));
    }

    @Test
    void jumpToEndPublishesJumpAndCompletionEvents() {
        JumpNodeRequest request = jumpRequest("operation-jump-end", "review");
        UserContext operator = operator();
        ProcessInstanceEntity running = runningInstance();
        ProcessInstanceEntity completed = runningInstance();
        completed.setInstanceStatus(InstanceStatusEnum.COMPLETED.name());
        completed.setCurrentNodeCodes("[]");
        completed.setEndedAt(LocalDateTime.of(2026, 7, 23, 10, 0));
        RuntimeAdvancePreparation preparation = new RuntimeAdvancePreparation(
                Collections.<String, List<String>>emptyMap(), Collections.<String, String>emptyMap());
        RuntimeAdvanceResult advanceResult = new RuntimeAdvanceResult();
        advanceResult.markInstanceCompleted();
        when(requestValidator.validateInstanceOperationIdentity(request, "instance-1", "admin"))
                .thenReturn(operator);
        when(operationExecutor.begin(eq(request), eq(RuntimeOperationTypes.JUMP), eq("admin"), eq("instance-1"),
                isNull(), any(LocalDateTime.class))).thenReturn(newDecision());
        when(instanceRepository.findById("instance-1")).thenReturn(running, completed);
        when(definitionLoader.loadForInstance(running)).thenReturn(definition(NodeTypeEnum.END));
        when(nodeAdvancer.prepareAdvance(eq(running), any(ProcessDefinitionDetailDTO.class), eq("review"),
                isNull(), isNull())).thenReturn(preparation);
        when(nodeAdvancer.advanceToNode(eq(running), any(ProcessDefinitionDetailDTO.class), eq("review"),
                isNull(), isNull(), eq(preparation))).thenReturn(advanceResult);
        when(taskCancellationService.cancelOpenWork(eq(running), eq(operator), eq(ActionTypeEnum.JUMP),
                eq("manual correction"), eq("operation-jump-end"), any(Map.class)))
                .thenReturn(Collections.emptyList());
        when(definitionRepository.insertAuditLog(anyString(), eq("instance-1"), eq("operation-jump-end"),
                anyString(), eq("instance-1"), eq(ActionTypeEnum.JUMP.name()), eq("admin"), anyString(),
                any(LocalDateTime.class))).thenReturn(1);

        ProcessInstanceDTO result = service.jumpToNode(request).getInstance();

        assertEquals(InstanceStatusEnum.COMPLETED, result.getInstanceStatus());
        ArgumentCaptor<com.flowmind.platform.api.dto.WorkflowEvent> events = ArgumentCaptor.forClass(
                com.flowmind.platform.api.dto.WorkflowEvent.class);
        verify(callbackService, times(2)).publishCallback(events.capture());
        assertEquals(com.flowmind.platform.api.enums.WorkflowEventTypeEnum.PROCESS_JUMPED,
                events.getAllValues().get(0).getEventType());
        assertEquals(com.flowmind.platform.api.enums.WorkflowEventTypeEnum.PROCESS_COMPLETED,
                events.getAllValues().get(1).getEventType());
    }

    private OperationIdempotencyDecision newDecision() {
        return new OperationIdempotencyDecision(OperationIdempotencyDecisionType.NEW, null);
    }

    private UserContext operator() {
        return new UserContext("admin", "Administrator", "ops", "Operations");
    }

    private ProcessInstanceEntity runningInstance() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId("instance-1");
        instance.setDefinitionId("definition-1");
        instance.setProcessCode("expense");
        instance.setProcessName("Expense");
        instance.setVersion(Integer.valueOf(1));
        instance.setInstanceTitle("Expense request");
        instance.setStarterUserId("starter");
        instance.setStarterUserName("Starter");
        instance.setCurrentNodeCodes("[\"apply\"]");
        instance.setVariablesJson("{}");
        instance.setInstanceStatus(InstanceStatusEnum.RUNNING.name());
        instance.setStartedAt(LocalDateTime.of(2026, 7, 23, 9, 0));
        return instance;
    }

    private ProcessDefinitionDetailDTO definition(NodeTypeEnum targetNodeType) {
        ProcessNodeDTO start = node("start", NodeTypeEnum.START);
        ProcessNodeDTO target = node("review", targetNodeType);
        ProcessNodeDTO end = node("end", NodeTypeEnum.END);
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setId("definition-1");
        definition.setProcessCode("expense");
        definition.setProcessName("Expense");
        definition.setVersion(Integer.valueOf(1));
        definition.setNodes(java.util.Arrays.asList(start, target, end));
        definition.setEdges(java.util.Arrays.asList(edge("edge-start", "start", "review"),
                edge("edge-end", "review", "end")));
        return definition;
    }

    private ProcessNodeDTO node(String nodeCode, NodeTypeEnum nodeType) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(nodeCode);
        node.setNodeName(nodeCode);
        node.setNodeType(nodeType);
        return node;
    }

    private ProcessEdgeDTO edge(String edgeCode, String sourceNodeCode, String targetNodeCode) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode(edgeCode);
        edge.setSourceNodeCode(sourceNodeCode);
        edge.setTargetNodeCode(targetNodeCode);
        return edge;
    }

    private JumpNodeRequest jumpRequest(String operationId, String targetNodeCode) {
        JumpNodeRequest request = new JumpNodeRequest();
        request.setOperationId(operationId);
        request.setInstanceId("instance-1");
        request.setTargetNodeCode(targetNodeCode);
        request.setOperatorUserId("admin");
        request.setComment("manual correction");
        return request;
    }

    private ForceCompleteRequest forceCompleteRequest(String operationId) {
        ForceCompleteRequest request = new ForceCompleteRequest();
        request.setOperationId(operationId);
        request.setInstanceId("instance-1");
        request.setOperatorUserId("admin");
        request.setComment("close by administrator");
        return request;
    }
}
