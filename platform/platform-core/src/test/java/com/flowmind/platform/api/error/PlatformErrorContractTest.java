package com.flowmind.platform.api.error;

import com.flowmind.platform.core.runtime.RuntimeStateException;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformErrorContractTest {

    @Test
    void publicClassifierFreezesSecurityAndConflictCategories() {
        assertEquals(PlatformErrorCategory.FORBIDDEN, PlatformErrorCategories.resolve(
                PlatformErrorCodes.TASK_PERMISSION_DENIED, PlatformErrorCategory.INVALID_REQUEST));
        assertEquals(PlatformErrorCategory.NOT_FOUND, PlatformErrorCategories.resolve(
                PlatformErrorCodes.INSTANCE_NOT_FOUND, PlatformErrorCategory.CONFLICT));
        assertEquals(PlatformErrorCategory.CONFLICT, PlatformErrorCategories.resolve(
                PlatformErrorCodes.TASK_CONCURRENT_MODIFIED, PlatformErrorCategory.INVALID_REQUEST));
        assertEquals(PlatformErrorCategory.DEPENDENCY_FAILURE, PlatformErrorCategories.resolve(
                PlatformErrorCodes.ATTACHMENT_STORAGE_FAILED, PlatformErrorCategory.CONFLICT));
    }

    @Test
    void existingCoreExceptionsExposePublicErrorContract() {
        RuntimeValidationException forbidden = new RuntimeValidationException(
                PlatformErrorCodes.TASK_PERMISSION_DENIED, "denied");
        RuntimeStateException missing = new RuntimeStateException(
                PlatformErrorCodes.TASK_NOT_FOUND, "missing");

        assertTrue(forbidden instanceof PlatformError);
        assertEquals(PlatformErrorCategory.FORBIDDEN, forbidden.getErrorCategory());
        assertEquals(PlatformErrorCategory.NOT_FOUND, missing.getErrorCategory());
    }
}
