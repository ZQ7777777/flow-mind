package com.flowmind.business.auth;

import javax.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() { }

    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class UserResponse {
        private String userId;
        private String username;
        private String realName;
        private String departmentId;
        private String departmentName;
        private String userType;
        private boolean administrator;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getRealName() { return realName; }
        public void setRealName(String realName) { this.realName = realName; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public String getDepartmentName() { return departmentName; }
        public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
        public String getUserType() { return userType; }
        public void setUserType(String userType) { this.userType = userType; }
        public boolean isAdministrator() { return administrator; }
        public void setAdministrator(boolean administrator) { this.administrator = administrator; }
    }
}
