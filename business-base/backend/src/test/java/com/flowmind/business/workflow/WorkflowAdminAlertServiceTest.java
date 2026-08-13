package com.flowmind.business.workflow;

import com.flowmind.business.common.OperationIdFactory;
import com.flowmind.business.platform.PlatformFacade;
import com.flowmind.business.security.BusinessAccessDeniedException;
import com.flowmind.business.security.BusinessAuthorizationProvider;
import com.flowmind.business.workflow.dto.WorkflowPageResponse;
import com.flowmind.platform.api.dto.AlertDTO;
import com.flowmind.platform.api.dto.AlertQuery;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.AlertStatusEnum;
import com.flowmind.platform.api.request.HandleAlertRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowAdminAlertServiceTest {

    private PlatformFacade facade;
    private BusinessAuthorizationProvider authorizationProvider;
    private WorkflowAdminAlertService service;

    @BeforeEach
    void setUp() {
        facade = mock(PlatformFacade.class);
        authorizationProvider = mock(BusinessAuthorizationProvider.class);
        service = new WorkflowAdminAlertService(facade, authorizationProvider, new OperationIdFactory());
        when(facade.currentUser()).thenReturn(new UserContext("admin-1", "Admin", "dept-1", "Dept"));
        when(authorizationProvider.isAdministrator("admin-1")).thenReturn(true);
    }

    @Test
    void administratorCanQueryAlertsWithBusinessPageShape() {
        AlertDTO alert = new AlertDTO();
        alert.setAlertId("alert-1");
        PageResult<AlertDTO> page = new PageResult<AlertDTO>();
        page.setRecords(Arrays.asList(alert));
        page.setPageNo(2);
        page.setPageSize(10);
        page.setTotal(21L);
        page.setTotalPages(3);
        when(facade.queryAlerts(any(AlertQuery.class))).thenReturn(page);

        AlertQuery query = new AlertQuery();
        query.setPageNo(2);
        query.setPageSize(10);
        query.setAlertStatus(AlertStatusEnum.OPEN);

        WorkflowPageResponse<AlertDTO> result = service.query(query);

        assertThat(result.getRecords()).containsExactly(alert);
        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(21L);
        assertThat(result.getTotalPages()).isEqualTo(3);
        ArgumentCaptor<AlertQuery> captor = ArgumentCaptor.forClass(AlertQuery.class);
        verify(facade).queryAlerts(captor.capture());
        assertThat(captor.getValue().getAlertStatus()).isEqualTo(AlertStatusEnum.OPEN);
    }

    @Test
    void administratorHandleAlertUsesTrustedOperatorAndStableOperationId() {
        AlertDTO handled = new AlertDTO();
        handled.setAlertId("alert-1");
        handled.setAlertStatus(AlertStatusEnum.HANDLED);
        when(facade.handleAlert(any(HandleAlertRequest.class))).thenReturn(handled);

        HandleAlertRequest request = new HandleAlertRequest();
        request.setTargetStatus(AlertStatusEnum.HANDLED);
        request.setComment("已确认");
        request.setOperatorUserId("forged-user");
        request.setOperationId("forged-operation");

        AlertDTO result = service.handle("alert-1", "retry-key", request);

        assertThat(result).isSameAs(handled);
        ArgumentCaptor<HandleAlertRequest> captor = ArgumentCaptor.forClass(HandleAlertRequest.class);
        verify(facade).handleAlert(captor.capture());
        assertThat(captor.getValue().getAlertId()).isEqualTo("alert-1");
        assertThat(captor.getValue().getOperatorUserId()).isEqualTo("admin-1");
        assertThat(captor.getValue().getTargetStatus()).isEqualTo(AlertStatusEnum.HANDLED);
        assertThat(captor.getValue().getComment()).isEqualTo("已确认");
        assertThat(captor.getValue().getOperationId()).isNotBlank();
        assertThat(captor.getValue().getOperationId()).isNotEqualTo("forged-operation");
    }

    @Test
    void nonAdministratorCannotQueryOrHandleAlerts() {
        when(authorizationProvider.isAdministrator("admin-1")).thenReturn(false);

        assertThatThrownBy(() -> service.query(new AlertQuery()))
                .isInstanceOf(BusinessAccessDeniedException.class);
        assertThatThrownBy(() -> service.handle("alert-1", "retry-key", new HandleAlertRequest()))
                .isInstanceOf(BusinessAccessDeniedException.class);
        verify(facade, never()).queryAlerts(any(AlertQuery.class));
        verify(facade, never()).handleAlert(any(HandleAlertRequest.class));
    }
}