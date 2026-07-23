package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 实例级任务取消协调器的 CAS 与归档测试。 */
class InstanceTaskCancellationServiceTest {

    private ActiveTaskRepository activeTaskRepository;
    private TaskGroupRepository taskGroupRepository;
    private HistoryTaskWriter historyTaskWriter;
    private InstanceTaskCancellationService service;

    @BeforeEach
    void setUp() {
        activeTaskRepository = mock(ActiveTaskRepository.class);
        taskGroupRepository = mock(TaskGroupRepository.class);
        historyTaskWriter = mock(HistoryTaskWriter.class);
        service = new InstanceTaskCancellationService(activeTaskRepository, taskGroupRepository, historyTaskWriter);
    }

    @Test
    void cancelsAllOpenTasksArchivesThemAndCancelsActiveGroups() {
        ProcessInstanceEntity instance = instance();
        ProcessActiveTaskEntity task = task();
        ProcessTaskGroupEntity group = group();
        ProcessHistoryTaskEntity history = new ProcessHistoryTaskEntity();
        history.setId("history-1");
        history.setInstanceId("instance-1");
        history.setActiveTaskId("task-1");
        history.setActionType(ActionTypeEnum.TERMINATE.name());
        when(activeTaskRepository.findOpenByInstanceId("instance-1")).thenReturn(Collections.singletonList(task));
        when(activeTaskRepository.cancel("task-1", 2L)).thenReturn(1);
        when(historyTaskWriter.archiveCanceledTask(eq(instance), eq(task), any(UserContext.class),
                eq(ActionTypeEnum.TERMINATE), eq("stop"), any(), eq("operation-1"))).thenReturn(history);
        when(taskGroupRepository.findActiveByInstanceId("instance-1")).thenReturn(Collections.singletonList(group));
        when(taskGroupRepository.cancel("group-1", 3L)).thenReturn(1);

        java.util.List<HistoryTaskDTO> result = service.cancelOpenWork(instance, operator(),
                ActionTypeEnum.TERMINATE, "stop", "operation-1", Collections.<String, Object>emptyMap());

        assertEquals(1, result.size());
        assertEquals("history-1", result.get(0).getHistoryTaskId());
        verify(activeTaskRepository).cancel("task-1", 2L);
        verify(taskGroupRepository).cancel("group-1", 3L);
    }

    @Test
    void stopsWhenTaskCompareAndSetFails() {
        ProcessActiveTaskEntity task = task();
        when(activeTaskRepository.findOpenByInstanceId("instance-1")).thenReturn(Collections.singletonList(task));
        when(activeTaskRepository.cancel("task-1", 2L)).thenReturn(0);

        RuntimeStateException error = assertThrows(RuntimeStateException.class,
                () -> service.cancelOpenWork(instance(), operator(), ActionTypeEnum.FORCE_COMPLETE, "stop",
                        "operation-2", Collections.<String, Object>emptyMap()));

        assertEquals(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED, error.getErrorCode());
        verify(historyTaskWriter, never()).archiveCanceledTask(any(ProcessInstanceEntity.class),
                any(ProcessActiveTaskEntity.class), any(UserContext.class), any(ActionTypeEnum.class), any(), any(),
                any());
        verify(taskGroupRepository, never()).findActiveByInstanceId(any());
    }

    private ProcessInstanceEntity instance() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId("instance-1");
        return instance;
    }

    private ProcessActiveTaskEntity task() {
        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setId("task-1");
        task.setInstanceId("instance-1");
        task.setNodeCode("review");
        task.setLockVersion(Long.valueOf(2L));
        return task;
    }

    private ProcessTaskGroupEntity group() {
        ProcessTaskGroupEntity group = new ProcessTaskGroupEntity();
        group.setId("group-1");
        group.setLockVersion(Long.valueOf(3L));
        return group;
    }

    private UserContext operator() {
        return new UserContext("admin", "Administrator", null, null);
    }
}
