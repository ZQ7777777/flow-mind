package com.flowmind.platform.mock;

import com.flowmind.platform.api.request.AttachmentAccessRequest;
import com.flowmind.platform.api.spi.AttachmentAccessProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configurable attachment access provider for local verification and tests.
 */
public class MockAttachmentAccessProvider implements AttachmentAccessProvider {

    private final List<AttachmentAccessRequest> requests =
            Collections.synchronizedList(new ArrayList<AttachmentAccessRequest>());

    private volatile boolean allowed;

    public MockAttachmentAccessProvider() {
        this(false);
    }

    public MockAttachmentAccessProvider(boolean allowed) {
        this.allowed = allowed;
    }

    @Override
    public boolean isAllowed(AttachmentAccessRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        requests.add(copy(request));
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public List<AttachmentAccessRequest> getRequests() {
        synchronized (requests) {
            List<AttachmentAccessRequest> snapshot = new ArrayList<AttachmentAccessRequest>();
            for (AttachmentAccessRequest request : requests) {
                snapshot.add(copy(request));
            }
            return Collections.unmodifiableList(snapshot);
        }
    }

    public void clearRequests() {
        requests.clear();
    }

    private static AttachmentAccessRequest copy(AttachmentAccessRequest source) {
        return new AttachmentAccessRequest(source.getUserId(), source.getUserName(), source.getAccessAction(),
                source.getInstanceId(), source.getTaskId(), source.getAttachmentId(), source.getOwnerType());
    }
}
