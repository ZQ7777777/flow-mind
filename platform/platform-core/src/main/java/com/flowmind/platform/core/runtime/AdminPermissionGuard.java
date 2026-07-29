package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.UserContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Minimal admin permission boundary for M6 operational repair actions. */
@Component
public class AdminPermissionGuard {

    private final Set<String> adminUserIds;

    public AdminPermissionGuard() {
        this(new LinkedHashSet<String>(Arrays.asList("admin", "operator", "system_timeout")));
    }

    public AdminPermissionGuard(Set<String> adminUserIds) {
        this.adminUserIds = adminUserIds == null
                ? new LinkedHashSet<String>()
                : new LinkedHashSet<String>(adminUserIds);
    }

    public void assertAdmin(UserContext operator) {
        if (operator == null) {
            throw denied();
        }
        assertAdminUserId(operator.getUserId());
    }

    public void assertAdminUserId(String operatorUserId) {
        if (operatorUserId == null || operatorUserId.trim().isEmpty()
                || !adminUserIds.contains(operatorUserId.trim())) {
            throw denied();
        }
    }

    private RuntimeValidationException denied() {
        return new RuntimeValidationException(RuntimeErrorCodes.ADMIN_PERMISSION_DENIED,
                "administrator permission is required");
    }
}
