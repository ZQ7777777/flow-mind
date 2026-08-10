package com.flowmind.business.security;

import com.flowmind.platform.api.dto.DepartmentDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.api.spi.OrganizationProvider;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

/**
 * 将业务可信身份适配为平台当前用户 SPI。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
public class PlatformCurrentUserAdapter implements CurrentUserProvider {

    private final CurrentBusinessUserProvider businessUserProvider;
    private final ObjectProvider<OrganizationProvider> organizationProvider;

    public PlatformCurrentUserAdapter(CurrentBusinessUserProvider businessUserProvider,
                                      ObjectProvider<OrganizationProvider> organizationProvider) {
        this.businessUserProvider = businessUserProvider;
        this.organizationProvider = organizationProvider;
    }

    @Override
    public UserContext getCurrentUser() {
        CurrentBusinessUserProvider.BusinessUser businessUser = businessUserProvider.currentUser();
        String userName = businessUser.getUserId();
        String departmentName = businessUser.getDepartmentId();
        OrganizationProvider directory = organizationProvider.getIfAvailable();
        if (directory != null) {
            userName = resolveUserName(directory, businessUser.getUserId(), userName);
            departmentName = resolveDepartmentName(directory, businessUser.getDepartmentId(), departmentName);
        }
        return new UserContext(businessUser.getUserId(), userName,
                businessUser.getDepartmentId(), departmentName);
    }

    private String resolveUserName(OrganizationProvider directory, String userId, String fallback) {
        try {
            Optional<UserDTO> user = directory.findUser(userId);
            return user.isPresent() && hasText(user.get().getUserName()) ? user.get().getUserName() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String resolveDepartmentName(OrganizationProvider directory, String departmentId, String fallback) {
        if (!hasText(departmentId)) {
            return null;
        }
        try {
            Optional<DepartmentDTO> department = directory.findDepartment(departmentId);
            return department.isPresent() && hasText(department.get().getDepartmentName())
                    ? department.get().getDepartmentName() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
