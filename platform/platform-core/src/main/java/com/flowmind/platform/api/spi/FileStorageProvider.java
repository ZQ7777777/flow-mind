package com.flowmind.platform.api.spi;

import com.flowmind.platform.api.dto.FileContent;
import com.flowmind.platform.api.dto.StoredFile;
import com.flowmind.platform.api.request.StoreFileRequest;

/**
 * 文件存储 SPI。
 */
public interface FileStorageProvider {
    /**
     * 存储文件内容并返回存储结果。
     *
     * @param request 文件存储请求
     * @return 存储结果
     */
    StoredFile store(StoreFileRequest request);

    /**
     * 读取已存储文件内容。
     *
     * @param storageKey 存储键
     * @return 文件内容
     */
    FileContent load(String storageKey);

    /**
     * 删除已存储文件。
     *
     * @param storageKey 存储键
     */
    void delete(String storageKey);
}
