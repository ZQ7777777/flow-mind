package com.flowmind.platform.api.request;

import com.flowmind.platform.persistence.entity.AttachmentUploadItem;

/**
 * 保存任务级附件请求。
 */
public class SaveTaskAttachmentRequest extends TaskOperationRequest {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 附件上传项。 */
    private AttachmentUploadItem attachment;

    public SaveTaskAttachmentRequest() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public AttachmentUploadItem getAttachment() {
        return attachment;
    }

    public void setAttachment(AttachmentUploadItem attachment) {
        this.attachment = attachment;
    }
}
