package com.flowmind.platform.api.request;

import java.util.List;
import java.util.Map;

public class SubmitTaskRequest extends TaskOperationRequest {

    private Map<String, Object> variables;
    private List<AttachmentUploadItem> attachments;

    public SubmitTaskRequest() {
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public List<AttachmentUploadItem> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentUploadItem> attachments) {
        this.attachments = attachments;
    }
}
