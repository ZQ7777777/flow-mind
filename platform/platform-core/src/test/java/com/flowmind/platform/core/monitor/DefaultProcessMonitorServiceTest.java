package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.AlertTypeEnum;
import com.flowmind.platform.api.request.TimeoutScanRequest;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessAlertRecordEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.AlertRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultProcessMonitorServiceTest {

    @Test
    void timeoutScanDryRunOnlyReturnsTasks() {
        ActiveTaskRepository activeTasks = mock(ActiveTaskRepository.class);
        AlertRecordRepository alerts = mock(AlertRecordRepository.class);
        DefaultProcessMonitorService service = service(activeTasks, alerts);
        LocalDateTime scanAt = LocalDateTime.of(2026, 7, 28, 10, 0);
        when(activeTasks.findTimeoutOpenTasks(scanAt, 10)).thenReturn(Collections.singletonList(task()));
        TimeoutScanRequest request = new TimeoutScanRequest();
        request.setScanAt(scanAt);
        request.setLimit(Integer.valueOf(10));
        request.setDryRun(Boolean.TRUE);

        List<TaskDTO> results = service.scanTimeoutTasks(request);

        assertEquals(1, results.size());
        verify(alerts, never()).insert(any(ProcessAlertRecordEntity.class));
    }

    @Test
    void timeoutScanCreatesOneOpenAlertAndSkipsDuplicates() {
        ActiveTaskRepository activeTasks = mock(ActiveTaskRepository.class);
        AlertRecordRepository alerts = mock(AlertRecordRepository.class);
        DefaultProcessMonitorService service = service(activeTasks, alerts);
        LocalDateTime scanAt = LocalDateTime.of(2026, 7, 28, 10, 0);
        when(activeTasks.findTimeoutOpenTasks(scanAt, 10)).thenReturn(Collections.singletonList(task()));
        when(alerts.findOpenByTaskAndType("task-timeout", AlertTypeEnum.TASK_TIMEOUT.name()))
                .thenReturn(null, existingAlert());
        TimeoutScanRequest request = new TimeoutScanRequest();
        request.setScanAt(scanAt);
        request.setLimit(Integer.valueOf(10));
        request.setDryRun(Boolean.FALSE);

        service.scanTimeoutTasks(request);
        service.scanTimeoutTasks(request);

        verify(alerts).insert(any(ProcessAlertRecordEntity.class));
    }

    private DefaultProcessMonitorService service(ActiveTaskRepository activeTasks, AlertRecordRepository alerts) {
        return new DefaultProcessMonitorService(activeTasks, null, null, alerts, null, null, null, null, null);
    }

    private ProcessActiveTaskEntity task() {
        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setId("task-timeout");
        task.setInstanceId("instance-1");
        task.setDefinitionId("definition-1");
        task.setNodeCode("approve");
        task.setTaskStatus("ACTIVE");
        task.setLockVersion(Long.valueOf(0));
        task.setCreatedAt(LocalDateTime.of(2026, 7, 28, 9, 0));
        task.setDueAt(LocalDateTime.of(2026, 7, 28, 9, 30));
        return task;
    }

    private ProcessAlertRecordEntity existingAlert() {
        ProcessAlertRecordEntity alert = new ProcessAlertRecordEntity();
        alert.setId("alert-1");
        alert.setTaskId("task-timeout");
        alert.setAlertType(AlertTypeEnum.TASK_TIMEOUT.name());
        alert.setAlertStatus("OPEN");
        return alert;
    }
}
