package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.DelegateRelationDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.request.ClaimTaskRequest;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.spi.DelegateProvider;
import com.flowmind.platform.core.audit.AuditLogWriter;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskClaimCoordinatorTest {

    @Test
    void claimAllowsDelegatePrincipalCandidateAndReturnsUpdatedTask() {
        Fixture fixture = fixture();
        DelegateRelationDTO relation = new DelegateRelationDTO();
        relation.setPrincipalUserId("principal-1");
        relation.setPrincipalUserName("Principal One");
        relation.setDelegateUserId("agent-1");
        when(fixture.delegateProvider.findPrincipals(eq("agent-1"), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(relation));
        when(fixture.activeTasks.claim("task-1", 3L, "agent-1", "Agent One")).thenReturn(1);

        TaskActionResult result = fixture.coordinator.claim(request());

        assertEquals(ActionTypeEnum.CLAIM, result.getArchivedTasks().get(0).getActionType());
        assertEquals("agent-1", result.getUpdatedTasks().get(0).getAssigneeUserId());
        assertEquals(Long.valueOf(4), result.getUpdatedTasks().get(0).getTaskVersion());
        verify(fixture.activeTasks).claim("task-1", 3L, "agent-1", "Agent One");
        verify(fixture.operations).markSuccess(eq("op-claim"), any(TaskActionResult.class));
    }

    @Test
    void claimRejectsOperatorWhoIsNeitherCandidateNorDelegate() {
        Fixture fixture = fixture();
        when(fixture.delegateProvider.findPrincipals(eq("agent-1"), any(LocalDateTime.class)))
                .thenReturn(Collections.<DelegateRelationDTO>emptyList());

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> fixture.coordinator.claim(request()));

        assertEquals(RuntimeErrorCodes.TASK_CLAIM_PERMISSION_DENIED, error.getErrorCode());
        verify(fixture.activeTasks, never()).claim(any(String.class), any(Long.class), any(String.class),
                any(String.class));
        verify(fixture.operations).markDeterministicFailure("op-claim",
                RuntimeErrorCodes.TASK_CLAIM_PERMISSION_DENIED);
    }

    private Fixture fixture() {
        ProcessInstanceRepository instances = mock(ProcessInstanceRepository.class);
        ActiveTaskRepository activeTasks = mock(ActiveTaskRepository.class);
        RuntimeRequestValidator validator = mock(RuntimeRequestValidator.class);
        RuntimeOperationExecutor operations = mock(RuntimeOperationExecutor.class);
        RuntimeTransactionExecutor transactions = mock(RuntimeTransactionExecutor.class);
        HistoryTaskWriter historyWriter = mock(HistoryTaskWriter.class);
        AuditLogWriter auditWriter = mock(AuditLogWriter.class);
        CallbackService callbacks = mock(CallbackService.class);
        DelegateProvider delegateProvider = mock(DelegateProvider.class);
        doAnswer(invocation -> ((RuntimeTransactionWork<?>) invocation.getArgument(0)).execute())
                .when(transactions).execute(any(RuntimeTransactionWork.class));
        UserContext operator = new UserContext("agent-1", "Agent One", null, null);
        ProcessInstanceEntity instance = instance();
        ProcessActiveTaskEntity task = activeTask("ACTIVE", Long.valueOf(3), null);
        ProcessActiveTaskEntity updated = activeTask("CLAIMED", Long.valueOf(4), "agent-1");
        ProcessHistoryTaskEntity history = history();
        when(validator.validateTaskIdentity(any())).thenReturn(operator);
        when(operations.begin(any(), eq(RuntimeOperationTypes.CLAIM), eq("agent-1"), eq(null), eq("task-1"),
                any(LocalDateTime.class)))
                .thenReturn(new OperationIdempotencyDecision(OperationIdempotencyDecisionType.NEW, null));
        when(instances.findById("instance-1")).thenReturn(instance);
        when(activeTasks.findById("task-1")).thenReturn(task, updated);
        when(historyWriter.archive(any())).thenReturn(history);
        TaskClaimCoordinator coordinator = new TaskClaimCoordinator(instances, activeTasks, validator, operations,
                transactions, historyWriter, auditWriter, callbacks, delegateProvider);
        return new Fixture(coordinator, activeTasks, operations, delegateProvider);
    }

    private ClaimTaskRequest request() {
        ClaimTaskRequest request = new ClaimTaskRequest();
        request.setOperationId("op-claim");
        request.setTaskId("task-1");
        request.setExpectedTaskVersion(Long.valueOf(3));
        request.setOperatorUserId("agent-1");
        return request;
    }

    private ProcessInstanceEntity instance() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId("instance-1");
        instance.setDefinitionId("definition-1");
        instance.setProcessCode("leave");
        instance.setProcessName("Leave");
        instance.setInstanceStatus("RUNNING");
        instance.setVariablesJson("{}");
        return instance;
    }

    private ProcessActiveTaskEntity activeTask(String status, Long version, String assigneeUserId) {
        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setId("task-1");
        task.setInstanceId("instance-1");
        task.setDefinitionId("definition-1");
        task.setNodeCode("approve");
        task.setCandidateUserIds("[\"principal-1\"]");
        task.setAssigneeUserId(assigneeUserId);
        task.setAssigneeUserName(assigneeUserId == null ? null : "Agent One");
        task.setTaskStatus(status);
        task.setLockVersion(version);
        task.setCreatedAt(LocalDateTime.now());
        return task;
    }

    private ProcessHistoryTaskEntity history() {
        ProcessHistoryTaskEntity history = new ProcessHistoryTaskEntity();
        history.setId("history-claim");
        history.setInstanceId("instance-1");
        history.setActiveTaskId("task-1");
        history.setNodeCode("approve");
        history.setActionType(ActionTypeEnum.CLAIM.name());
        history.setAssigneeUserId("agent-1");
        history.setAssigneeUserName("Agent One");
        history.setCompletedAt(LocalDateTime.now());
        return history;
    }

    private static final class Fixture {
        private final TaskClaimCoordinator coordinator;
        private final ActiveTaskRepository activeTasks;
        private final RuntimeOperationExecutor operations;
        private final DelegateProvider delegateProvider;

        private Fixture(TaskClaimCoordinator coordinator, ActiveTaskRepository activeTasks,
                        RuntimeOperationExecutor operations, DelegateProvider delegateProvider) {
            this.coordinator = coordinator;
            this.activeTasks = activeTasks;
            this.operations = operations;
            this.delegateProvider = delegateProvider;
        }
    }
}
