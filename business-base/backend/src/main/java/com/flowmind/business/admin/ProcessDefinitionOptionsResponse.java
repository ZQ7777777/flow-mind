package com.flowmind.business.admin;

import java.util.Collections;
import java.util.List;

/** Administrator process-definition editor organization options. */
public class ProcessDefinitionOptionsResponse {
    private final List<UserOption> users;
    private final List<DepartmentOption> departments;
    private final List<RoleOption> roles;

    public ProcessDefinitionOptionsResponse(List<UserOption> users,
                                            List<DepartmentOption> departments,
                                            List<RoleOption> roles) {
        this.users = users == null ? Collections.<UserOption>emptyList() : users;
        this.departments = departments == null ? Collections.<DepartmentOption>emptyList() : departments;
        this.roles = roles == null ? Collections.<RoleOption>emptyList() : roles;
    }

    public List<UserOption> getUsers() { return users; }
    public List<DepartmentOption> getDepartments() { return departments; }
    public List<RoleOption> getRoles() { return roles; }

    public static class UserOption {
        private final String userId;
        private final String userName;
        private final String departmentId;
        private final String departmentName;
        private final List<String> roleCodes;

        public UserOption(String userId, String userName, String departmentId,
                          String departmentName, List<String> roleCodes) {
            this.userId = userId;
            this.userName = userName;
            this.departmentId = departmentId;
            this.departmentName = departmentName;
            this.roleCodes = roleCodes == null ? Collections.<String>emptyList() : roleCodes;
        }

        public String getUserId() { return userId; }
        public String getUserName() { return userName; }
        public String getDepartmentId() { return departmentId; }
        public String getDepartmentName() { return departmentName; }
        public List<String> getRoleCodes() { return roleCodes; }
    }

    public static class DepartmentOption {
        private final String departmentId;
        private final String departmentName;
        private final String parentDepartmentId;

        public DepartmentOption(String departmentId, String departmentName, String parentDepartmentId) {
            this.departmentId = departmentId;
            this.departmentName = departmentName;
            this.parentDepartmentId = parentDepartmentId;
        }

        public String getDepartmentId() { return departmentId; }
        public String getDepartmentName() { return departmentName; }
        public String getParentDepartmentId() { return parentDepartmentId; }
    }

    public static class RoleOption {
        private final String roleCode;
        private final String roleName;

        public RoleOption(String roleCode, String roleName) {
            this.roleCode = roleCode;
            this.roleName = roleName;
        }

        public String getRoleCode() { return roleCode; }
        public String getRoleName() { return roleName; }
    }
}
