package com.flowmind.platform.mock;

import com.flowmind.platform.api.dto.DepartmentDTO;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.spi.OrganizationProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory organization provider for local verification and tests.
 */
public class InMemoryOrganizationProvider implements OrganizationProvider {

    private final Map<String, DepartmentDTO> departments = new LinkedHashMap<String, DepartmentDTO>();
    private final Map<String, UserDTO> users = new LinkedHashMap<String, UserDTO>();

    public InMemoryOrganizationProvider() {
        seedDefaults();
    }

    public static InMemoryOrganizationProvider empty() {
        return new InMemoryOrganizationProvider(false);
    }

    private InMemoryOrganizationProvider(boolean seedDefaults) {
        if (seedDefaults) {
            seedDefaults();
        }
    }

    public synchronized void addDepartment(DepartmentDTO department) {
        if (department == null || isBlank(department.getDepartmentId())) {
            throw new IllegalArgumentException("department.departmentId is required");
        }
        departments.put(department.getDepartmentId(), copyDepartment(department));
    }

    public synchronized void addUser(UserDTO user) {
        if (user == null || isBlank(user.getUserId())) {
            throw new IllegalArgumentException("user.userId is required");
        }
        users.put(user.getUserId(), copyUser(user));
    }

    @Override
    public synchronized List<DepartmentDTO> listDepartments() {
        List<DepartmentDTO> result = new ArrayList<DepartmentDTO>();
        for (DepartmentDTO department : departments.values()) {
            result.add(copyDepartment(department));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized List<UserDTO> listUsersByDepartment(String departmentId) {
        requireText(departmentId, "departmentId");
        List<UserDTO> result = new ArrayList<UserDTO>();
        for (UserDTO user : users.values()) {
            if (departmentId.equals(user.getDepartmentId())) {
                result.add(copyUser(user));
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized List<UserDTO> listUsersByRole(String roleCode) {
        requireText(roleCode, "roleCode");
        List<UserDTO> result = new ArrayList<UserDTO>();
        for (UserDTO user : users.values()) {
            if (hasRole(user, roleCode)) {
                result.add(copyUser(user));
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized List<UserDTO> listUsersByRoleAndDepartment(String roleCode, String departmentId) {
        requireText(roleCode, "roleCode");
        requireText(departmentId, "departmentId");
        List<UserDTO> result = new ArrayList<UserDTO>();
        for (UserDTO user : users.values()) {
            if (departmentId.equals(user.getDepartmentId()) && hasRole(user, roleCode)) {
                result.add(copyUser(user));
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized Optional<UserDTO> findUser(String userId) {
        requireText(userId, "userId");
        UserDTO user = users.get(userId);
        return user == null ? Optional.<UserDTO>empty() : Optional.of(copyUser(user));
    }

    @Override
    public synchronized Optional<DepartmentDTO> findDepartment(String departmentId) {
        requireText(departmentId, "departmentId");
        DepartmentDTO department = departments.get(departmentId);
        return department == null ? Optional.<DepartmentDTO>empty() : Optional.of(copyDepartment(department));
    }

    private void seedDefaults() {
        addDepartment(new DepartmentDTO("mock-dept", "Mock Department", null));
        addDepartment(new DepartmentDTO("dept_sales", "Sales Department", null));
        addDepartment(new DepartmentDTO("dept_manager", "Manager Department", null));
        addDepartment(new DepartmentDTO("dept_finance", "Finance Department", null));

        addUser(user("mock-user", "Mock User", "mock-dept", "Mock Department",
                Collections.singletonList("mock")));
        addUser(user("user_sales", "Sales User", "dept_sales", "Sales Department",
                Collections.singletonList("sales")));
        addUser(user("user_sales_manager", "Sales Manager", "dept_sales", "Sales Department",
                Collections.singletonList("manager")));
        addUser(user("user_manager", "Manager User", "dept_manager", "Manager Department",
                Collections.singletonList("manager")));
        addUser(user("user_finance", "Finance User", "dept_finance", "Finance Department",
                Collections.singletonList("finance")));
        addUser(user("user_admin", "Admin User", "dept_manager", "Manager Department",
                Arrays.asList("admin", "manager")));
    }

    private static UserDTO user(String userId, String userName, String departmentId, String departmentName,
                                List<String> roles) {
        UserDTO user = new UserDTO(userId, userName);
        user.setDepartmentId(departmentId);
        user.setDepartmentName(departmentName);
        user.setRoleCodes(new ArrayList<String>(roles));
        user.setActive(Boolean.TRUE);
        return user;
    }

    private static boolean hasRole(UserDTO user, String roleCode) {
        List<String> roles = user.getRoleCodes();
        return roles != null && roles.contains(roleCode);
    }

    private static UserDTO copyUser(UserDTO source) {
        UserDTO target = new UserDTO(source.getUserId(), source.getUserName());
        target.setDepartmentId(source.getDepartmentId());
        target.setDepartmentName(source.getDepartmentName());
        target.setRoleCodes(source.getRoleCodes() == null ? null : new ArrayList<String>(source.getRoleCodes()));
        target.setActive(source.getActive());
        return target;
    }

    private static DepartmentDTO copyDepartment(DepartmentDTO source) {
        return new DepartmentDTO(source.getDepartmentId(), source.getDepartmentName(), source.getParentDepartmentId());
    }

    private static void requireText(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
