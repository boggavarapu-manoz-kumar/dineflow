package com.dineflow.storage;

public record StorageDeleteResult(
        String objectKey,
        boolean deleted,
        String storageBackend
) {}
