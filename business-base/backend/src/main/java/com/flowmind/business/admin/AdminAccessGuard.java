package com.flowmind.business.admin;

import com.flowmind.business.security.BusinessAccessDeniedException;
import com.flowmind.business.security.BusinessAuthorizationProvider;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import org.springframework.stereotype.Component;

/** Ensures that management endpoints are only used by a trusted business administrator. */
@Component
public class AdminAccessGuard {

    private final CurrentBusinessUserProvider currentUserProvider;
    private final BusinessAuthorizationProvider authorizationProvider;

    public AdminAccessGuard(CurrentBusinessUserProvider currentUserProvider,
                            BusinessAuthorizationProvider authorizationProvider) {
        this.currentUserProvider = currentUserProvider;
        this.authorizationProvider = authorizationProvider;
    }

    /** Returns the trusted administrator user ID or rejects the request. */
    public String requireAdministrator() {
        String userId = currentUserProvider.currentUser().getUserId();
        if (!authorizationProvider.isAdministrator(userId)) {
            throw new BusinessAccessDeniedException("仅流程管理员可以访问管理控制台");
        }
        return userId;
    }
}
