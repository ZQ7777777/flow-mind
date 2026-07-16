package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.request.TaskOperationRequest;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeResultContractTest {

    @Test
    void processInstanceAndTaskDtosExpressRuntimeInformation() {
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("amount", 10);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 13, 10, 0);
        LocalDateTime dueAt = LocalDateTime.of(2026, 7, 14, 10, 0);
        TaskDTO task = new TaskDTO();
        task.setTaskId("task-001");
        task.setInstanceId("instance-001");
        task.setDefinitionId("definition-001");
        task.setNodeCode("review");
        task.setNodeName("Review");
        task.setCandidateUserIds(Arrays.asList("user-001", "user-002"));
        task.setAssigneeUserId("user-001");
        task.setAssigneeUserName("Reviewer");
        task.setDelegateFromUserId("user-003");
        task.setDelegateFromUserName("Delegator");
        task.setTaskGroupId("group-001");
        task.setBranchKey("branch-001");
        task.setTaskVersion(2L);
        task.setCreatedAt(createdAt);
        task.setDueAt(dueAt);

        ProcessInstanceDTO instance = new ProcessInstanceDTO();
        instance.setInstanceId("instance-001");
        instance.setDefinitionId("definition-001");
        instance.setAttachmentConfigId("attachment-config-001");
        instance.setProcessCode("process-code-001");
        instance.setProcessName("Process");
        instance.setVersion(1);
        instance.setInstanceTitle("Instance");
        instance.setBusinessKey("business-001");
        instance.setStarterUserId("starter-001");
        instance.setStarterUserName("Starter");
        instance.setStarterDeptId("dept-001");
        instance.setCurrentNodeCodes(Collections.singletonList("review"));
        instance.setVariables(variables);
        instance.setStartedAt(createdAt);
        instance.setEndedAt(dueAt);
        instance.setCreatedTasks(Collections.singletonList(task));

        assertEquals("instance-001", instance.getInstanceId());
        assertEquals("definition-001", instance.getDefinitionId());
        assertEquals("attachment-config-001", instance.getAttachmentConfigId());
        assertEquals("process-code-001", instance.getProcessCode());
        assertEquals("Process", instance.getProcessName());
        assertEquals(Integer.valueOf(1), instance.getVersion());
        assertEquals("Instance", instance.getInstanceTitle());
        assertEquals("business-001", instance.getBusinessKey());
        assertEquals("starter-001", instance.getStarterUserId());
        assertEquals("Starter", instance.getStarterUserName());
        assertEquals("dept-001", instance.getStarterDeptId());
        assertEquals(Collections.singletonList("review"), instance.getCurrentNodeCodes());
        assertEquals(variables, instance.getVariables());
        assertEquals(createdAt, instance.getStartedAt());
        assertEquals(dueAt, instance.getEndedAt());
        assertEquals(Collections.singletonList(task), instance.getCreatedTasks());
        assertEquals("task-001", task.getTaskId());
        assertEquals("instance-001", task.getInstanceId());
        assertEquals("definition-001", task.getDefinitionId());
        assertEquals("review", task.getNodeCode());
        assertEquals("Review", task.getNodeName());
        assertEquals(Arrays.asList("user-001", "user-002"), task.getCandidateUserIds());
        assertEquals("user-001", task.getAssigneeUserId());
        assertEquals("Reviewer", task.getAssigneeUserName());
        assertEquals("user-003", task.getDelegateFromUserId());
        assertEquals("Delegator", task.getDelegateFromUserName());
        assertEquals("group-001", task.getTaskGroupId());
        assertEquals("branch-001", task.getBranchKey());
        assertEquals(Long.valueOf(2L), task.getTaskVersion());
        assertEquals(createdAt, task.getCreatedAt());
        assertEquals(dueAt, task.getDueAt());
    }

    @Test
    void historyTaskAndActionResultExpressArchivedAndCreatedTasks() {
        Map<String, Object> variablesSnapshot = new LinkedHashMap<String, Object>();
        variablesSnapshot.put("approved", Boolean.TRUE);
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 13, 10, 0);
        LocalDateTime completedAt = LocalDateTime.of(2026, 7, 13, 11, 0);
        HistoryTaskDTO archivedTask = new HistoryTaskDTO();
        archivedTask.setHistoryTaskId("history-001");
        archivedTask.setInstanceId("instance-001");
        archivedTask.setOperationId("operation-001");
        archivedTask.setActiveTaskId("task-001");
        archivedTask.setNodeCode("review");
        archivedTask.setTaskGroupId("group-001");
        archivedTask.setBranchKey("branch-001");
        archivedTask.setAssigneeUserId("user-001");
        archivedTask.setAssigneeUserName("Reviewer");
        archivedTask.setDelegateFromUserId("user-002");
        archivedTask.setDelegateFromUserName("Delegator");
        archivedTask.setComment("completed");
        archivedTask.setVariablesSnapshot(variablesSnapshot);
        archivedTask.setStartedAt(startedAt);
        archivedTask.setCompletedAt(completedAt);

        ProcessInstanceDTO instance = new ProcessInstanceDTO();
        instance.setInstanceId("instance-001");
        TaskDTO createdTask = new TaskDTO();
        createdTask.setTaskId("task-002");
        TaskActionResult result = new TaskActionResult();
        result.setOperationId("operation-001");
        result.setInstance(instance);
        result.setArchivedTasks(Collections.singletonList(archivedTask));
        result.setCreatedTasks(Collections.singletonList(createdTask));
        result.setReplayed(true);

        assertEquals("history-001", archivedTask.getHistoryTaskId());
        assertEquals("instance-001", archivedTask.getInstanceId());
        assertEquals("operation-001", archivedTask.getOperationId());
        assertEquals("task-001", archivedTask.getActiveTaskId());
        assertEquals("review", archivedTask.getNodeCode());
        assertEquals("group-001", archivedTask.getTaskGroupId());
        assertEquals("branch-001", archivedTask.getBranchKey());
        assertEquals("user-001", archivedTask.getAssigneeUserId());
        assertEquals("Reviewer", archivedTask.getAssigneeUserName());
        assertEquals("user-002", archivedTask.getDelegateFromUserId());
        assertEquals("Delegator", archivedTask.getDelegateFromUserName());
        assertEquals("completed", archivedTask.getComment());
        assertEquals(variablesSnapshot, archivedTask.getVariablesSnapshot());
        assertEquals(startedAt, archivedTask.getStartedAt());
        assertEquals(completedAt, archivedTask.getCompletedAt());
        assertEquals("operation-001", result.getOperationId());
        assertEquals(instance, result.getInstance());
        assertEquals(Collections.singletonList(archivedTask), result.getArchivedTasks());
        assertEquals(Collections.singletonList(createdTask), result.getCreatedTasks());
        assertTrue(result.isReplayed());
    }

    @Test
    void operationResultAndAttachmentItemExpressReplayAndBinaryContent() {
        OperationResult result = new OperationResult();
        result.setOperationId("operation-002");
        result.setTargetType("INSTANCE");
        result.setTargetId("instance-002");
        result.setDeleted(true);
        result.setReplayed(false);

        AttachmentUploadItem attachment = new AttachmentUploadItem();
        attachment.setContent(new byte[] {1, 2, 3});

        assertEquals("operation-002", result.getOperationId());
        assertEquals("INSTANCE", result.getTargetType());
        assertEquals("instance-002", result.getTargetId());
        assertTrue(result.isDeleted());
        assertFalse(result.isReplayed());
        assertArrayEquals(new byte[] {1, 2, 3}, attachment.getContent());
    }

    @Test
    void runtimeDtoTypesAlignWithPersistedModelAndOptimisticLockRequest() throws NoSuchFieldException {
        assertEquals(fieldType(ProcessInstanceEntity.class, "version"),
                fieldType(ProcessInstanceDTO.class, "version"));
        assertEquals(fieldType(ProcessActiveTaskEntity.class, "lockVersion"),
                fieldType(TaskDTO.class, "taskVersion"));
        assertEquals(fieldType(TaskDTO.class, "taskVersion"),
                fieldType(TaskOperationRequest.class, "expectedTaskVersion"));
        assertEquals(fieldType(ProcessHistoryTaskEntity.class, "operationId"),
                fieldType(HistoryTaskDTO.class, "operationId"));
        assertEquals(fieldType(ProcessHistoryTaskEntity.class, "commentText"),
                fieldType(HistoryTaskDTO.class, "comment"));
    }

    private Class<?> fieldType(Class<?> owner, String fieldName) throws NoSuchFieldException {
        return owner.getDeclaredField(fieldName).getType();
    }
}
