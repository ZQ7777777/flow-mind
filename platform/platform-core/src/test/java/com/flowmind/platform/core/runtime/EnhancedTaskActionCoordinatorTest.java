package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.request.TransferTaskRequest;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.spi.OrganizationProvider;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M5 增强动作协调器的关键回归测试。 */
class EnhancedTaskActionCoordinatorTest {

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
