package com.flowmind.business.security.local;

import com.flowmind.business.auth.SessionAuthentication;
import com.flowmind.business.organization.local.MockOrganizationRepository;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalBusinessUserProviderTest {

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void usesOnlyTheAuthenticatedSessionUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-FlowMind-Local-User", "u_admin_01");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SessionAuthentication authentication = mock(SessionAuthentication.class);
        when(authentication.currentUser(request)).thenReturn(new MockOrganizationRepository.UserRecord(
                "u_sales_01", "sales01", "hash", "张三", "dept_sales", "业务一部",
                "业务员", "USER", true));

        CurrentBusinessUserProvider.BusinessUser user =
                new LocalBusinessUserProvider(authentication).currentUser();

        assertEquals("u_sales_01", user.getUserId());
        assertEquals("dept_sales", user.getDepartmentId());
    }
}
