package com.flowmind.platform.storage;

import com.flowmind.platform.api.dto.FileContent;
import com.flowmind.platform.api.dto.StoredFile;
import com.flowmind.platform.api.request.StoreFileRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDiskFileStorageProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void storesLoadsAndDeletesFileOnDisk() {
        LocalDiskFileStorageProvider provider = new LocalDiskFileStorageProvider(tempDir.toString());
        byte[] content = new byte[] {1, 2, 3};

        StoredFile stored = provider.store(new StoreFileRequest("op-1", "receipt.pdf",
                "application/pdf", Long.valueOf(3L), content));

        assertTrue(stored.getStorageKey().startsWith("local-disk://"));
        assertTrue(Files.exists(resolveStoredPath(stored.getStorageKey())));

        FileContent loaded = provider.load(stored.getStorageKey());
        assertArrayEquals(content, loaded.getContent());
        assertEquals(Long.valueOf(3L), loaded.getSizeBytes());

        provider.delete(stored.getStorageKey());
        assertFalse(Files.exists(resolveStoredPath(stored.getStorageKey())));
        assertThrows(IllegalArgumentException.class, () -> provider.load(stored.getStorageKey()));
    }

    @Test
    void rejectsStorageKeysOutsideRootDirectory() {
        LocalDiskFileStorageProvider provider = new LocalDiskFileStorageProvider(tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> provider.load("mock://file"));
        assertThrows(IllegalArgumentException.class, () -> provider.load("local-disk://../outside.txt"));
    }

    @Test
    void sanitizesUnsafeFileNameBeforeWriting() {
        LocalDiskFileStorageProvider provider = new LocalDiskFileStorageProvider(tempDir.toString());

        StoredFile stored = provider.store(new StoreFileRequest("op/unsafe", "..\\contract?.pdf",
                "application/pdf", Long.valueOf(1L), new byte[] {7}));

        assertTrue(stored.getStorageKey().endsWith("contract_.pdf"));
        assertTrue(Files.exists(resolveStoredPath(stored.getStorageKey())));
    }

    private Path resolveStoredPath(String storageKey) {
        return tempDir.resolve(storageKey.substring("local-disk://".length())).normalize();
    }
}
