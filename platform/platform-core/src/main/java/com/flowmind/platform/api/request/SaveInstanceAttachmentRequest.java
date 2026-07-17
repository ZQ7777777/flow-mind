package com.flowmind.platform.api.request;

/**
 * 保存实例级附件请求。
 */
public class SaveInstanceAttachmentRequest extends OperationRequest {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 上传人用户 ID。 */
    private String operatorUserId;
    /** 附件上传项。 */
    private AttachmentUploadItem attachment;

    public SaveInstanceAttachmentRequest() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public AttachmentUploadItem getAttachment() {
        return attachment;
    }

    public void setAttachment(AttachmentUploadItem attachment) {
        this.attachment = attachment;
    }
}
