package com.flowmind.platform.api.request;

import java.util.List;
import java.util.Map;

public class SubmitTaskRequest extends TaskOperationRequest {

    /** 本次办理要写入或更新的流程变量。 */
    private Map<String, Object> variables;
    /** 本次节点办理时一并上传的附件。 */
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
