package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import com.flowmind.platform.api.request.ApproverResolveRequest;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.request.TaskOperationRequest;
import com.flowmind.platform.api.request.UpdateVariablesRequest;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeRequestValidatorTest {

    private final RuntimeRequestValidator validator = new RuntimeRequestValidator(
            () -> new UserContext("user-1", "Alice", "dept-1", "Finance"));

    @Test
    void startRequiresOperationIdAndTrustedStarter() {
        StartProcessRequest missingOperationId = startRequest("user-1");
        RuntimeValidationException missing = assertThrows(RuntimeValidationException.class,
                () -> validator.validateStart(missingOperationId));
        assertEquals(RuntimeErrorCodes.OPERATION_ID_REQUIRED, missing.getErrorCode());

        StartProcessRequest forgedStarter = startRequest("user-2");
        forgedStarter.setOperationId("operation-start");
        RuntimeValidationException forged = assertThrows(RuntimeValidationException.class,
                () -> validator.validateStart(forgedStarter));
        assertEquals(RuntimeErrorCodes.INVALID_ACTION, forged.getErrorCode());
    }

    @Test
    void taskActionRequiresRunningActiveCandidateTaskAndMatchingVersion() {
        TaskOperationRequest request = taskRequest(Long.valueOf(4));
        ProcessInstanceEntity instance = runningInstance();
        ProcessActiveTaskEntity task = activeTask();

        assertEquals("user-1", validator.validateTaskAction(request, instance, task).getUserId());

        request.setExpectedTaskVersion(Long.valueOf(3));
        RuntimeStateException stale = assertThrows(RuntimeStateException.class,
                () -> validator.validateTaskAction(request, instance, task));
        assertEquals(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED, stale.getErrorCode());

        request.setExpectedTaskVersion(Long.valueOf(4));
        task.setCandidateUserIds("[\"other-user\"]");
        RuntimeValidationException denied = assertThrows(RuntimeValidationException.class,
                () -> validator.validateTaskAction(request, instance, task));
        assertEquals(RuntimeErrorCodes.TASK_PERMISSION_DENIED, denied.getErrorCode());
    }

    @Test
    void taskActionAllowsDelegatedTaskAssignedToCurrentUser() {
        RuntimeRequestValidator delegateValidator = new RuntimeRequestValidator(
                () -> new UserContext("agent-1", "Agent", "dept-1", "Finance"));
        TaskOperationRequest request = taskRequest(Long.valueOf(4));
        request.setOperatorUserId("agent-1");
        ProcessActiveTaskEntity task = activeTask();
        task.setAssigneeUserId("agent-1");
        task.setAssigneeUserName("Agent");
        task.setDelegateFromUserId("principal-1");
        task.setDelegateFromUserName("Principal");

        assertEquals("agent-1", delegateValidator.validateTaskAction(request, runningInstance(), task).getUserId());
        assertEquals("principal-1", task.getDelegateFromUserId());
        assertEquals("Principal", task.getDelegateFromUserName());
    }

    @Test
    void variableUpdateRejectsTerminalInstanceAndForgedOperator() {
        UpdateVariablesRequest request = new UpdateVariablesRequest();
        request.setOperationId("operation-variable");
        request.setInstanceId("instance-1");
        request.setOperatorUserId("user-1");
        request.setVariables(new LinkedHashMap<String, Object>());
        ProcessInstanceEntity instance = runningInstance();
        instance.setInstanceStatus(InstanceStatusEnum.COMPLETED.name());

        RuntimeStateException terminal = assertThrows(RuntimeStateException.class,
                () -> validator.validateVariableUpdate(request, instance));
        assertEquals(RuntimeErrorCodes.INVALID_ACTION, terminal.getErrorCode());

        instance.setInstanceStatus(InstanceStatusEnum.RUNNING.name());
        request.setOperatorUserId("user-2");
        RuntimeValidationException forged = assertThrows(RuntimeValidationException.class,
                () -> validator.validateVariableUpdate(request, instance));
        assertEquals(RuntimeErrorCodes.INVALID_ACTION, forged.getErrorCode());
    }

    @Test
    void approverResolutionDeduplicatesSortsAndKeepsMultiInstanceApprovers() {
        List<UserDTO> users = validator.resolveApprovers(MultiInstanceModeEnum.SINGLE,
                request -> Arrays.asList(new UserDTO("user-3", "Carol"), new UserDTO("user-2", "Bob"),
                        new UserDTO("user-2", "Duplicate")),
                new ApproverResolveRequest());
        assertEquals(2, users.size());
        assertEquals("user-2", users.get(0).getUserId());
        assertEquals("user-3", users.get(1).getUserId());

        List<UserDTO> orSignUsers = validator.resolveApprovers(MultiInstanceModeEnum.OR_SIGN,
                request -> Arrays.asList(new UserDTO("user-2", "Bob"), new UserDTO("user-1", "Alice")),
                new ApproverResolveRequest());
        assertEquals(2, orSignUsers.size());
        assertEquals("user-1", orSignUsers.get(0).getUserId());
        assertEquals("user-2", orSignUsers.get(1).getUserId());

        RuntimeStateException empty = assertThrows(RuntimeStateException.class,
                () -> validator.resolveApprovers(MultiInstanceModeEnum.SINGLE,
                        request -> Collections.<UserDTO>emptyList(), new ApproverResolveRequest()));
        assertEquals(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED, empty.getErrorCode());
    }

    private StartProcessRequest startRequest(String starterUserId) {
        StartProcessRequest request = new StartProcessRequest();
        request.setProcessCode("expense");
        request.setInstanceTitle("Expense application");
        request.setStarterUserId(starterUserId);
        request.setStarterDeptId("dept-1");
        return request;
    }

    private TaskOperationRequest taskRequest(Long version) {
        TaskOperationRequest request = new TaskOperationRequest();
        request.setOperationId("operation-task");
        request.setTaskId("task-1");
        request.setOperatorUserId("user-1");
        request.setExpectedTaskVersion(version);
        return request;
    }

    private ProcessInstanceEntity runningInstance() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId("instance-1");
        instance.setInstanceStatus(InstanceStatusEnum.RUNNING.name());
        return instance;
    }

    private ProcessActiveTaskEntity activeTask() {
        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setId("task-1");
        task.setInstanceId("instance-1");
        task.setTaskStatus(TaskStatusEnum.ACTIVE.name());
        task.setCandidateUserIds("[\"user-1\"]");
        task.setLockVersion(Long.valueOf(4));
        return task;
    }
}
