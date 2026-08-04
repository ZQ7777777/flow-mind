package com.flowmind.business.security;

import java.util.Objects;

/**
 * Trusted server-side accessor for the authenticated business user.
 * Generated business code must obtain identity here instead of accepting it from the client.
 */
public interface CurrentBusinessUserProvider {

    BusinessUser currentUser();

    final class BusinessUser {
        private final String userId;
        private final String departmentId;

        public BusinessUser(String userId, String departmentId) {
            this.userId = Objects.requireNonNull(userId, "userId");
            this.departmentId = departmentId;
        }

        public String getUserId() {
            return userId;
        }

        public String getDepartmentId() {
            return departmentId;
        }
    }
}
