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
import com.flowmind.platform.api.request.HandleAlertRequest;
import org.springframework.stereotype.Service;

@Service
public class WorkflowAdminAlertService {
    private static final String DOMAIN = "workflow-admin-alert";

    private final PlatformFacade platformFacade;
    private final BusinessAuthorizationProvider authorizationProvider;
    private final OperationIdFactory operationIdFactory;

    public WorkflowAdminAlertService(PlatformFacade platformFacade, BusinessAuthorizationProvider authorizationProvider,
                                     OperationIdFactory operationIdFactory) {
        this.platformFacade = platformFacade;
        this.authorizationProvider = authorizationProvider;
        this.operationIdFactory = operationIdFactory;
    }

    public WorkflowPageResponse<AlertDTO> query(AlertQuery query) {
        UserContext currentUser = requireAdministrator();
        PageResult<AlertDTO> page = platformFacade.queryAlerts(query == null ? new AlertQuery() : query);
        WorkflowPageResponse<AlertDTO> response = new WorkflowPageResponse<AlertDTO>();
        response.setRecords(page.getRecords());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setTotal(page.getTotal());
        response.setTotalPages(page.getTotalPages());
        return response;
    }

    public AlertDTO handle(String alertId, String idempotencyKey, HandleAlertRequest input) {
        UserContext currentUser = requireAdministrator();
        HandleAlertRequest request = new HandleAlertRequest();
        request.setAlertId(alertId);
        request.setTargetStatus(input == null ? null : input.getTargetStatus());
        request.setComment(input == null ? null : input.getComment());
        request.setOperatorUserId(currentUser.getUserId());
        request.setOperationId(operationIdFactory.create(DOMAIN, "handle", alertId, currentUser.getUserId(), idempotencyKey));
        return platformFacade.handleAlert(request);
    }

    private UserContext requireAdministrator() {
        UserContext currentUser = platformFacade.currentUser();
        if (currentUser == null || !authorizationProvider.isAdministrator(currentUser.getUserId())) {
            throw new BusinessAccessDeniedException("仅管理员可以处理流程告警");
        }
        return currentUser;
    }
}