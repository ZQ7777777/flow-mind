package com.flowmind.business.admin;

import com.flowmind.platform.api.dto.AdminHistoryTaskQuery;
import com.flowmind.platform.api.dto.AuditLogQuery;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.ReadRecordQuery;
import com.flowmind.platform.api.request.DeleteProcessInstanceRequest;
import com.flowmind.platform.api.request.TerminateProcessRequest;
import com.flowmind.platform.api.service.AdminProcessService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.service.TaskQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminProcessInstanceControllerTest {

    private AdminProcessService adminService;
    private ProcessRuntimeService runtimeService;
    private TaskQueryService queryService;
    private AdminProcessInstanceController controller;

    @BeforeEach
    void setUp() {
        adminService = mock(AdminProcessService.class);
        runtimeService = mock(ProcessRuntimeService.class);
        queryService = mock(TaskQueryService.class);
        AdminAccessGuard guard = mock(AdminAccessGuard.class);
        when(guard.requireAdministrator()).thenReturn("trusted-admin");
        controller = new AdminProcessInstanceController(adminService, runtimeService, queryService, guard);
    }

    @Test
    void scopesEveryTraceQueryToPathInstance() {
        AdminHistoryTaskQuery history = new AdminHistoryTaskQuery();
        CallbackLogQuery callbacks = new CallbackLogQuery();
        ReadRecordQuery reads = new ReadRecordQuery();
        AuditLogQuery audits = new AuditLogQuery();
        controller.historyTasks("instance-1", history);
        controller.comments("instance-1");
        controller.callbackLogs("instance-1", callbacks);
        controller.readRecords("instance-1", reads);
        controller.auditLogs("instance-1", audits);
        assertThat(history.getInstanceId()).isEqualTo("instance-1");
        assertThat(callbacks.getInstanceId()).isEqualTo("instance-1");
        assertThat(reads.getInstanceId()).isEqualTo("instance-1");
        assertThat(audits.getInstanceId()).isEqualTo("instance-1");
        verify(queryService).queryComments("instance-1");
    }

    @Test
    void overwritesInstanceAndOperatorForDestructiveOperations() {
        TerminateProcessRequest terminate = new TerminateProcessRequest();
        terminate.setInstanceId("spoofed"); terminate.setOperatorUserId("spoofed");
        DeleteProcessInstanceRequest delete = new DeleteProcessInstanceRequest();
        delete.setInstanceId("spoofed"); delete.setOperatorUserId("spoofed");
        controller.terminate("instance-1", terminate);
        controller.delete("instance-1", delete);
        assertThat(terminate.getInstanceId()).isEqualTo("instance-1");
        assertThat(terminate.getOperatorUserId()).isEqualTo("trusted-admin");
        assertThat(delete.getInstanceId()).isEqualTo("instance-1");
        assertThat(delete.getOperatorUserId()).isEqualTo("trusted-admin");
        verify(runtimeService).terminate(terminate);
        verify(runtimeService).deleteInstance(delete);
    }
}
