package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.AlertDTO;
import com.flowmind.platform.api.dto.AdminHistoryTaskQuery;
import com.flowmind.platform.api.dto.AdminInstanceQuery;
import com.flowmind.platform.api.dto.AdminTaskQuery;
import com.flowmind.platform.api.dto.AlertQuery;
import com.flowmind.platform.api.dto.AuditLogDTO;
import com.flowmind.platform.api.dto.AuditLogQuery;
import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ReadRecordDTO;
import com.flowmind.platform.api.dto.ReadRecordQuery;
import com.flowmind.platform.api.dto.ReminderDTO;
import com.flowmind.platform.api.dto.ReminderQuery;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.TaskGroupViewDTO;
import com.flowmind.platform.api.enums.OperationTargetTypeEnum;
import com.flowmind.platform.api.request.HandleAlertRequest;
import com.flowmind.platform.api.request.AddSignRequest;
import com.flowmind.platform.api.request.ClaimTaskRequest;
import com.flowmind.platform.api.request.DirectSendRequest;
import com.flowmind.platform.api.request.RejectTaskRequest;
import com.flowmind.platform.api.request.RemindTaskRequest;
import com.flowmind.platform.api.request.ReturnTaskRequest;
import com.flowmind.platform.api.request.TimeoutScanRequest;
import com.flowmind.platform.api.request.TransferTaskRequest;
import com.flowmind.platform.api.request.UnclaimTaskRequest;
import com.flowmind.platform.api.request.WithdrawTaskRequest;
import com.flowmind.platform.api.service.AdminProcessService;
import com.flowmind.platform.api.service.ProcessMonitorService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.service.TaskQueryService;
import com.flowmind.platform.core.query.ReadRecordManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M5 REST 路由、参数绑定和服务委派验收。
 */
class M5RestAcceptanceTest {

    private ProcessMonitorService monitorService;
    private AdminProcessService adminService;
    private TaskQueryService taskQueryService;
    private ReadRecordManager readRecordManager;
    private ProcessRuntimeService runtimeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        monitorService = mock(ProcessMonitorService.class);
        adminService = mock(AdminProcessService.class);
        taskQueryService = mock(TaskQueryService.class);
        readRecordManager = mock(ReadRecordManager.class);
        runtimeService = mock(ProcessRuntimeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ProcessMonitorController(monitorService),
                new AdminQueryController(adminService),
                new PlatformQueryController(taskQueryService, runtimeService, readRecordManager),
                new ProcessRuntimeController(runtimeService))
                .build();
    }

    @Test
    void monitorEndpointsBindPathBodyAndPageQueries() throws Exception {
        ReminderDTO reminder = new ReminderDTO();
        reminder.setReminderId("reminder-1");
        reminder.setTaskId("task-1");
        when(monitorService.remindTask(any(RemindTaskRequest.class))).thenReturn(reminder);
        when(monitorService.queryReminders(any(ReminderQuery.class)))
                .thenReturn(page(Collections.singletonList(reminder)));
        TaskDTO timedOut = new TaskDTO();
        timedOut.setTaskId("task-timeout");
        when(monitorService.scanTimeoutTasks(any(TimeoutScanRequest.class)))
                .thenReturn(Collections.singletonList(timedOut));

        mockMvc.perform(post("/api/platform/tasks/task-1/remind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationId\":\"remind-1\",\"operatorUserId\":\"operator\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("task-1"));

        ArgumentCaptor<RemindTaskRequest> remind = ArgumentCaptor.forClass(RemindTaskRequest.class);
        verify(monitorService).remindTask(remind.capture());
        assertEquals("task-1", remind.getValue().getTaskId());

        mockMvc.perform(get("/api/platform/reminders")
                        .param("taskId", "task-1")
                        .param("pageNo", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].reminderId").value("reminder-1"));

        ArgumentCaptor<ReminderQuery> query = ArgumentCaptor.forClass(ReminderQuery.class);
        verify(monitorService).queryReminders(query.capture());
        assertEquals("task-1", query.getValue().getTaskId());
        assertEquals(Integer.valueOf(2), query.getValue().getPageNo());

        mockMvc.perform(post("/api/platform/admin/timeout-scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationId\":\"scan-1\",\"operatorUserId\":\"admin\",\"limit\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value("task-timeout"));
        verify(monitorService).scanTimeoutTasks(any(TimeoutScanRequest.class));
    }

    @Test
    void alertHandleEndpointUsesPathAlertId() throws Exception {
        AlertDTO alert = new AlertDTO();
        alert.setAlertId("alert-1");
        when(monitorService.handleAlert(any(HandleAlertRequest.class))).thenReturn(alert);
        when(monitorService.queryAlerts(any(AlertQuery.class)))
                .thenReturn(page(Collections.singletonList(alert)));

        mockMvc.perform(get("/api/platform/admin/alerts").param("instanceId", "instance-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].alertId").value("alert-1"));

        mockMvc.perform(post("/api/platform/admin/alerts/alert-1/handle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationId\":\"handle-1\",\"operatorUserId\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertId").value("alert-1"));

        ArgumentCaptor<HandleAlertRequest> request = ArgumentCaptor.forClass(HandleAlertRequest.class);
        verify(monitorService).handleAlert(request.capture());
        assertEquals("alert-1", request.getValue().getAlertId());
        ArgumentCaptor<AlertQuery> query = ArgumentCaptor.forClass(AlertQuery.class);
        verify(monitorService).queryAlerts(query.capture());
        assertEquals("instance-1", query.getValue().getInstanceId());
    }

    @Test
    void adminAuditAndCallbackEndpointsBindFilters() throws Exception {
        when(adminService.queryAuditLogs(any(AuditLogQuery.class)))
                .thenReturn(page(Collections.<AuditLogDTO>emptyList()));
        when(adminService.queryCallbackLogs(any(CallbackLogQuery.class)))
                .thenReturn(page(Collections.<CallbackLogDTO>emptyList()));

        mockMvc.perform(get("/api/platform/admin/audit-logs")
                        .param("instanceId", "instance-1")
                        .param("targetType", "TASK")
                        .param("operatorUserId", "reviewer"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/admin/callback-logs")
                        .param("instanceId", "instance-1"))
                .andExpect(status().isOk());

        ArgumentCaptor<AuditLogQuery> audit = ArgumentCaptor.forClass(AuditLogQuery.class);
        verify(adminService).queryAuditLogs(audit.capture());
        assertEquals("instance-1", audit.getValue().getInstanceId());
        assertEquals(OperationTargetTypeEnum.TASK, audit.getValue().getTargetType());
        ArgumentCaptor<CallbackLogQuery> callback = ArgumentCaptor.forClass(CallbackLogQuery.class);
        verify(adminService).queryCallbackLogs(callback.capture());
        assertEquals("instance-1", callback.getValue().getInstanceId());
    }

    @Test
    void adminRuntimeQueryEndpointsBindFiltersAndPaths() throws Exception {
        when(adminService.queryInstances(any(AdminInstanceQuery.class)))
                .thenReturn(page(Collections.<ProcessInstanceDTO>emptyList()));
        when(adminService.queryActiveTasks(any(AdminTaskQuery.class)))
                .thenReturn(page(Collections.<TaskDTO>emptyList()));
        when(adminService.queryHistoryTasks(any(AdminHistoryTaskQuery.class)))
                .thenReturn(page(Collections.<HistoryTaskDTO>emptyList()));
        when(adminService.queryTaskGroups("instance-1"))
                .thenReturn(Collections.<TaskGroupViewDTO>emptyList());

        mockMvc.perform(get("/api/platform/admin/instances")
                        .param("processCode", "expense")
                        .param("currentNodeCode", "review"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/admin/tasks")
                        .param("instanceId", "instance-1")
                        .param("taskGroupId", "group-1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/admin/history-tasks")
                        .param("instanceId", "instance-1")
                        .param("actionType", "APPROVE"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/admin/instances/instance-1/task-groups"))
                .andExpect(status().isOk());

        ArgumentCaptor<AdminInstanceQuery> instances = ArgumentCaptor.forClass(AdminInstanceQuery.class);
        verify(adminService).queryInstances(instances.capture());
        assertEquals("expense", instances.getValue().getProcessCode());
        assertEquals("review", instances.getValue().getCurrentNodeCode());
        ArgumentCaptor<AdminTaskQuery> activeTasks = ArgumentCaptor.forClass(AdminTaskQuery.class);
        verify(adminService).queryActiveTasks(activeTasks.capture());
        assertEquals("group-1", activeTasks.getValue().getTaskGroupId());
        ArgumentCaptor<AdminHistoryTaskQuery> histories = ArgumentCaptor.forClass(AdminHistoryTaskQuery.class);
        verify(adminService).queryHistoryTasks(histories.capture());
        assertEquals("instance-1", histories.getValue().getInstanceId());
        verify(adminService).queryTaskGroups("instance-1");
    }

    @Test
    void readEndpointsUseTrustedPathInstanceId() throws Exception {
        ReadRecordDTO record = new ReadRecordDTO();
        record.setReadRecordId("read-1");
        record.setInstanceId("instance-1");
        when(readRecordManager.markRead("instance-1")).thenReturn(record);
        when(taskQueryService.queryReadRecords(any(ReadRecordQuery.class)))
                .thenReturn(page(Collections.singletonList(record)));

        mockMvc.perform(post("/api/platform/instances/instance-1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readRecordId").value("read-1"));
        mockMvc.perform(get("/api/platform/instances/instance-1/read-records")
                        .param("userId", "reader"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].instanceId").value("instance-1"));

        verify(readRecordManager).markRead("instance-1");
        ArgumentCaptor<ReadRecordQuery> query = ArgumentCaptor.forClass(ReadRecordQuery.class);
        verify(taskQueryService).queryReadRecords(query.capture());
        assertEquals("instance-1", query.getValue().getInstanceId());
        assertEquals("reader", query.getValue().getUserId());
    }

    @Test
    void enhancedActionEndpointsBindJsonAndDelegateToRuntimeService() throws Exception {
        String common = "{\"operationId\":\"op-1\",\"taskId\":\"task-1\","
                + "\"expectedTaskVersion\":0,\"operatorUserId\":\"reviewer\"";
        performAction("/api/platform/runtime/tasks/reject", common + ",\"targetNodeCode\":\"apply\"}");
        performAction("/api/platform/runtime/tasks/return", common + "}");
        performAction("/api/platform/runtime/tasks/withdraw", common + "}");
        performAction("/api/platform/runtime/tasks/direct-send", common + ",\"targetNodeCode\":\"review\"}");
        performAction("/api/platform/runtime/tasks/transfer", common + ",\"targetUserId\":\"receiver\"}");
        performAction("/api/platform/runtime/tasks/add-sign", common + ",\"addSignUserIds\":[\"receiver\"]}");
        performAction("/api/platform/runtime/tasks/claim", common + "}");
        performAction("/api/platform/runtime/tasks/unclaim", common + "}");

        ArgumentCaptor<RejectTaskRequest> reject = ArgumentCaptor.forClass(RejectTaskRequest.class);
        verify(runtimeService).reject(reject.capture());
        assertCommonTaskFields(reject.getValue());
        assertEquals("apply", reject.getValue().getTargetNodeCode());
        verify(runtimeService).returnToStarter(any(ReturnTaskRequest.class));
        verify(runtimeService).withdraw(any(WithdrawTaskRequest.class));
        ArgumentCaptor<DirectSendRequest> directSend = ArgumentCaptor.forClass(DirectSendRequest.class);
        verify(runtimeService).directSend(directSend.capture());
        assertEquals("review", directSend.getValue().getTargetNodeCode());
        ArgumentCaptor<TransferTaskRequest> transfer = ArgumentCaptor.forClass(TransferTaskRequest.class);
        verify(runtimeService).transfer(transfer.capture());
        assertEquals("receiver", transfer.getValue().getTargetUserId());
        ArgumentCaptor<AddSignRequest> addSign = ArgumentCaptor.forClass(AddSignRequest.class);
        verify(runtimeService).addSign(addSign.capture());
        assertEquals(Collections.singletonList("receiver"), addSign.getValue().getAddSignUserIds());
        verify(runtimeService).claim(any(ClaimTaskRequest.class));
        verify(runtimeService).unclaim(any(UnclaimTaskRequest.class));
    }

    private void assertCommonTaskFields(com.flowmind.platform.api.request.TaskOperationRequest request) {
        assertEquals("op-1", request.getOperationId());
        assertEquals("task-1", request.getTaskId());
        assertEquals(Long.valueOf(0L), request.getExpectedTaskVersion());
        assertEquals("reviewer", request.getOperatorUserId());
    }

    private void performAction(String path, String json) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private <T> PageResult<T> page(java.util.List<T> records) {
        PageResult<T> result = new PageResult<T>();
        result.setRecords(records);
        result.setPageNo(Integer.valueOf(1));
        result.setPageSize(Integer.valueOf(20));
        result.setTotal(Long.valueOf(records.size()));
        result.setTotalPages(Integer.valueOf(records.isEmpty() ? 0 : 1));
        return result;
    }
}
