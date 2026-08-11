package com.flowmind.business.security.local;

import com.flowmind.business.auth.SessionAuthentication;
import com.flowmind.business.organization.local.MockOrganizationRepository;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 仅供 local/test 使用的白名单业务用户实现。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
@Component
@Profile({"local", "test"})
public class LocalBusinessUserProvider implements CurrentBusinessUserProvider {

    private final SessionAuthentication authentication;

    public LocalBusinessUserProvider(SessionAuthentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public BusinessUser currentUser() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        MockOrganizationRepository.UserRecord user = authentication.currentUser(request);
        return new BusinessUser(user.getId(), user.getDepartmentId());
    }
}
