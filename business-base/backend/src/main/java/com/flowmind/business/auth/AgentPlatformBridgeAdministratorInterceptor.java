package com.flowmind.business.auth;

import com.flowmind.business.organization.local.MockOrganizationRepository;
import com.flowmind.business.security.BusinessAccessDeniedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 限制 agent-web 平台桥接写入只能由已登录的 business-base 管理员执行。
 */
@Component
@Profile({"local", "test"})
public class AgentPlatformBridgeAdministratorInterceptor implements HandlerInterceptor {

    private final SessionAuthentication authentication;

    public AgentPlatformBridgeAdministratorInterceptor(SessionAuthentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        MockOrganizationRepository.UserRecord user = authentication.currentUser(request);
        if (!"ADMIN".equals(user.getUserType())) {
            throw new BusinessAccessDeniedException("仅 agent-web 管理员桥接接口可以写入 business-base 内嵌平台库");
        }
        return true;
    }
}
