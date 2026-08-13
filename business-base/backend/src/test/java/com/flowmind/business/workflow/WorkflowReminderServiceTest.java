package com.flowmind.business.workflow;

import com.flowmind.business.common.OperationIdFactory;
import com.flowmind.business.platform.PlatformDtoMapper;
import com.flowmind.business.platform.PlatformFacade;
import com.flowmind.business.workflow.dto.WorkflowActionRequests;
import com.flowmind.platform.api.dto.ReminderDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.request.RemindTaskRequest;
import com.flowmind.platform.api.service.ProcessMonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowReminderServiceTest {

    private PlatformFacade facade;
    private ProcessMonitorService monitorService;
    private WorkflowActionService service;
    private WorkflowFormValueValidator formValueValidator;

    @BeforeEach
    void setUp() {
        facade = mock(PlatformFacade.class);
        monitorService = mock(ProcessMonitorService.class);
        formValueValidator = mock(WorkflowFormValueValidator.class);
        service = new WorkflowActionService(facade, new PlatformDtoMapper(), new OperationIdFactory(),
                mock(WorkflowQueryService.class), formValueValidator, monitorService);
        when(facade.currentUser()).thenReturn(new UserContext("starter-1", "Starter", "dept-1", "Dept"));
        when(monitorService.remindTask(any(RemindTaskRequest.class))).thenReturn(new ReminderDTO());
    }

    @Test
    void delegatesReminderToPlatformMonitorServiceWithTrustedIdentity() {
        WorkflowActionRequests.Remind request = new WorkflowActionRequests.Remind();
        request.setExpectedTaskVersion(3L);
        request.setComment("请尽快处理");

        service.remind("task-1", "retry-key", request);

        ArgumentCaptor<RemindTaskRequest> captor = ArgumentCaptor.forClass(RemindTaskRequest.class);
        verify(monitorService).remindTask(captor.capture());
        assertThat(captor.getValue().getTaskId()).isEqualTo("task-1");
        assertThat(captor.getValue().getExpectedTaskVersion()).isEqualTo(3L);
        assertThat(captor.getValue().getOperatorUserId()).isEqualTo("starter-1");
        assertThat(captor.getValue().getComment()).isEqualTo("请尽快处理");
        assertThat(captor.getValue().getOperationId()).isNotBlank();
    }
}