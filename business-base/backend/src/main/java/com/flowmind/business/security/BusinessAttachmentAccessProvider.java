package com.flowmind.business.security;

import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.request.AttachmentAccessRequest;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.spi.AttachmentAccessProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Applies the business application's process-participant policy to attachment access.
 */
@Component
public class BusinessAttachmentAccessProvider implements AttachmentAccessProvider {

    private final ObjectProvider<ProcessRuntimeService> runtimeServiceProvider;
    private final WorkflowAccessGuard workflowAccessGuard;

    public BusinessAttachmentAccessProvider(ObjectProvider<ProcessRuntimeService> runtimeServiceProvider,
                                            WorkflowAccessGuard workflowAccessGuard) {
        this.runtimeServiceProvider = runtimeServiceProvider;
        this.workflowAccessGuard = workflowAccessGuard;
    }

    @Override
    public boolean isAllowed(AttachmentAccessRequest request) {
        if (request == null || !hasText(request.getUserId()) || !hasText(request.getInstanceId())) {
            return false;
        }

        ProcessRuntimeService runtimeService = runtimeServiceProvider.getIfAvailable();
        if (runtimeService == null) {
            return false;
        }

        try {
            ProcessInstanceDetailDTO instance = runtimeService.getInstance(request.getInstanceId());
            workflowAccessGuard.check(instance, instance.getActiveTasks(), instance.getHistoryTasks(),
                    request.getUserId());
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
