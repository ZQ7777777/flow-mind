package com.flowmind.platform.mock;

import com.flowmind.platform.api.dto.DepartmentDTO;
import com.flowmind.platform.api.dto.UserDTO;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryOrganizationProviderTest {

    @Test
    void defaultFixturesCanFindUsersDepartmentsAndRoles() {
        InMemoryOrganizationProvider provider = new InMemoryOrganizationProvider();

        assertTrue(provider.findDepartment("dept_sales").isPresent());
        assertEquals("Sales User", provider.findUser("user_sales").get().getUserName());
        assertEquals("user_sales_manager",
                provider.listUsersByRoleAndDepartment("manager", "dept_sales").get(0).getUserId());
        assertEquals("user_finance", provider.listUsersByRole("finance").get(0).getUserId());
    }

    @Test
    void returnsEmptyListsForMissingDepartmentOrRole() {
        InMemoryOrganizationProvider provider = InMemoryOrganizationProvider.empty();

        assertTrue(provider.listUsersByDepartment("missing-dept").isEmpty());
        assertTrue(provider.listUsersByRole("missing-role").isEmpty());
        assertTrue(provider.listUsersByRoleAndDepartment("manager", "missing-dept").isEmpty());
    }

    @Test
    void returnsDefensiveSnapshots() {
        InMemoryOrganizationProvider provider = InMemoryOrganizationProvider.empty();
        provider.addDepartment(new DepartmentDTO("dept-1", "Department One", null));
        UserDTO user = new UserDTO("user-1", "User One");
        user.setDepartmentId("dept-1");
        user.setDepartmentName("Department One");
        user.setRoleCodes(Collections.singletonList("manager"));
        user.setActive(Boolean.TRUE);
        provider.addUser(user);

        List<UserDTO> users = provider.listUsersByDepartment("dept-1");
        users.get(0).setUserName("Changed");

        assertEquals("User One", provider.findUser("user-1").get().getUserName());
        assertThrows(UnsupportedOperationException.class, () -> provider.listDepartments().clear());
    }

    @Test
    void rejectsBlankKeys() {
        InMemoryOrganizationProvider provider = InMemoryOrganizationProvider.empty();

        assertThrows(IllegalArgumentException.class, () -> provider.findUser(" "));
        assertThrows(IllegalArgumentException.class, () -> provider.listUsersByRole(null));
    }
}
