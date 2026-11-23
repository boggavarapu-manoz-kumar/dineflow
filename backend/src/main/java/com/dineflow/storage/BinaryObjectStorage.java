package com.dineflow.storage;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Storage-agnostic binary object provider SPI supporting local disk (dev/test)
 * and S3/GCS/R2/MinIO-compatible cloud object storage (staging/production).
 */
public interface BinaryObjectStorage {

    void putObject(String objectKey, byte[] content, String contentType);

    default void putObject(String objectKey, byte[] content, String contentType, Map<String, String> metadata) {
        putObject(objectKey, content, contentType);
    }

    Optional<byte[]> getObject(String objectKey);

    void deleteObject(String objectKey);

    boolean exists(String objectKey);

    default Optional<SignedUrlResult> generatePresignedDownloadUrl(String objectKey, Duration ttl) {
        return Optional.empty();
    }

    String getStorageBackendName();
}
