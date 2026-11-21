package com.dineflow.menu.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

public interface ImageStorageService {
    StoredImageResult storeImage(UUID tenantId, UUID entityId, MultipartFile file);
    void deleteImage(String storageKey);
    Optional<Resource> loadAsResource(String storageKey);

    record StoredImageResult(
            String imageUrl,
            String storageKey,
            String originalFileName,
            long fileSizeBytes,
            String contentType
    ) {}
}
