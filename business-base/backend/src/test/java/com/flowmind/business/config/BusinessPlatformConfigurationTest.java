package com.flowmind.business.config;

import com.flowmind.business.security.BusinessAuthorizationProvider;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.core.runtime.AdminPermissionGuard;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessPlatformConfigurationTest {

    @Test
    void adminPermissionGuardUsesBusinessAuthorizationProvider() {
        BusinessPlatformConfiguration configuration = new BusinessPlatformConfiguration();
        BusinessAuthorizationProvider authorizationProvider = userId -> "u_admin_01".equals(userId);
        AdminPermissionGuard guard = configuration.adminPermissionGuard(authorizationProvider);

        assertThatCode(() -> guard.assertAdmin(new UserContext("u_admin_01", "Admin", "dept-1", "Dept")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.assertAdmin(new UserContext("u_sales_01", "Sales", "dept-1", "Dept")))
                .isInstanceOf(RuntimeValidationException.class)
                .hasMessage("administrator permission is required");
    }
}
