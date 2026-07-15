package com.flowmind.platform.api.entity.request;

/**
 * 删除附件请求。
 */
public class DeleteAttachmentRequest extends OperationRequest {

    /** 附件 ID。 */
    private String attachmentId;
    /** 删除人用户 ID。 */
    private String operatorUserId;

    public DeleteAttachmentRequest() {
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
