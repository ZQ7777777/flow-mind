package com.flowmind.platform.mock;

import com.flowmind.platform.api.dto.FileContent;
import com.flowmind.platform.api.dto.StoredFile;
import com.flowmind.platform.api.request.StoreFileRequest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryFileStorageProviderTest {

    @Test
    void storesAndLoadsDefensiveContentCopies() {
        InMemoryFileStorageProvider provider = new InMemoryFileStorageProvider();
        byte[] original = new byte[] {1, 2, 3};

        StoredFile stored = provider.store(new StoreFileRequest("op-1", "receipt.pdf",
                "application/pdf", Long.valueOf(3L), original));
        original[0] = 9;

        FileContent firstLoad = provider.load(stored.getStorageKey());
        assertArrayEquals(new byte[] {1, 2, 3}, firstLoad.getContent());

        firstLoad.getContent()[1] = 8;
        FileContent secondLoad = provider.load(stored.getStorageKey());
        assertArrayEquals(new byte[] {1, 2, 3}, secondLoad.getContent());
    }

    @Test
    void deleteIsIdempotentAndLoadMissingFailsClearly() {
        InMemoryFileStorageProvider provider = new InMemoryFileStorageProvider();
        StoredFile stored = provider.store(new StoreFileRequest("op-1", "receipt.pdf",
                "application/pdf", Long.valueOf(1L), new byte[] {1}));

        provider.delete(stored.getStorageKey());
        provider.delete(stored.getStorageKey());

        assertThrows(IllegalArgumentException.class, () -> provider.load(stored.getStorageKey()));
    }

    @Test
    void concurrentStoresUseDifferentStorageKeys() throws Exception {
        final InMemoryFileStorageProvider provider = new InMemoryFileStorageProvider();
        final int count = 16;
        final Set<String> keys = Collections.synchronizedSet(new HashSet<String>());
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            final int index = i;
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        StoredFile stored = provider.store(new StoreFileRequest("op-" + index,
                                "file-" + index + ".txt", "text/plain", Long.valueOf(1L), new byte[] {(byte) index}));
                        keys.add(stored.getStorageKey());
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();
        assertEquals(count, keys.size());
    }
}

