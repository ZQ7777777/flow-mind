package com.flowmind.platform.api.request;

/**
 * 下载附件请求。
 */
public class DownloadAttachmentRequest {

    /** 附件 ID。 */
    private String attachmentId;
    /** 下载人用户 ID。 */
    private String operatorUserId;

    public DownloadAttachmentRequest() {
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
