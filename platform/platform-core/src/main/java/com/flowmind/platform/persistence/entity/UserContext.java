package com.flowmind.platform.persistence.entity;

public class UserContext {

    /** 当前用户 ID。 */
    private String userId;
    /** 当前用户名称。 */
    private String userName;
    /** 当前用户所属部门 ID。 */
    private String departmentId;
    /** 当前用户所属部门名称。 */
    private String departmentName;

    public UserContext() {
    }

    public UserContext(String userId, String userName, String departmentId, String departmentName) {
        this.userId = userId;
        this.userName = userName;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
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
}
