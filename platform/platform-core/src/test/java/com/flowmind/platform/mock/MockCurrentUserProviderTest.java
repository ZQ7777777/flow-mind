package com.flowmind.platform.mock;

import com.flowmind.platform.api.dto.UserContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockCurrentUserProviderTest {

    @Test
    void returnsDefaultMockUser() {
        MockCurrentUserProvider provider = new MockCurrentUserProvider();

        UserContext currentUser = provider.getCurrentUser();

        assertEquals("mock-user", currentUser.getUserId());
        assertEquals("Mock Department", currentUser.getDepartmentName());
    }

    @Test
    void returnsDefensiveCopy() {
        MockCurrentUserProvider provider = new MockCurrentUserProvider(
                new UserContext("user-1", "User One", "dept-1", "Department One"));

        UserContext currentUser = provider.getCurrentUser();
        currentUser.setUserId("changed");

        assertEquals("user-1", provider.getCurrentUser().getUserId());
    }

    @Test
    void canSwitchCurrentUser() {
        MockCurrentUserProvider provider = new MockCurrentUserProvider();

        provider.setCurrentUser(new UserContext("user-2", "User Two", "dept-2", "Department Two"));

        assertEquals("user-2", provider.getCurrentUser().getUserId());
        assertEquals("dept-2", provider.getCurrentUser().getDepartmentId());
    }

    @Test
    void rejectsMissingUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> new MockCurrentUserProvider(new UserContext(null, "User", "dept", "Department")));
    }
}
