package com.flowmind.business.admin;

import com.flowmind.platform.api.dto.DepartmentDTO;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.spi.OrganizationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessDefinitionOptionsServiceTest {

    @Test
    void returnsActiveUsersDepartmentsAndDeduplicatedRoles() {
        OrganizationProvider provider = mock(OrganizationProvider.class);
        ObjectProvider<OrganizationProvider> objectProvider = provider(provider);
        DepartmentDTO department = new DepartmentDTO("d1", "财务部", null);
        UserDTO active = user("u1", true, "finance", "manager", "finance");
        active.setDepartmentId("d1");
        active.setDepartmentName("财务部");
        UserDTO inactive = user("u2", false, "finance");
        when(provider.listDepartments()).thenReturn(Collections.singletonList(department));
        when(provider.listUsersByDepartment("d1")).thenReturn(Arrays.asList(active, inactive, active));

        ProcessDefinitionOptionsResponse result = new ProcessDefinitionOptionsService(objectProvider).options();

        assertThat(result.getDepartments()).extracting(ProcessDefinitionOptionsResponse.DepartmentOption::getDepartmentId)
                .containsExactly("d1");
        assertThat(result.getUsers()).extracting(ProcessDefinitionOptionsResponse.UserOption::getUserId)
                .containsExactly("u1");
        assertThat(result.getRoles()).extracting(ProcessDefinitionOptionsResponse.RoleOption::getRoleCode)
                .containsExactly("finance", "manager");
    }

    @Test
    void returnsEmptyOptionsWhenProviderIsUnavailableOrFails() {
        ObjectProvider<OrganizationProvider> unavailable = provider(null);
        assertThat(new ProcessDefinitionOptionsService(unavailable).options().getUsers()).isEmpty();

        OrganizationProvider failing = mock(OrganizationProvider.class);
        when(failing.listDepartments()).thenThrow(new IllegalStateException("directory unavailable"));
        ProcessDefinitionOptionsResponse result = new ProcessDefinitionOptionsService(provider(failing)).options();
        assertThat(result.getUsers()).isEmpty();
        assertThat(result.getDepartments()).isEmpty();
        assertThat(result.getRoles()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<OrganizationProvider> provider(OrganizationProvider value) {
        ObjectProvider<OrganizationProvider> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private UserDTO user(String id, boolean active, String... roles) {
        UserDTO user = new UserDTO(id, id);
        user.setActive(active);
        user.setRoleCodes(Arrays.asList(roles));
        return user;
    }
}
