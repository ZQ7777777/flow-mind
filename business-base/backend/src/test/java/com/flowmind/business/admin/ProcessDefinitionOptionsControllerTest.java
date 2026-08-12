package com.flowmind.business.admin;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessDefinitionOptionsControllerTest {

    @Test
    void requiresAdministratorBeforeReturningOptions() {
        ProcessDefinitionOptionsService service = mock(ProcessDefinitionOptionsService.class);
        AdminAccessGuard guard = mock(AdminAccessGuard.class);
        ProcessDefinitionOptionsResponse response = new ProcessDefinitionOptionsResponse(
                Collections.<ProcessDefinitionOptionsResponse.UserOption>emptyList(),
                Collections.<ProcessDefinitionOptionsResponse.DepartmentOption>emptyList(),
                Collections.<ProcessDefinitionOptionsResponse.RoleOption>emptyList());
        when(guard.requireAdministrator()).thenReturn("admin");
        when(service.options()).thenReturn(response);

        ProcessDefinitionOptionsResponse actual = new ProcessDefinitionOptionsController(service, guard).options();

        assertThat(actual).isSameAs(response);
        verify(guard).requireAdministrator();
        verify(service).options();
    }
}
