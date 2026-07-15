package com.flowmind.platform.api.entity.po;

public class AttachmentUploadItem {

    /** 附件模板编码，在实例绑定的附件配置范围内唯一。 */
    private String attachmentCode;
    /** 附件归属范围，用于区分实例级与任务级附件。 */
    private String ownerType;
    /** 调用方上传时提供的原始文件名。 */
    private String fileName;
    /** 文件的 MIME 内容类型。 */
    private String contentType;
    /** 文件内容的字节数。 */
    private Long sizeBytes;
    /** 上传文件的二进制内容；后续由文件存储 SPI 保存。 */
    private byte[] content;

    public AttachmentUploadItem() {
    }

    public String getAttachmentCode() {
        return attachmentCode;
    }

    public void setAttachmentCode(String attachmentCode) {
        this.attachmentCode = attachmentCode;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }
}
