package com.dineflow.storage;

import com.dineflow.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "dineflow.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalBinaryObjectStorage implements BinaryObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalBinaryObjectStorage.class);
    private final Path rootPath;

    public LocalBinaryObjectStorage(StorageProperties properties) {
        this.rootPath = Paths.get(properties.getLocalRootPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            log.error("Failed to initialize local storage root: {}", rootPath, e);
        }
    }

    @Override
    public void putObject(String objectKey, byte[] content, String contentType) {
        Path target = resolveSafe(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new BusinessException("Failed to write object to local storage", HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_WRITE_ERROR");
        }
    }

    @Override
    public Optional<byte[]> getObject(String objectKey) {
        Path target = resolveSafe(objectKey);
        if (!Files.exists(target) || !Files.isReadable(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException e) {
            log.error("Failed to read local object: {}", objectKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        Path target = resolveSafe(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete local object {}: {}", objectKey, e.getMessage());
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(resolveSafe(objectKey));
    }

    @Override
    public String getStorageBackendName() {
        return "LOCAL_FILE";
    }

    private Path resolveSafe(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..")) {
            throw new BusinessException("Invalid storage object key", HttpStatus.BAD_REQUEST, "PATH_TRAVERSAL_DETECTED");
        }
        Path resolved = rootPath.resolve(objectKey).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new BusinessException("Storage path traversal detected", HttpStatus.BAD_REQUEST, "PATH_TRAVERSAL_DETECTED");
        }
        return resolved;
    }
}
