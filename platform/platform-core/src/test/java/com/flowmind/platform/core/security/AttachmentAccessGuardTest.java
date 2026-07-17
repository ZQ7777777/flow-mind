package com.flowmind.platform.core.security;

import com.flowmind.platform.api.request.AttachmentAccessRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentAccessGuardTest {

    private final AttachmentAccessRequest request = new AttachmentAccessRequest();

    @Test
    void missingProviderDeniesAccess() {
        AttachmentAccessGuard guard = new AttachmentAccessGuard(null);

        assertFalse(guard.isAllowed(request));
    }

    @Test
    void providerRejectionDeniesAccess() {
        AttachmentAccessGuard guard = new AttachmentAccessGuard(accessRequest -> false);

        assertFalse(guard.isAllowed(request));
    }

    @Test
    void providerExceptionDeniesAccess() {
        AttachmentAccessGuard guard = new AttachmentAccessGuard(accessRequest -> {
            throw new IllegalStateException("authorization unavailable");
        });

        assertFalse(guard.isAllowed(request));
    }

    @Test
    void explicitProviderApprovalAllowsAccess() {
        AttachmentAccessGuard guard = new AttachmentAccessGuard(accessRequest -> true);

        assertTrue(guard.isAllowed(request));
    }
}
