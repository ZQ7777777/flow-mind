package com.flowmind.platform.api.dto;

import java.util.List;

public class UserDTO {

    /** 用户 ID。 */
    private String userId;
    /** 用户名称。 */
    private String userName;
    /** 用户所属部门 ID；跨部门或无部门用户可为空。 */
    private String departmentId;
    /** 用户所属部门名称；用于展示和审计，允许为空。 */
    private String departmentName;
    /** 用户角色编码列表，如 finance、manager；无角色时为空集合或 null。 */
    private List<String> roleCodes;
    /** 用户有效状态；true 表示有效，false 表示不可作为审批人，null 表示宿主系统未提供该字段。 */
    private Boolean active;

    public UserDTO() {
    }

    public UserDTO(String userId, String userName) {
        this.userId = userId;
        this.userName = userName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public List<String> getRoleCodes() {
        return roleCodes;
    }

    public void setRoleCodes(List<String> roleCodes) {
        this.roleCodes = roleCodes;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
