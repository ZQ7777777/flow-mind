package com.flowmind.business.auth;

import com.flowmind.business.organization.local.MockOrganizationRepository;
import com.flowmind.business.security.BusinessAuthenticationException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Optional;

@Component
@Profile({"local", "test"})
public class SessionAuthentication {

    static final String USER_ID_SESSION_KEY = "FLOW_MIND_AUTHENTICATED_USER_ID";
    private final MockOrganizationRepository repository;

    public SessionAuthentication(MockOrganizationRepository repository) {
        this.repository = repository;
    }

    public void establish(HttpServletRequest request, String userId) {
        HttpSession existing = request.getSession(false);
        if (existing != null) existing.invalidate();
        request.getSession(true).setAttribute(USER_ID_SESSION_KEY, userId);
    }

    public MockOrganizationRepository.UserRecord currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(USER_ID_SESSION_KEY);
        if (!(userId instanceof String) || ((String) userId).trim().isEmpty()) {
            throw new BusinessAuthenticationException("请先登录" );
        }
        Optional<MockOrganizationRepository.UserRecord> user = repository.findActiveUserById((String) userId);
        if (!user.isPresent()) {
            session.invalidate();
            throw new BusinessAuthenticationException("登录已失效，请重新登录");
        }
        return user.get();
    }

    public void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}
