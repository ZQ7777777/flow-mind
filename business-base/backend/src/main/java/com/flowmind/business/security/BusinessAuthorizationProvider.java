package com.flowmind.business.security;

/**
 * 宿主业务角色授权扩展点。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
public interface BusinessAuthorizationProvider {

    /**
     * 判断可信用户是否拥有流程管理员权限。
     *
     * @param userId 可信用户 ID
     * @return 是否为管理员
     */
    boolean isAdministrator(String userId);
}
