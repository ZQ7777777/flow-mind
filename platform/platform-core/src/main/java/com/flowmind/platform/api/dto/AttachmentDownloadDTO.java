package com.flowmind.platform.api.dto;

/**
 * 附件下载结果。
 */
public class AttachmentDownloadDTO {

    /** 附件元数据。 */
    private AttachmentDTO attachment;
    /** 文件二进制内容。 */
    private byte[] content;

    public AttachmentDownloadDTO() {
    }

    public AttachmentDTO getAttachment() {
        return attachment;
    }

    public void setAttachment(AttachmentDTO attachment) {
        this.attachment = attachment;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }
}
