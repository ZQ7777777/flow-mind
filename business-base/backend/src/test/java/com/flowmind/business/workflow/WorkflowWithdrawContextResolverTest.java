package com.flowmind.business.workflow;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowWithdrawContextResolverTest {
    private final WorkflowWithdrawContextResolver resolver = new WorkflowWithdrawContextResolver();

    @Test
    void resolvesLatestPreviousHandlerForSingleOpenSerialTask() {
        ProcessInstanceDetailDTO instance = instance();

        WorkflowWithdrawContextResolver.Resolution result = resolver.resolve(instance, "sales-1");

        assertThat(result).isNotNull();
        assertThat(result.getActiveTask().getTaskId()).isEqualTo("manager-task");
        assertThat(result.getSourceHistory().getHistoryTaskId()).isEqualTo("apply-history");
    }

    @Test
    void rejectsWrongUserInvalidInstanceAndGroupedOrMultipleTasks() {
        ProcessInstanceDetailDTO instance = instance();
        assertThat(resolver.resolve(instance, "other-user")).isNull();

        instance.setInstanceStatus(InstanceStatusEnum.COMPLETED);
        assertThat(resolver.resolve(instance, "sales-1")).isNull();
        instance.setInstanceStatus(InstanceStatusEnum.RUNNING);

        instance.getActiveTasks().get(0).setTaskGroupId("group-1");
        assertThat(resolver.resolve(instance, "sales-1")).isNull();
        instance.getActiveTasks().get(0).setTaskGroupId(null);
        instance.setActiveTasks(Arrays.asList(instance.getActiveTasks().get(0), task("other-task")));
        assertThat(resolver.resolve(instance, "sales-1")).isNull();
    }

    @Test
    void ignoresHistoryArchivedFromTheCurrentActiveTask() {
        ProcessInstanceDetailDTO instance = instance();
        HistoryTaskDTO cancellation = history("cancel-history", "manager-task", "manager-1", ActionTypeEnum.REJECT);
        cancellation.setCompletedAt(LocalDateTime.of(2026, 8, 12, 10, 0));
        instance.setHistoryTasks(Arrays.asList(instance.getHistoryTasks().get(0), cancellation));

        WorkflowWithdrawContextResolver.Resolution result = resolver.resolve(instance, "sales-1");

        assertThat(result).isNotNull();
        assertThat(result.getSourceHistory().getHistoryTaskId()).isEqualTo("apply-history");
    }

    @Test
    void usesCompletionTimeInsteadOfListOrderAndRejectsRecreatedSameNode() {
        ProcessInstanceDetailDTO instance = instance();
        HistoryTaskDTO older = history("older", "older-task", "other-user", ActionTypeEnum.APPROVE);
        older.setCompletedAt(LocalDateTime.of(2026, 8, 12, 8, 0));
        HistoryTaskDTO latest = instance.getHistoryTasks().get(0);
        latest.setCompletedAt(LocalDateTime.of(2026, 8, 12, 9, 0));
        instance.setHistoryTasks(Arrays.asList(latest, older));

        assertThat(resolver.resolve(instance, "sales-1").getSourceHistory().getHistoryTaskId())
                .isEqualTo("apply-history");

        instance.getActiveTasks().get(0).setNodeCode("apply");
        assertThat(resolver.resolve(instance, "sales-1")).isNull();
    }

    private ProcessInstanceDetailDTO instance() {
        ProcessInstanceDetailDTO instance = new ProcessInstanceDetailDTO();
        instance.setInstanceStatus(InstanceStatusEnum.RUNNING);
        instance.setActiveTasks(Collections.singletonList(task("manager-task")));
        instance.setHistoryTasks(Collections.singletonList(
                history("apply-history", "apply-task", "sales-1", ActionTypeEnum.SEND)));
        return instance;
    }

    private TaskDTO task(String id) {
        TaskDTO task = new TaskDTO();
        task.setTaskId(id); task.setNodeCode("manager");
        task.setTaskStatus(TaskStatusEnum.ACTIVE); task.setTaskVersion(1L);
        return task;
    }

    private HistoryTaskDTO history(String id, String taskId, String userId, ActionTypeEnum action) {
        HistoryTaskDTO history = new HistoryTaskDTO();
        history.setHistoryTaskId(id); history.setActiveTaskId(taskId); history.setAssigneeUserId(userId);
        history.setNodeCode("apply"); history.setNodeName("申请"); history.setActionType(action);
        return history;
    }
}
