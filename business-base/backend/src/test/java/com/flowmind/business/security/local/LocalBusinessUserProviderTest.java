package com.flowmind.business.security.local;

import com.flowmind.business.config.BusinessBaseProperties;
import com.flowmind.business.security.BusinessAuthenticationException;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalBusinessUserProviderTest {

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void usesConfiguredDefaultOutsideHttpRequest() {
        LocalBusinessUserProvider provider = provider();

        CurrentBusinessUserProvider.BusinessUser user = provider.currentUser();

        assertEquals("u_sales_01", user.getUserId());
        assertEquals("dept_sales", user.getDepartmentId());
    }

    @Test
    void acceptsOnlyWhitelistedHeaderUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-FlowMind-Local-User", "u_finance_01");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        CurrentBusinessUserProvider.BusinessUser user = provider().currentUser();

        assertEquals("u_finance_01", user.getUserId());
        assertEquals("dept_finance", user.getDepartmentId());
    }

    @Test
    void rejectsUnknownHeaderUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-FlowMind-Local-User", "attacker");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThrows(BusinessAuthenticationException.class, () -> provider().currentUser());
    }

    private LocalBusinessUserProvider provider() {
        BusinessBaseProperties properties = new BusinessBaseProperties();
        Map<String, String> users = new LinkedHashMap<String, String>();
        users.put("u_sales_01", "dept_sales");
        users.put("u_finance_01", "dept_finance");
        properties.getLocalUser().setUsers(users);
        return new LocalBusinessUserProvider(properties);
    }
}
