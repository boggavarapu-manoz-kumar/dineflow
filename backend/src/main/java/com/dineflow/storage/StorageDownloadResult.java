package com.dineflow.storage;

public record StorageDownloadResult(
        String objectKey,
        byte[] content,
        String contentType,
        long contentLength,
        String sha256Checksum
) {}
