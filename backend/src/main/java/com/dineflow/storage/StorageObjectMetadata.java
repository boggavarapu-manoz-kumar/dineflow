package com.dineflow.storage;

import java.util.Map;
import java.util.UUID;

public record StorageObjectMetadata(
        String objectKey,
        UUID tenantId,
        UUID branchId,
        StorageCategory category,
        String contentType,
        long contentLength,
        String sha256Checksum,
        String storageBackend,
        Map<String, String> customMetadata
) {}
