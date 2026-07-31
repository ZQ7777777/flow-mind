package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.UserContext;

/**
 * Supplies a trusted operator for platform-owned background work on the current thread.
 */
public final class SystemOperatorContext {

    private static final ThreadLocal<UserContext> CURRENT = new ThreadLocal<UserContext>();

    private SystemOperatorContext() {
    }

    public static UserContext current() {
        return copy(CURRENT.get());
    }

    public static void runAs(UserContext operator, Runnable work) {
        if (operator == null || isBlank(operator.getUserId())) {
            throw new IllegalArgumentException("operator.userId is required");
        }
        if (work == null) {
            throw new IllegalArgumentException("work is required");
        }
        UserContext previous = CURRENT.get();
        CURRENT.set(copy(operator));
        try {
            work.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    private static UserContext copy(UserContext source) {
        if (source == null) {
            return null;
        }
        return new UserContext(source.getUserId(), source.getUserName(),
                source.getDepartmentId(), source.getDepartmentName());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
