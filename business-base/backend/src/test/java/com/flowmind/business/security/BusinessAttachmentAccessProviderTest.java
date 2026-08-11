package com.flowmind.business.security;

import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.enums.AttachmentAccessActionEnum;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.request.AttachmentAccessRequest;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessAttachmentAccessProviderTest {

    @Test
    void permitsTheProcessStarterAndRejectsAnOutsider() {
        ProcessRuntimeService runtimeService = mock(ProcessRuntimeService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProcessRuntimeService> runtimeServiceProvider = mock(ObjectProvider.class);
        when(runtimeServiceProvider.getIfAvailable()).thenReturn(runtimeService);

        ProcessInstanceDetailDTO instance = new ProcessInstanceDetailDTO();
        instance.setStarterUserId("starter");
        instance.setActiveTasks(Collections.emptyList());
        instance.setHistoryTasks(Collections.emptyList());
        when(runtimeService.getInstance("instance-1")).thenReturn(instance);

        BusinessAttachmentAccessProvider provider = new BusinessAttachmentAccessProvider(
                runtimeServiceProvider, new WorkflowAccessGuard(userId -> false));

        assertTrue(provider.isAllowed(request("starter", "instance-1")));
        assertFalse(provider.isAllowed(request("outsider", "instance-1")));
    }

    @Test
    void rejectsIncompleteRequestsAndUnavailableInstances() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ProcessRuntimeService> runtimeServiceProvider = mock(ObjectProvider.class);
        when(runtimeServiceProvider.getIfAvailable()).thenReturn(null);
        BusinessAttachmentAccessProvider provider = new BusinessAttachmentAccessProvider(
                runtimeServiceProvider, new WorkflowAccessGuard(userId -> false));

        assertFalse(provider.isAllowed(null));
        assertFalse(provider.isAllowed(request(null, "instance-1")));
        assertFalse(provider.isAllowed(request("starter", null)));
        assertFalse(provider.isAllowed(request("starter", "instance-1")));
    }

    private AttachmentAccessRequest request(String userId, String instanceId) {
        return new AttachmentAccessRequest(userId, userId, AttachmentAccessActionEnum.UPLOAD,
                instanceId, "task-1", null, AttachmentOwnerTypeEnum.INSTANCE);
    }
}
