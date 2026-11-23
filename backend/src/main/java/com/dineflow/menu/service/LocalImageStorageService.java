package com.dineflow.menu.service;

import com.dineflow.common.exception.BusinessException;
import com.dineflow.storage.ObjectStorageService;
import com.dineflow.storage.StorageCategory;
import com.dineflow.storage.StorageObjectMetadata;
import com.dineflow.storage.StorageUploadRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Menu image storage adapter backed by the unified {@link ObjectStorageService}.
 * Works seamlessly with local filesystem (dev/test) and S3/GCS-compatible cloud
 * object storage (staging/production) while preserving the existing public image API contract.
 */
@Service
public class LocalImageStorageService implements ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalImageStorageService.class);
    private static final String IMAGE_PREFIX = StorageCategory.MENU_IMAGE.getKeyPrefix() + "/";

    private final ObjectStorageService objectStorageService;

    public LocalImageStorageService(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    @Override
    public StoredImageResult storeImage(UUID tenantId, UUID entityId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Image file is missing or empty", HttpStatus.BAD_REQUEST, "INVALID_IMAGE_FILE");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            log.error("Unable to read uploaded image bytes for tenant={}", tenantId, e);
            throw new BusinessException("Unable to read uploaded image", HttpStatus.BAD_REQUEST, "INVALID_IMAGE_STREAM");
        }

        StorageObjectMetadata meta = objectStorageService.upload(new StorageUploadRequest(
                tenantId,
                null,
                StorageCategory.MENU_IMAGE,
                entityId,
                file.getOriginalFilename(),
                file.getContentType(),
                content
        ));

        String relativeKey = meta.objectKey().startsWith(IMAGE_PREFIX)
                ? meta.objectKey().substring(IMAGE_PREFIX.length())
                : meta.objectKey();

        String imageUrl = "/api/v1/public/images/" + relativeKey;
        return new StoredImageResult(imageUrl, relativeKey, file.getOriginalFilename(), meta.contentLength(), meta.contentType());
    }

    @Override
    public void deleteImage(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")) {
            return;
        }
        String normalized = storageKey.startsWith(IMAGE_PREFIX) ? storageKey : IMAGE_PREFIX + storageKey;
        String withoutPrefix = normalized.substring(IMAGE_PREFIX.length());
        int slashIdx = withoutPrefix.indexOf('/');
        if (slashIdx <= 0) {
            return;
        }
        try {
            UUID tenantId = UUID.fromString(withoutPrefix.substring(0, slashIdx));
            objectStorageService.delete(tenantId, normalized);
        } catch (Exception e) {
            log.warn("Failed to delete image key={}: {}", storageKey, e.getMessage());
        }
    }

    @Override
    public Optional<Resource> loadAsResource(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")) {
            return Optional.empty();
        }
        String fullKey = storageKey.startsWith(IMAGE_PREFIX) ? storageKey : IMAGE_PREFIX + storageKey;
        try {
            return objectStorageService.downloadMenuImage(fullKey)
                    .map(result -> new ByteArrayResource(result.content()));
        } catch (BusinessException ex) {
            log.warn("Rejected unsafe image load attempt for key={}: {}", storageKey, ex.getErrorCode());
            return Optional.empty();
        }
    }
}
