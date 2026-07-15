package com.flowmind.platform.api.entity.po;

public class StoredFile {

    /** 文件存储键。 */
    private String storageKey;
    /** 原始文件名。 */
    private String fileName;
    /** 文件 MIME 内容类型。 */
    private String contentType;
    /** 文件大小，单位字节。 */
    private Long sizeBytes;

    public StoredFile() {
    }

    public StoredFile(String storageKey, String fileName, String contentType, Long sizeBytes) {
        this.storageKey = storageKey;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
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
}
