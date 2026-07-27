package com.flowmind.platform.api.spi;

import com.flowmind.platform.api.dto.DepartmentDTO;
import com.flowmind.platform.api.dto.UserDTO;

import java.util.List;
import java.util.Optional;

/**
 * 组织架构 SPI。
 *
 * <p>该接口由宿主业务系统提供实现，平台只通过公共 DTO 查询用户、部门和角色关系。
 * 列表方法未找到数据时应返回空集合，不应返回 {@code null}；单对象查询未找到时返回
 * {@link Optional#empty()}。</p>
 *
 * @author FlowMind
 * @since 2026-07-24
 */
public interface OrganizationProvider {
    /**
     * 查询全部部门。
     *
     * @return 部门列表；无部门时返回空集合
     */
    List<DepartmentDTO> listDepartments();

    /**
     * 按部门查询用户。
     *
     * @param departmentId 部门 ID；不能为空白
     * @return 用户列表；部门不存在或部门下无用户时返回空集合
     */
    List<UserDTO> listUsersByDepartment(String departmentId);

    /**
     * 按角色查询用户。
     *
     * @param roleCode 角色编码；不能为空白
     * @return 用户列表；角色不存在或角色下无用户时返回空集合
     */
    List<UserDTO> listUsersByRole(String roleCode);

    /**
     * 按角色和部门查询用户。
     *
     * @param roleCode 角色编码；不能为空白
     * @param departmentId 部门 ID；不能为空白
     * @return 用户列表；角色、部门不存在或交集无用户时返回空集合
     */
    List<UserDTO> listUsersByRoleAndDepartment(String roleCode, String departmentId);

    /**
     * 查询单个用户。
     *
     * @param userId 用户 ID；不能为空白
     * @return 用户信息；用户不存在时返回 {@link Optional#empty()}
     */
    Optional<UserDTO> findUser(String userId);

    /**
     * 查询单个部门。
     *
     * @param departmentId 部门 ID；不能为空白
     * @return 部门信息；部门不存在时返回 {@link Optional#empty()}
     */
    Optional<DepartmentDTO> findDepartment(String departmentId);
}
