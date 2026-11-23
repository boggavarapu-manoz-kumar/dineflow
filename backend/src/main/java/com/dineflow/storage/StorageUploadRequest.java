package com.dineflow.storage;

import java.util.UUID;

public record StorageUploadRequest(
        UUID tenantId,
        UUID branchId,
        StorageCategory category,
        UUID entityId,
        String originalFileName,
        String contentType,
        byte[] content
) {}
