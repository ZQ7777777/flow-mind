package com.flowmind.platform.mock;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.spi.CurrentUserProvider;

/**
 * Fixed current-user provider for local verification and tests.
 */
public class MockCurrentUserProvider implements CurrentUserProvider {

    private volatile UserContext currentUser;

    public MockCurrentUserProvider() {
        this(new UserContext("u_sales_01", "业务员", "mock-dept", "Mock Department"));
    }

    public MockCurrentUserProvider(UserContext currentUser) {
        setCurrentUser(currentUser);
    }

    @Override
    public UserContext getCurrentUser() {
        return copy(currentUser);
    }

    public void setCurrentUser(UserContext currentUser) {
        if (currentUser == null || isBlank(currentUser.getUserId())) {
            throw new IllegalArgumentException("currentUser.userId is required");
        }
        this.currentUser = copy(currentUser);
    }

    private static UserContext copy(UserContext source) {
        return new UserContext(source.getUserId(), source.getUserName(),
                source.getDepartmentId(), source.getDepartmentName());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
