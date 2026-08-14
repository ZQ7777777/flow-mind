package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.AlertTypeEnum;
import com.flowmind.platform.api.enums.ReminderTypeEnum;
import com.flowmind.platform.api.request.TimeoutScanRequest;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessAlertRecordEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;
import com.flowmind.platform.persistence.entity.ProcessReminderRecordEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.AlertRecordRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.ProcessNodeRepository;
import com.flowmind.platform.persistence.repository.ReminderRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(alerts.findLatestByTaskAndType("task-timeout", AlertTypeEnum.TASK_TIMEOUT.name()))
                .thenReturn(null, existingAlert());
        TimeoutScanRequest request = new TimeoutScanRequest();
        request.setScanAt(scanAt);
        request.setLimit(Integer.valueOf(10));
        request.setDryRun(Boolean.FALSE);

        service.scanTimeoutTasks(request);
        service.scanTimeoutTasks(request);

        verify(alerts).insert(any(ProcessAlertRecordEntity.class));
    }

    @Test
    void timeoutScanSkipsHandledAlertWhenTaskIsStillOverdue() {
        ActiveTaskRepository activeTasks = mock(ActiveTaskRepository.class);
        AlertRecordRepository alerts = mock(AlertRecordRepository.class);
        DefaultProcessMonitorService service = service(activeTasks, alerts);
        LocalDateTime scanAt = LocalDateTime.of(2026, 7, 28, 10, 0);
        when(activeTasks.findTimeoutOpenTasks(scanAt, 10)).thenReturn(Collections.singletonList(task()));
        when(alerts.findLatestByTaskAndType("task-timeout", AlertTypeEnum.TASK_TIMEOUT.name()))
                .thenReturn(existingAlert("HANDLED"));
        TimeoutScanRequest request = new TimeoutScanRequest();
        request.setScanAt(scanAt);
        request.setLimit(Integer.valueOf(10));
        request.setDryRun(Boolean.FALSE);

        service.scanTimeoutTasks(request);

        verify(alerts, never()).insert(any(ProcessAlertRecordEntity.class));
    }

    @Test
    void timeoutScanPublishesDueSoonReminderWithStableMessageType() {
        ActiveTaskRepository activeTasks = mock(ActiveTaskRepository.class);
        ProcessInstanceRepository instances = mock(ProcessInstanceRepository.class);
        ReminderRecordRepository reminders = mock(ReminderRecordRepository.class);
        AlertRecordRepository alerts = mock(AlertRecordRepository.class);
        MessagePublisher publisher = mock(MessagePublisher.class);
        ProcessNodeRepository nodes = mock(ProcessNodeRepository.class);
        ReminderDeduplicationGuard deduplicationGuard = mock(ReminderDeduplicationGuard.class);
        DefaultProcessMonitorService service = new DefaultProcessMonitorService(activeTasks, instances, reminders,
                alerts, null, null, null, null, publisher, nodes, new TimeoutPolicyReader(), new ReminderPolicyReader(),
                deduplicationGuard, null, null, null);
        LocalDateTime scanAt = LocalDateTime.of(2026, 7, 28, 10, 0);
        ProcessActiveTaskEntity dueSoonTask = dueSoonTask();
        when(activeTasks.findDueSoonOpenTasks(scanAt, 10)).thenReturn(Collections.singletonList(dueSoonTask));
        when(activeTasks.findTimeoutOpenTasks(scanAt, 10)).thenReturn(Collections.<ProcessActiveTaskEntity>emptyList());
        when(nodes.findByDefinitionIdAndNodeCode("definition-1", "approve")).thenReturn(reminderNode());
        when(reminders.findLatestByTaskAndType("task-due-soon", ReminderTypeEnum.DUE_SOON.name())).thenReturn(null);
        when(reminders.insert(any(ProcessReminderRecordEntity.class))).thenReturn(Integer.valueOf(1));
        when(reminders.markSent(anyString())).thenReturn(Integer.valueOf(1));
        when(reminders.findById(anyString())).thenAnswer(invocation -> sentReminder(invocation.getArgument(0)));
        when(deduplicationGuard.canCreate("task-due-soon", ReminderTypeEnum.DUE_SOON, 1)).thenReturn(true);
        when(instances.findById("instance-1")).thenReturn(instance());
        TimeoutScanRequest request = new TimeoutScanRequest();
        request.setScanAt(scanAt);
        request.setLimit(Integer.valueOf(10));
        request.setDryRun(Boolean.FALSE);

        service.scanTimeoutTasks(request);

        ArgumentCaptor<ProcessReminderRecordEntity> reminderCaptor = ArgumentCaptor.forClass(ProcessReminderRecordEntity.class);
        verify(reminders).insert(reminderCaptor.capture());
        assertEquals(ReminderTypeEnum.DUE_SOON.name(), reminderCaptor.getValue().getReminderType());
        ArgumentCaptor<ProcessMessage> messageCaptor = ArgumentCaptor.forClass(ProcessMessage.class);
        verify(publisher).publish(messageCaptor.capture());
        ProcessMessage message = messageCaptor.getValue();
        assertEquals("TASK_DUE_SOON", message.getMessageType());
        assertEquals(Collections.singletonList("approver-1"), message.getTargetUserIds());
        assertEquals("task-due-soon", message.getPayload().get("taskId"));
        assertEquals("instance-1", message.getPayload().get("instanceId"));
        assertEquals("expense", message.getPayload().get("processCode"));
        assertEquals("approve", message.getPayload().get("nodeCode"));
        assertTrue(message.getPayload().containsKey("dueAt"));
        assertFalse(message.getPayload().containsKey("reminderType"));
    }

    @Test
    void timeoutJumpReminderUsesConfiguredTargetNodeName() {
        ActiveTaskRepository activeTasks = mock(ActiveTaskRepository.class);
        ProcessInstanceRepository instances = mock(ProcessInstanceRepository.class);
        ReminderRecordRepository reminders = mock(ReminderRecordRepository.class);
        AlertRecordRepository alerts = mock(AlertRecordRepository.class);
        MessagePublisher publisher = mock(MessagePublisher.class);
        ProcessNodeRepository nodes = mock(ProcessNodeRepository.class);
        ReminderDeduplicationGuard deduplicationGuard = mock(ReminderDeduplicationGuard.class);
        DefaultProcessMonitorService service = new DefaultProcessMonitorService(activeTasks, instances, reminders,
                alerts, null, null, null, null, publisher, nodes, new TimeoutPolicyReader(), new ReminderPolicyReader(),
                deduplicationGuard, null, null, null);
        LocalDateTime scanAt = LocalDateTime.of(2026, 7, 28, 10, 0);
        ProcessActiveTaskEntity timeoutTask = task();
        timeoutTask.setCandidateUserIds("[\"approver-1\"]");
        ProcessNodeEntity currentNode = timeoutJumpNode();
        ProcessNodeEntity targetNode = new ProcessNodeEntity();
        targetNode.setNodeName("财务复核");
        when(activeTasks.findDueSoonOpenTasks(scanAt, 10))
                .thenReturn(Collections.<ProcessActiveTaskEntity>emptyList());
        when(activeTasks.findTimeoutOpenTasks(scanAt, 10)).thenReturn(Collections.singletonList(timeoutTask));
        when(nodes.findByDefinitionIdAndNodeCode("definition-1", "approve")).thenReturn(currentNode);
        when(nodes.findByDefinitionIdAndNodeCode("definition-1", "finance-review")).thenReturn(targetNode);
        when(reminders.findLatestByTaskAndType("task-timeout", ReminderTypeEnum.TIMEOUT.name())).thenReturn(null);
        when(reminders.insert(any(ProcessReminderRecordEntity.class))).thenReturn(Integer.valueOf(1));
        when(reminders.markSent(anyString())).thenReturn(Integer.valueOf(1));
        when(reminders.findById(anyString())).thenAnswer(invocation -> sentReminder(invocation.getArgument(0)));
        when(deduplicationGuard.canCreate("task-timeout", ReminderTypeEnum.TIMEOUT, 1)).thenReturn(true);
        when(instances.findById("instance-1")).thenReturn(instance());
        TimeoutScanRequest request = new TimeoutScanRequest();
        request.setScanAt(scanAt);
        request.setLimit(Integer.valueOf(10));
        request.setDryRun(Boolean.FALSE);

        service.scanTimeoutTasks(request);

        ArgumentCaptor<ProcessReminderRecordEntity> reminderCaptor = ArgumentCaptor.forClass(ProcessReminderRecordEntity.class);
        verify(reminders).insert(reminderCaptor.capture());
        assertTrue(reminderCaptor.getValue().getMessage().endsWith("系统将按配置流转至财务复核"));
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

    private ProcessActiveTaskEntity dueSoonTask() {
        ProcessActiveTaskEntity task = task();
        task.setId("task-due-soon");
        task.setDueAt(LocalDateTime.of(2026, 7, 28, 10, 20));
        task.setCandidateUserIds("[\"approver-1\"]");
        return task;
    }

    private ProcessNodeEntity reminderNode() {
        ProcessNodeEntity node = new ProcessNodeEntity();
        node.setDefinitionId("definition-1");
        node.setNodeCode("approve");
        node.setReminderConfig("{\"enabled\":true,\"beforeDueMinutes\":30}");
        return node;
    }

    private ProcessNodeEntity timeoutJumpNode() {
        ProcessNodeEntity node = reminderNode();
        node.setTimeoutConfig("{\"enabled\":true,\"durationMinutes\":30,\"action\":\"JUMP\","
                + "\"targetNodeCode\":\"finance-review\"}");
        return node;
    }

    private ProcessInstanceEntity instance() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId("instance-1");
        instance.setProcessCode("expense");
        instance.setInstanceTitle("Expense approval");
        instance.setInstanceStatus("RUNNING");
        return instance;
    }

    private ProcessReminderRecordEntity sentReminder(String id) {
        ProcessReminderRecordEntity reminder = new ProcessReminderRecordEntity();
        reminder.setId(id);
        reminder.setInstanceId("instance-1");
        reminder.setTaskId("task-due-soon");
        reminder.setReminderType(ReminderTypeEnum.DUE_SOON.name());
        reminder.setTargetUserIds("[\"approver-1\"]");
        reminder.setMessage("Task due soon reminder: approve");
        reminder.setReminderStatus("SENT");
        reminder.setCreatedBy("system_due_soon");
        reminder.setCreatedAt(LocalDateTime.of(2026, 7, 28, 10, 0));
        reminder.setSentAt(LocalDateTime.of(2026, 7, 28, 10, 0));
        return reminder;
    }

    private ProcessAlertRecordEntity existingAlert() {
        return existingAlert("OPEN");
    }

    private ProcessAlertRecordEntity existingAlert(String status) {
        ProcessAlertRecordEntity alert = new ProcessAlertRecordEntity();
        alert.setId("alert-1");
        alert.setTaskId("task-timeout");
        alert.setAlertType(AlertTypeEnum.TASK_TIMEOUT.name());
        alert.setAlertStatus(status);
        return alert;
    }
}
