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

        assertTrue(provider.findDepartment("mock-dept").isPresent());
        assertTrue(provider.findDepartment("dept_finance").isPresent());
        assertEquals(3, provider.listUsersByDepartment("dept_finance").size());
        assertEquals("业务员", provider.findUser("u_sales_01").get().getUserName());
        assertEquals("u_dept_manager_01",
                provider.listUsersByRoleAndDepartment("manager", "dept_sales").get(0).getUserId());
        assertEquals("u_dept_manager_02",
                provider.listUsersByRoleAndDepartment("manager", "dept_finance").get(0).getUserId());
        assertEquals("u_finance_01", provider.listUsersByRole("finance").get(0).getUserId());
        assertEquals("u_group_leader_01", provider.listUsersByRole("group").get(0).getUserId());
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
