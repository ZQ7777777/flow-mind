package com.flowmind.platform.api.request;

public class StoreFileRequest {

    /** 本次文件存储对应的操作幂等号。 */
    private String operationId;
    /** 原始文件名。 */
    private String fileName;
    /** 文件 MIME 内容类型。 */
    private String contentType;
    /** 文件大小，单位字节。 */
    private Long sizeBytes;
    /** 文件二进制内容。 */
    private byte[] content;

    public StoreFileRequest() {
    }

    public StoreFileRequest(String operationId, String fileName, String contentType, Long sizeBytes, byte[] content) {
        this.operationId = operationId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.content = content;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
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
