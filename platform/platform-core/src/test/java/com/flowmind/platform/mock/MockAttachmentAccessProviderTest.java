package com.flowmind.platform.mock;

import com.flowmind.platform.api.enums.AttachmentAccessActionEnum;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.request.AttachmentAccessRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockAttachmentAccessProviderTest {

    @Test
    void deniesByDefaultAndRecordsRequest() {
        MockAttachmentAccessProvider provider = new MockAttachmentAccessProvider();

        boolean allowed = provider.isAllowed(request("user-1", "attachment-1"));

        assertFalse(allowed);
        assertEquals(1, provider.getRequests().size());
        assertEquals("user-1", provider.getRequests().get(0).getUserId());
    }

    @Test
    void canSwitchToAllowAllMode() {
        MockAttachmentAccessProvider provider = new MockAttachmentAccessProvider();

        provider.setAllowed(true);

        assertTrue(provider.isAllowed(request("user-2", "attachment-2")));
    }

    @Test
    void returnsDefensiveRequestSnapshot() {
        MockAttachmentAccessProvider provider = new MockAttachmentAccessProvider(true);
        AttachmentAccessRequest request = request("user-3", "attachment-3");

        provider.isAllowed(request);
        request.setUserId("changed");
        List<AttachmentAccessRequest> requests = provider.getRequests();
        requests.get(0).setUserId("snapshot-changed");

        assertEquals("user-3", provider.getRequests().get(0).getUserId());
    }

    @Test
    void canClearRecordedRequests() {
        MockAttachmentAccessProvider provider = new MockAttachmentAccessProvider(true);
        provider.isAllowed(request("user-4", "attachment-4"));

        provider.clearRequests();

        assertTrue(provider.getRequests().isEmpty());
    }

    @Test
    void rejectsNullRequest() {
        MockAttachmentAccessProvider provider = new MockAttachmentAccessProvider();

        assertThrows(IllegalArgumentException.class, () -> provider.isAllowed(null));
    }

    private static AttachmentAccessRequest request(String userId, String attachmentId) {
        return new AttachmentAccessRequest(userId, "User", AttachmentAccessActionEnum.DOWNLOAD,
                "instance-1", "task-1", attachmentId, AttachmentOwnerTypeEnum.TASK);
    }
}
