package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.request.AddSignRequest;
import com.flowmind.platform.api.request.DirectSendRequest;
import com.flowmind.platform.api.request.RejectTaskRequest;
import com.flowmind.platform.api.request.ReturnTaskRequest;
import com.flowmind.platform.api.request.TransferTaskRequest;
import com.flowmind.platform.api.request.WithdrawTaskRequest;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.spi.OrganizationProvider;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.core.task.HistoryArchiveCommand;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M5 增强动作协调器的关键回归测试。 */
class EnhancedTaskActionCoordinatorTest {

    @Test
    void rejectRejectsTargetOutsideConfiguredRuleBeforeCompletingTask() {
        Fixture fixture = fixture(ActionTypeEnum.REJECT);
        RejectTaskRequest request = taskRequest(new RejectTaskRequest(), "op-reject", fixture.task, fixture.operator);
        request.setTargetNodeCode("finance");
        fixture.definition.setNodes(java.util.Arrays.asList(userNode("manager", null), userNode("finance",
                "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"starter\"]}}}")));

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> fixture.coordinator.reject(request));
        assertEquals(RuntimeErrorCodes.REJECT_TARGET_NOT_ALLOWED, error.getErrorCode());
        verify(fixture.tasks, never()).complete(any(String.class), any(Long.class));
    }

    @Test
    void rejectCompletesSourceArchivesRelationAndCreatesTargetTasks() {
        Fixture fixture = fixture(ActionTypeEnum.REJECT);
        RejectTaskRequest request = taskRequest(new RejectTaskRequest(), "op-reject-success", fixture.task, fixture.operator);
        request.setTargetNodeCode("finance");
        fixture.definition.setNodes(java.util.Arrays.asList(userNode("manager",
                "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"finance\"]}}}"),
                userNode("finance", null)));
        ProcessHistoryTaskEntity archived = history("reject-history", "user-a", "manager", ActionTypeEnum.REJECT.name(), "{}");
        when(fixture.tasks.complete("task-1", 3L)).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class))).thenReturn(archived);
        when(fixture.histories.updateExtraJson(eq("reject-history"), any(String.class))).thenReturn(1);
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("finance"), eq(null), eq(null), eq(null)))
                .thenReturn(new RuntimeAdvanceResult());

        TaskActionResult result = fixture.coordinator.reject(request);
        assertNotNull(result);
        verify(fixture.tasks).complete("task-1", 3L);
        verify(fixture.histories).updateExtraJson(eq("reject-history"), any(String.class));
        verify(fixture.advancer).advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("finance"), eq(null), eq(null), eq(null));
    }

    @Test
    void returnToStarterFailsWhenNoStarterHistoryExists() {
        Fixture fixture = fixture(ActionTypeEnum.RETURN);
        ReturnTaskRequest request = taskRequest(new ReturnTaskRequest(), "op-return", fixture.task, fixture.operator);
        when(fixture.histories.findLatestByInstanceAndActions(eq("instance-1"), eq(ActionTypeEnum.SEND.name())))
                .thenReturn(java.util.Collections.<ProcessHistoryTaskEntity>emptyList());

        RuntimeStateException error = assertThrows(RuntimeStateException.class,
                () -> fixture.coordinator.returnToStarter(request));
        assertEquals(RuntimeErrorCodes.WITHDRAW_HISTORY_NOT_FOUND, error.getErrorCode());
        verify(fixture.tasks, never()).complete(any(String.class), any(Long.class));
    }

    @Test
    void returnToStarterCompletesCurrentTaskAndAdvancesToStarterHistoryNode() {
        Fixture fixture = fixture(ActionTypeEnum.RETURN);
        ReturnTaskRequest request = taskRequest(new ReturnTaskRequest(), "op-return-success", fixture.task, fixture.operator);
        ProcessNodeDTO starter = userNode("starter", ApproverRuleTypeEnum.STARTER, null);
        fixture.definition.setNodes(java.util.Arrays.asList(starter, userNode("manager", null)));
        ProcessHistoryTaskEntity target = history("starter-history", "user-a", "starter", ActionTypeEnum.SEND.name(), null);
        ProcessHistoryTaskEntity archived = history("return-history", "user-a", "manager", ActionTypeEnum.RETURN.name(), "{}");
        when(fixture.histories.findLatestByInstanceAndActions(eq("instance-1"), eq(ActionTypeEnum.SEND.name())))
                .thenReturn(java.util.Collections.singletonList(target));
        when(fixture.tasks.complete("task-1", 3L)).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class))).thenReturn(archived);
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("starter"), eq(null), eq(null), eq(null)))
                .thenReturn(new RuntimeAdvanceResult());

        TaskActionResult result = fixture.coordinator.returnToStarter(request);
        assertNotNull(result);
        verify(fixture.tasks).complete("task-1", 3L);
        verify(fixture.advancer).advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("starter"), eq(null), eq(null), eq(null));
    }

    @Test
    void withdrawRejectsOperatorWhoDidNotHandlePreviousTask() {
        Fixture fixture = fixture(ActionTypeEnum.WITHDRAW);
        WithdrawTaskRequest request = taskRequest(new WithdrawTaskRequest(), "op-withdraw", fixture.task, fixture.operator);
        ProcessHistoryTaskEntity previous = history("previous", "reviewer", "manager", ActionTypeEnum.APPROVE.name(), null);
        when(fixture.tasks.countOpenByInstanceId("instance-1")).thenReturn(1L);
        when(fixture.histories.findLatestByInstanceAndActions(eq("instance-1"), any(String.class), any(String.class),
                any(String.class), any(String.class), any(String.class))).thenReturn(java.util.Collections.singletonList(previous));
        fixture.definition.setNodes(java.util.Collections.singletonList(userNode("manager", null)));

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> fixture.coordinator.withdraw(request));
        assertEquals(RuntimeErrorCodes.WITHDRAW_PERMISSION_DENIED, error.getErrorCode());
        verify(fixture.tasks, never()).cancel(any(String.class), any(Long.class));
    }

    @Test
    void withdrawCancelsCurrentTaskAndRecreatesPreviousHandlerNode() {
        Fixture fixture = fixture(ActionTypeEnum.WITHDRAW);
        WithdrawTaskRequest request = taskRequest(new WithdrawTaskRequest(), "op-withdraw-success", fixture.task, fixture.operator);
        fixture.definition.setNodes(java.util.Collections.singletonList(userNode("manager", null)));
        ProcessHistoryTaskEntity previous = history("previous-success", "user-a", "manager", ActionTypeEnum.APPROVE.name(), null);
        ProcessHistoryTaskEntity archived = history("withdraw-history", "user-a", "reviewer", ActionTypeEnum.WITHDRAW.name(), "{}");
        when(fixture.tasks.countOpenByInstanceId("instance-1")).thenReturn(1L);
        when(fixture.histories.findLatestByInstanceAndActions(eq("instance-1"), any(String.class), any(String.class),
                any(String.class), any(String.class), any(String.class))).thenReturn(java.util.Collections.singletonList(previous));
        when(fixture.tasks.cancel("task-1", 3L)).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class))).thenReturn(archived);
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("manager"), eq(null), eq(null), eq(null)))
                .thenReturn(new RuntimeAdvanceResult());

        TaskActionResult result = fixture.coordinator.withdraw(request);
        assertNotNull(result);
        verify(fixture.tasks).cancel("task-1", 3L);
        verify(fixture.advancer).advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("manager"), eq(null), eq(null), eq(null));
    }

    @Test
    void directSendFailsWhenNoVersionedRejectSourceContainsCurrentTask() {
        Fixture fixture = fixture(ActionTypeEnum.DIRECT_SEND);
        DirectSendRequest request = taskRequest(new DirectSendRequest(), "op-direct", fixture.task, fixture.operator);
        request.setTargetNodeCode("manager");
        when(fixture.histories.findLatestByInstanceAndActions(eq("instance-1"), eq(ActionTypeEnum.REJECT.name())))
                .thenReturn(java.util.Collections.<ProcessHistoryTaskEntity>emptyList());

        RuntimeStateException error = assertThrows(RuntimeStateException.class,
                () -> fixture.coordinator.directSend(request));
        assertEquals(RuntimeErrorCodes.DIRECT_SEND_SOURCE_NOT_FOUND, error.getErrorCode());
        verify(fixture.tasks, never()).complete(any(String.class), any(Long.class));
    }

    @Test
    void directSendConsumesRejectSourceAndAdvancesBackToSourceNode() {
        Fixture fixture = fixture(ActionTypeEnum.DIRECT_SEND);
        DirectSendRequest request = taskRequest(new DirectSendRequest(), "op-direct-success", fixture.task, fixture.operator);
        request.setTargetNodeCode("manager");
        fixture.definition.setNodes(java.util.Collections.singletonList(userNode("manager",
                "{\"taskActionRules\":{\"directSend\":{\"enabled\":true,\"targetMode\":\"REJECT_SOURCE\"}}}")));
        ProcessHistoryTaskEntity source = history("reject-source", "user-a", "manager", ActionTypeEnum.REJECT.name(),
                "{\"schemaVersion\":1,\"sourceNodeCode\":\"manager\",\"createdTaskIds\":[\"task-1\"]}");
        ProcessHistoryTaskEntity archived = history("direct-history", "user-a", "manager", ActionTypeEnum.DIRECT_SEND.name(), "{}");
        when(fixture.histories.findLatestByInstanceAndActions(eq("instance-1"), eq(ActionTypeEnum.REJECT.name())))
                .thenReturn(java.util.Collections.singletonList(source));
        when(fixture.tasks.complete("task-1", 3L)).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class))).thenReturn(archived);
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("manager"), eq(null), eq(null), eq(null)))
                .thenReturn(new RuntimeAdvanceResult());

        TaskActionResult result = fixture.coordinator.directSend(request);
        assertNotNull(result);
        verify(fixture.tasks).complete("task-1", 3L);
        verify(fixture.advancer).advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("manager"), eq(null), eq(null), eq(null));
    }

    @Test
    void addSignRejectsDuplicateOrOperatorUserBeforeCancellingSourceTask() {
        Fixture fixture = fixture(ActionTypeEnum.ADD_SIGN);
        AddSignRequest request = taskRequest(new AddSignRequest(), "op-add-sign", fixture.task, fixture.operator);
        request.setAddSignUserIds(java.util.Arrays.asList("user-a", "user-b"));

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> fixture.coordinator.addSign(request));
        assertEquals(RuntimeErrorCodes.INVALID_ACTION, error.getErrorCode());
        verify(fixture.tasks, never()).cancel(any(String.class), any(Long.class));
    }

    @Test
    void addSignCancelsSourceCreatesCounterSignGroupAndTemporaryTask() {
        Fixture fixture = fixture(ActionTypeEnum.ADD_SIGN);
        AddSignRequest request = taskRequest(new AddSignRequest(), "op-add-sign-success", fixture.task, fixture.operator);
        request.setAddSignUserIds(java.util.Collections.singletonList("user-b"));
        when(fixture.organization.findUser("user-b")).thenReturn(Optional.of(new UserDTO("user-b", "User B")));
        when(fixture.tasks.cancel("task-1", 3L)).thenReturn(1);
        when(fixture.groups.insert(any(ProcessTaskGroupEntity.class))).thenReturn(1);
        when(fixture.tasks.insert(any(ProcessActiveTaskEntity.class))).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class)))
                .thenReturn(history("add-sign-history", "user-a", "manager", ActionTypeEnum.ADD_SIGN.name(), "{}"));

        TaskActionResult result = fixture.coordinator.addSign(request);
        assertNotNull(result);
        assertEquals(1, result.getCreatedTasks().size());
        verify(fixture.tasks).cancel("task-1", 3L);
        verify(fixture.groups).insert(any(ProcessTaskGroupEntity.class));
        verify(fixture.tasks).insert(any(ProcessActiveTaskEntity.class));
    }

    @Test
    void transferUpdatesAssigneeRecordsHistoryAndReturnsNewTaskVersion() {
        ProcessInstanceRepository instances = mock(ProcessInstanceRepository.class);
        ActiveTaskRepository tasks = mock(ActiveTaskRepository.class);
        ProcessHistoryTaskRepository histories = mock(ProcessHistoryTaskRepository.class);
        TaskGroupRepository groups = mock(TaskGroupRepository.class);
        RuntimeDefinitionLoader definitions = mock(RuntimeDefinitionLoader.class);
        RuntimeRequestValidator validator = mock(RuntimeRequestValidator.class);
        RuntimeOperationExecutor operations = mock(RuntimeOperationExecutor.class);
        RuntimeNodeAdvancer advancer = mock(RuntimeNodeAdvancer.class);
        RuntimeStateValidator state = mock(RuntimeStateValidator.class);
        HistoryTaskWriter writer = mock(HistoryTaskWriter.class);
        CallbackService callbacks = mock(CallbackService.class);
        OrganizationProvider organization = mock(OrganizationProvider.class);
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("organizationProvider", organization);

        EnhancedTaskActionCoordinator coordinator = new EnhancedTaskActionCoordinator(instances, tasks, histories,
                groups, definitions, validator, operations, advancer, state, writer, null, callbacks,
                factory.getBeanProvider(OrganizationProvider.class),
                factory.getBeanProvider(com.flowmind.platform.persistence.repository.ProcessDefinitionRepository.class));
        TransferTaskRequest request = new TransferTaskRequest();
        request.setOperationId("op-transfer");
        request.setTaskId("task-1");
        request.setExpectedTaskVersion(Long.valueOf(3));
        request.setOperatorUserId("user-a");
        request.setTargetUserId("user-b");
        UserContext operator = new UserContext("user-a", "User A", null, null);
        ProcessInstanceEntity instance = instance();
        ProcessActiveTaskEntity task = task();
        ProcessHistoryTaskEntity archived = new ProcessHistoryTaskEntity();
        archived.setId("history-1");
        archived.setActiveTaskId(task.getId());
        archived.setInstanceId(instance.getId());

        when(validator.validateTaskIdentity(request)).thenReturn(operator);
        when(operations.begin(eq(request), eq(RuntimeOperationTypes.TRANSFER), eq("user-a"), eq(null),
                eq("task-1"), any(LocalDateTime.class)))
                .thenReturn(new OperationIdempotencyDecision(OperationIdempotencyDecisionType.NEW, null));
        when(state.validateTaskAction("task-1", Long.valueOf(3), ActionTypeEnum.TRANSFER, operator))
                .thenReturn(new RuntimeTaskContext(instance, task, ActionTypeEnum.TRANSFER, operator));
        when(definitions.loadForInstance(instance)).thenReturn(new ProcessDefinitionDetailDTO());
        when(organization.findUser("user-b")).thenReturn(Optional.of(new UserDTO("user-b", "User B")));
        when(tasks.transfer("task-1", 3L, "user-b", "User B")).thenReturn(1);
        when(writer.archive(any())).thenReturn(archived);
        when(instances.findById("instance-1")).thenReturn(instance);

        com.flowmind.platform.api.dto.TaskActionResult result = coordinator.transfer(request);
        assertEquals("user-b", result.getUpdatedTasks().get(0).getAssigneeUserId());
        assertEquals(Long.valueOf(4), result.getUpdatedTasks().get(0).getTaskVersion());
        verify(tasks).transfer("task-1", 3L, "user-b", "User B");
        verify(operations, never()).markDeterministicFailure(eq("op-transfer"), any(String.class));
    }

    private <T extends com.flowmind.platform.api.request.TaskOperationRequest> T taskRequest(T request,
                                                                                              String operationId,
                                                                                              ProcessActiveTaskEntity task,
                                                                                              UserContext operator) {
        request.setOperationId(operationId);
        request.setTaskId(task.getId());
        request.setExpectedTaskVersion(task.getLockVersion());
        request.setOperatorUserId(operator.getUserId());
        return request;
    }

    private Fixture fixture(ActionTypeEnum action) {
        ProcessInstanceRepository instances = mock(ProcessInstanceRepository.class);
        ActiveTaskRepository tasks = mock(ActiveTaskRepository.class);
        ProcessHistoryTaskRepository histories = mock(ProcessHistoryTaskRepository.class);
        TaskGroupRepository groups = mock(TaskGroupRepository.class);
        RuntimeDefinitionLoader definitions = mock(RuntimeDefinitionLoader.class);
        RuntimeRequestValidator validator = mock(RuntimeRequestValidator.class);
        RuntimeOperationExecutor operations = mock(RuntimeOperationExecutor.class);
        RuntimeNodeAdvancer advancer = mock(RuntimeNodeAdvancer.class);
        RuntimeStateValidator state = mock(RuntimeStateValidator.class);
        HistoryTaskWriter writer = mock(HistoryTaskWriter.class);
        CallbackService callbacks = mock(CallbackService.class);
        OrganizationProvider organization = mock(OrganizationProvider.class);
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("organizationProvider", organization);
        EnhancedTaskActionCoordinator coordinator = new EnhancedTaskActionCoordinator(instances, tasks, histories,
                groups, definitions, validator, operations, advancer, state, writer, null, callbacks,
                factory.getBeanProvider(OrganizationProvider.class),
                factory.getBeanProvider(com.flowmind.platform.persistence.repository.ProcessDefinitionRepository.class));
        UserContext operator = new UserContext("user-a", "User A", null, null);
        ProcessInstanceEntity instance = instance();
        ProcessActiveTaskEntity task = task();
        when(validator.validateTaskIdentity(any(com.flowmind.platform.api.request.TaskOperationRequest.class))).thenReturn(operator);
        when(operations.begin(any(com.flowmind.platform.api.request.OperationRequest.class), any(String.class), any(String.class),
                any(String.class), any(String.class), any(LocalDateTime.class)))
                .thenReturn(new OperationIdempotencyDecision(OperationIdempotencyDecisionType.NEW, null));
        when(state.validateTaskAction(eq(task.getId()), eq(task.getLockVersion()), eq(action), eq(operator)))
                .thenReturn(new RuntimeTaskContext(instance, task, action, operator));
        when(definitions.loadForInstance(instance)).thenReturn(new ProcessDefinitionDetailDTO());
        when(instances.findById(instance.getId())).thenReturn(instance);
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        return new Fixture(coordinator, instances, tasks, histories, groups, operator, instance, task,
                definitions, definition, advancer, writer, organization);
    }

    private ProcessNodeDTO userNode(String code, String listenerConfig) {
        return userNode(code, ApproverRuleTypeEnum.USER, listenerConfig);
    }

    private ProcessNodeDTO userNode(String code, ApproverRuleTypeEnum approverRuleType, String listenerConfig) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeType(NodeTypeEnum.USER_TASK);
        node.setApproverRuleType(approverRuleType);
        node.setListenerConfig(listenerConfig);
        return node;
    }

    private ProcessHistoryTaskEntity history(String id, String assignee, String nodeCode, String action, String extraJson) {
        ProcessHistoryTaskEntity history = new ProcessHistoryTaskEntity();
        history.setId(id);
        history.setInstanceId("instance-1");
        history.setActiveTaskId("history-task-" + id);
        history.setAssigneeUserId(assignee);
        history.setNodeCode(nodeCode);
        history.setActionType(action);
        history.setExtraJson(extraJson);
        return history;
    }

    private static final class Fixture {
        private final EnhancedTaskActionCoordinator coordinator;
        private final ProcessInstanceRepository instances;
        private final ActiveTaskRepository tasks;
        private final ProcessHistoryTaskRepository histories;
        private final TaskGroupRepository groups;
        private final UserContext operator;
        private final ProcessInstanceEntity instance;
        private final ProcessActiveTaskEntity task;
        private final RuntimeDefinitionLoader definitions;
        private final ProcessDefinitionDetailDTO definition;
        private final RuntimeNodeAdvancer advancer;
        private final HistoryTaskWriter historyWriter;
        private final OrganizationProvider organization;

        private Fixture(EnhancedTaskActionCoordinator coordinator, ProcessInstanceRepository instances,
                        ActiveTaskRepository tasks, ProcessHistoryTaskRepository histories, TaskGroupRepository groups,
                        UserContext operator, ProcessInstanceEntity instance, ProcessActiveTaskEntity task,
                        RuntimeDefinitionLoader definitions, ProcessDefinitionDetailDTO definition,
                        RuntimeNodeAdvancer advancer, HistoryTaskWriter historyWriter,
                        OrganizationProvider organization) {
            this.coordinator = coordinator;
            this.instances = instances;
            this.tasks = tasks;
            this.histories = histories;
            this.groups = groups;
            this.operator = operator;
            this.instance = instance;
            this.task = task;
            this.definitions = definitions;
            this.definition = definition;
            this.advancer = advancer;
            this.historyWriter = historyWriter;
            this.organization = organization;
            when(definitions.loadForInstance(instance)).thenReturn(definition);
        }
    }

    private ProcessInstanceEntity instance() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId("instance-1"); instance.setDefinitionId("definition-1"); instance.setProcessCode("leave");
        instance.setProcessName("Leave"); instance.setInstanceStatus("RUNNING"); instance.setVariablesJson("{}");
        return instance;
    }

    private ProcessActiveTaskEntity task() {
        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setId("task-1"); task.setInstanceId("instance-1"); task.setDefinitionId("definition-1");
        task.setNodeCode("manager"); task.setAssigneeUserId("user-a"); task.setAssigneeUserName("User A");
        task.setTaskStatus("ACTIVE"); task.setLockVersion(Long.valueOf(3)); task.setCreatedAt(LocalDateTime.now());
        return task;
    }
}
