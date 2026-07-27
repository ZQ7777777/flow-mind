package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.DepartmentDTO;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.request.ApproverResolveRequest;
import com.flowmind.platform.api.spi.OrganizationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultApproverResolverTest {

    private FakeOrganizationProvider organizationProvider;
    private DefaultApproverResolver resolver;

    @BeforeEach
    void setUp() {
        organizationProvider = new FakeOrganizationProvider();
        resolver = new DefaultApproverResolver(organizationProvider);
    }

    @Test
    void resolvesConfiguredUsersByIdAndSortsDistinctValidUsers() {
        organizationProvider.addUser(user("user-2", "User Two", "dept-2", "Department Two",
                Arrays.asList("finance"), Boolean.TRUE));
        organizationProvider.addUser(user("user-1", "User One", "dept-1", "Department One",
                Arrays.asList("manager"), Boolean.TRUE));

        ApproverResolveRequest request = request(ApproverRuleTypeEnum.USER);
        request.getApproverRuleConfig().put("userIds", Arrays.asList("user-2", "missing", "user-1", "user-2"));

        List<UserDTO> users = resolver.resolveApprovers(request);

        assertEquals(2, users.size());
        assertUser(users.get(0), "user-1", "User One");
        assertEquals("dept-1", users.get(0).getDepartmentId());
        assertEquals(Collections.singletonList("manager"), users.get(0).getRoleCodes());
        assertEquals(Boolean.TRUE, users.get(0).getActive());
        assertUser(users.get(1), "user-2", "User Two");
        assertEquals("dept-2", users.get(1).getDepartmentId());
    }

    @Test
    void resolvesStarterDepartmentRoleAndRoleInDepartmentRules() {
        organizationProvider.addUser("starter", "Starter");
        organizationProvider.addDepartmentUsers("dept-1",
                Arrays.asList(new UserDTO("dept-user", "Department User")));
        organizationProvider.addRoleUsers("manager",
                Arrays.asList(new UserDTO("role-user", "Role User")));
        organizationProvider.addRoleDepartmentUsers("manager", "dept-1",
                Arrays.asList(new UserDTO("dept-role-user", "Department Role User")));

        assertUser(single(request(ApproverRuleTypeEnum.STARTER)), "starter", "Starter");

        ApproverResolveRequest department = request(ApproverRuleTypeEnum.DEPARTMENT);
        department.getApproverRuleConfig().put("departmentId", "dept-1");
        assertUser(single(department), "dept-user", "Department User");

        ApproverResolveRequest role = request(ApproverRuleTypeEnum.ROLE);
        role.getApproverRuleConfig().put("roleCode", "manager");
        assertUser(single(role), "role-user", "Role User");

        ApproverResolveRequest roleInDepartment = request(ApproverRuleTypeEnum.ROLE_IN_DEPARTMENT);
        roleInDepartment.getApproverRuleConfig().put("roleCode", "manager");
        assertUser(single(roleInDepartment), "dept-role-user", "Department Role User");
    }

    @Test
    void rejectsMissingRuleConfigMissingExpressionAndEmptyResult() {
        RuntimeConfigurationException missingConfig = assertThrows(RuntimeConfigurationException.class,
                () -> resolver.resolveApprovers(request(ApproverRuleTypeEnum.ROLE)));
        assertEquals(RuntimeErrorCodes.NODE_CONFIG_INVALID, missingConfig.getErrorCode());

        RuntimeStateException expression = assertThrows(RuntimeStateException.class,
                () -> resolver.resolveApprovers(request(ApproverRuleTypeEnum.APPROVER_EXPRESSION)));
        assertEquals(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED, expression.getErrorCode());

        ApproverResolveRequest department = request(ApproverRuleTypeEnum.DEPARTMENT);
        department.getApproverRuleConfig().put("departmentId", "empty-dept");
        RuntimeStateException emptyResult = assertThrows(RuntimeStateException.class,
                () -> resolver.resolveApprovers(department));
        assertEquals(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED, emptyResult.getErrorCode());
    }

    @Test
    void resolvesApproverExpressionThroughAllowedContextAndOrganizationProvider() {
        organizationProvider.addRoleDepartmentUsers("manager", "dept-1",
                Arrays.asList(user("manager-2", "Manager Two", "dept-1", "Department One",
                        Arrays.asList("manager"), Boolean.TRUE),
                        user("manager-1", "Manager One", "dept-1", "Department One",
                                Arrays.asList("manager"), Boolean.TRUE)));
        ApproverResolveRequest request = request(ApproverRuleTypeEnum.APPROVER_EXPRESSION);
        request.getApproverRuleConfig().put("expression", "departmentManager(starterDeptId)");

        List<UserDTO> users = resolver.resolveApprovers(request);

        assertEquals(2, users.size());
        assertUser(users.get(0), "manager-1", "Manager One");
        assertUser(users.get(1), "manager-2", "Manager Two");
    }

    @Test
    void rejectsApproverExpressionThatReadsUnauthorizedContext() {
        ApproverResolveRequest request = request(ApproverRuleTypeEnum.APPROVER_EXPRESSION);
        request.getApproverRuleConfig().put("expression", "departmentManager(systemProperties)");

        RuntimeStateException exception = assertThrows(RuntimeStateException.class,
                () -> resolver.resolveApprovers(request));

        assertEquals(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED, exception.getErrorCode());
    }

    private UserDTO single(ApproverResolveRequest request) {
        List<UserDTO> users = resolver.resolveApprovers(request);
        assertEquals(1, users.size());
        return users.get(0);
    }

    private ApproverResolveRequest request(ApproverRuleTypeEnum ruleType) {
        ApproverResolveRequest request = new ApproverResolveRequest();
        request.setDefinitionId("definition-1");
        request.setInstanceId("instance-1");
        request.setNodeCode("review");
        request.setNodeName("Review");
        request.setApproverRuleType(ruleType);
        request.setApproverRuleConfig(new LinkedHashMap<String, Object>());
        request.setStarterUserId("starter");
        request.setStarterDeptId("dept-1");
        request.setVariables(new LinkedHashMap<String, Object>());
        return request;
    }

    private void assertUser(UserDTO user, String userId, String userName) {
        assertEquals(userId, user.getUserId());
        assertEquals(userName, user.getUserName());
    }

    private UserDTO user(String userId, String userName, String departmentId, String departmentName,
                         List<String> roleCodes, Boolean active) {
        UserDTO user = new UserDTO(userId, userName);
        user.setDepartmentId(departmentId);
        user.setDepartmentName(departmentName);
        user.setRoleCodes(roleCodes);
        user.setActive(active);
        return user;
    }

    private static final class FakeOrganizationProvider implements OrganizationProvider {
        private final Map<String, UserDTO> usersById = new LinkedHashMap<String, UserDTO>();
        private final Map<String, List<UserDTO>> usersByDepartment = new LinkedHashMap<String, List<UserDTO>>();
        private final Map<String, List<UserDTO>> usersByRole = new LinkedHashMap<String, List<UserDTO>>();
        private final Map<String, List<UserDTO>> usersByRoleDepartment = new LinkedHashMap<String, List<UserDTO>>();

        void addUser(String userId, String userName) {
            addUser(new UserDTO(userId, userName));
        }

        void addUser(UserDTO user) {
            usersById.put(user.getUserId(), user);
        }

        void addDepartmentUsers(String departmentId, List<UserDTO> users) {
            usersByDepartment.put(departmentId, users);
        }

        void addRoleUsers(String roleCode, List<UserDTO> users) {
            usersByRole.put(roleCode, users);
        }

        void addRoleDepartmentUsers(String roleCode, String departmentId, List<UserDTO> users) {
            usersByRoleDepartment.put(roleCode + "@" + departmentId, users);
        }

        @Override
        public List<DepartmentDTO> listDepartments() {
            return Collections.emptyList();
        }

        @Override
        public List<UserDTO> listUsersByDepartment(String departmentId) {
            return copy(usersByDepartment.get(departmentId));
        }

        @Override
        public List<UserDTO> listUsersByRole(String roleCode) {
            return copy(usersByRole.get(roleCode));
        }

        @Override
        public List<UserDTO> listUsersByRoleAndDepartment(String roleCode, String departmentId) {
            return copy(usersByRoleDepartment.get(roleCode + "@" + departmentId));
        }

        @Override
        public Optional<UserDTO> findUser(String userId) {
            return Optional.ofNullable(usersById.get(userId));
        }

        @Override
        public Optional<DepartmentDTO> findDepartment(String departmentId) {
            return Optional.empty();
        }

        private List<UserDTO> copy(List<UserDTO> users) {
            return users == null ? Collections.<UserDTO>emptyList() : new ArrayList<UserDTO>(users);
        }
    }
}
