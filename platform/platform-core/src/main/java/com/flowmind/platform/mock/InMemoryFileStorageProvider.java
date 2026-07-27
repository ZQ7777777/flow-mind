package com.flowmind.platform.mock;

import com.flowmind.platform.api.dto.FileContent;
import com.flowmind.platform.api.dto.StoredFile;
import com.flowmind.platform.api.request.StoreFileRequest;
import com.flowmind.platform.api.spi.FileStorageProvider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 仅供本地验收使用的内存文件存储。 */
public class InMemoryFileStorageProvider implements FileStorageProvider {
    private final Map<String, FileContent> files = new ConcurrentHashMap<String, FileContent>();
    @Override public StoredFile store(StoreFileRequest request) {
        String key = "mock://" + UUID.randomUUID().toString();
        FileContent content = new FileContent(key, request.getFileName(), request.getContentType(), request.getSizeBytes(), request.getContent());
        files.put(key, content); return new StoredFile(key, request.getFileName(), request.getContentType(), request.getSizeBytes());
    }
    @Override public FileContent load(String storageKey) { FileContent content = files.get(storageKey); if (content == null) throw new IllegalArgumentException("File not found: " + storageKey); return content; }
    @Override public void delete(String storageKey) { files.remove(storageKey); }
}
