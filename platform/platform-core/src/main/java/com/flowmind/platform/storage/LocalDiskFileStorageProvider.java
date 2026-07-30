package com.flowmind.platform.storage;

import com.flowmind.platform.api.dto.FileContent;
import com.flowmind.platform.api.dto.StoredFile;
import com.flowmind.platform.api.request.StoreFileRequest;
import com.flowmind.platform.api.spi.FileStorageProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Local disk file storage provider for standalone demos.
 */
public class LocalDiskFileStorageProvider implements FileStorageProvider {

    private static final String SCHEME = "local-disk://";

    private final Path rootDirectory;

    public LocalDiskFileStorageProvider(String rootDirectory) {
        if (isBlank(rootDirectory)) {
            throw new IllegalArgumentException("rootDirectory is required");
        }
        this.rootDirectory = Paths.get(rootDirectory).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(StoreFileRequest request) {
        validateRequest(request);
        String fileName = safeFileName(request.getFileName());
        String relativeKey = relativeKey(request.getOperationId(), fileName);
        Path target = resolveStoragePath(SCHEME + relativeKey);
        Path parent = target.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = Files.createTempFile(parent == null ? rootDirectory : parent, "upload-", ".tmp");
            Files.write(temporary, request.getContent());
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to store file on local disk", ex);
        }
        return new StoredFile(SCHEME + relativeKey, request.getFileName(), contentType(request), request.getSizeBytes());
    }

    @Override
    public FileContent load(String storageKey) {
        Path path = resolveStoragePath(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("File not found: " + storageKey);
        }
        try {
            byte[] content = Files.readAllBytes(path);
            return new FileContent(storageKey, path.getFileName().toString(),
                    probeContentType(path), Long.valueOf(content.length), content);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load file from local disk", ex);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path path = resolveStoragePath(storageKey);
        try {
            Files.deleteIfExists(path);
            pruneEmptyDirectories(path.getParent());
        } catch (IOException ex) {
            throw new IllegalStateException("failed to delete file from local disk", ex);
        }
    }

    public Path getRootDirectory() {
        return rootDirectory;
    }

    private void validateRequest(StoreFileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("store request is required");
        }
        if (isBlank(request.getFileName())) {
            throw new IllegalArgumentException("fileName is required");
        }
        if (request.getContent() == null) {
            throw new IllegalArgumentException("content is required");
        }
        if (request.getSizeBytes() == null || request.getSizeBytes().longValue() != request.getContent().length) {
            throw new IllegalArgumentException("content and sizeBytes do not match");
        }
    }

    private Path resolveStoragePath(String storageKey) {
        if (isBlank(storageKey) || !storageKey.startsWith(SCHEME)) {
            throw new IllegalArgumentException("storageKey is not a local disk key");
        }
        String relativeKey = storageKey.substring(SCHEME.length()).replace('\\', '/');
        if (!relativeKey.matches("[A-Za-z0-9._/-]+")) {
            throw new IllegalArgumentException("storageKey contains invalid characters");
        }
        Path path = rootDirectory.resolve(relativeKey).normalize();
        if (!path.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("storageKey escapes root directory");
        }
        return path;
    }

    private String relativeKey(String operationId, String fileName) {
        LocalDate today = LocalDate.now();
        return today.getYear() + "/" + twoDigits(today.getMonthValue()) + "/" + twoDigits(today.getDayOfMonth())
                + "/" + safeToken(operationId) + "-" + UUID.randomUUID().toString() + "-" + fileName;
    }

    private String safeFileName(String fileName) {
        String normalized = fileName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        String safe = name.replaceAll("[^A-Za-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return isBlank(safe) ? "attachment.bin" : safe;
    }

    private String safeToken(String value) {
        String safe = isBlank(value) ? "op" : value.replaceAll("[^A-Za-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return isBlank(safe) ? "op" : safe;
    }

    private String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private String contentType(StoreFileRequest request) {
        return isBlank(request.getContentType()) ? "application/octet-stream" : request.getContentType();
    }

    private String probeContentType(Path path) {
        try {
            String value = Files.probeContentType(path);
            return isBlank(value) ? "application/octet-stream" : value;
        } catch (IOException ex) {
            return "application/octet-stream";
        }
    }

    private void pruneEmptyDirectories(Path start) throws IOException {
        Path current = start;
        while (current != null && current.startsWith(rootDirectory) && !current.equals(rootDirectory)) {
            try {
                Files.delete(current);
            } catch (IOException ex) {
                return;
            }
            current = current.getParent();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
