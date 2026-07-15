package com.flowmind.platform.api.entity.dto;

public class DepartmentDTO {

    /** 部门 ID。 */
    private String departmentId;
    /** 部门名称。 */
    private String departmentName;
    /** 父级部门 ID；顶级部门可为空。 */
    private String parentDepartmentId;

    public DepartmentDTO() {
    }

    public DepartmentDTO(String departmentId, String departmentName, String parentDepartmentId) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.parentDepartmentId = parentDepartmentId;
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

    public String getParentDepartmentId() {
        return parentDepartmentId;
    }

    public void setParentDepartmentId(String parentDepartmentId) {
        this.parentDepartmentId = parentDepartmentId;
    }
}
