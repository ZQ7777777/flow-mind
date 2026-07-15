package com.flowmind.platform.api.spi;

import com.flowmind.platform.api.dto.DepartmentDTO;
import com.flowmind.platform.api.dto.UserDTO;

import java.util.List;
import java.util.Optional;

/**
 * 组织架构 SPI。
 */
public interface OrganizationProvider {
    /**
     * 查询全部部门。
     *
     * @return 部门列表
     */
    List<DepartmentDTO> listDepartments();

    /**
     * 按部门查询用户。
     *
     * @param departmentId 部门 ID
     * @return 用户列表
     */
    List<UserDTO> listUsersByDepartment(String departmentId);

    /**
     * 按角色查询用户。
     *
     * @param roleCode 角色编码
     * @return 用户列表
     */
    List<UserDTO> listUsersByRole(String roleCode);

    /**
     * 按角色和部门查询用户。
     *
     * @param roleCode 角色编码
     * @param departmentId 部门 ID
     * @return 用户列表
     */
    List<UserDTO> listUsersByRoleAndDepartment(String roleCode, String departmentId);

    /**
     * 查询单个用户。
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    Optional<UserDTO> findUser(String userId);

    /**
     * 查询单个部门。
     *
     * @param departmentId 部门 ID
     * @return 部门信息
     */
    Optional<DepartmentDTO> findDepartment(String departmentId);
}
