package com.flowmind.business.security;

import com.flowmind.platform.api.dto.DepartmentDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.spi.OrganizationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformCurrentUserAdapterTest {

    @Test
    void preservesTrustedIdsAndResolvesServerSideNames() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("directory", directory());
        PlatformCurrentUserAdapter adapter = new PlatformCurrentUserAdapter(
                () -> new CurrentBusinessUserProvider.BusinessUser("user-1", "dept-1"),
                beans.getBeanProvider(OrganizationProvider.class));

        UserContext result = adapter.getCurrentUser();

        assertEquals("user-1", result.getUserId());
        assertEquals("Server User", result.getUserName());
        assertEquals("dept-1", result.getDepartmentId());
        assertEquals("Server Department", result.getDepartmentName());
    }

    @Test
    void fallsBackToTrustedIdsWhenDirectoryIsUnavailable() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        PlatformCurrentUserAdapter adapter = new PlatformCurrentUserAdapter(
                () -> new CurrentBusinessUserProvider.BusinessUser("user-1", "dept-1"),
                beans.getBeanProvider(OrganizationProvider.class));

        UserContext result = adapter.getCurrentUser();

        assertEquals("user-1", result.getUserName());
        assertEquals("dept-1", result.getDepartmentName());
    }

    private OrganizationProvider directory() {
        return new OrganizationProvider() {
            @Override
            public List<DepartmentDTO> listDepartments() {
                return Collections.emptyList();
            }

            @Override
            public List<UserDTO> listUsersByDepartment(String departmentId) {
                return Collections.emptyList();
            }

            @Override
            public List<UserDTO> listUsersByRole(String roleCode) {
                return Collections.emptyList();
            }

            @Override
            public List<UserDTO> listUsersByRoleAndDepartment(String roleCode, String departmentId) {
                return Collections.emptyList();
            }

            @Override
            public Optional<UserDTO> findUser(String userId) {
                return Optional.of(new UserDTO(userId, "Server User"));
            }

            @Override
            public Optional<DepartmentDTO> findDepartment(String departmentId) {
                return Optional.of(new DepartmentDTO(departmentId, "Server Department", null));
            }
        };
    }
}
