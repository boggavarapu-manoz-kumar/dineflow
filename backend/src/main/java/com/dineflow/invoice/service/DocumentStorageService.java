package com.dineflow.invoice.service;

import com.dineflow.common.exception.BusinessException;
import com.dineflow.storage.ObjectStorageService;
import com.dineflow.storage.SignedUrlResult;
import com.dineflow.storage.StorageCategory;
import com.dineflow.storage.StorageObjectMetadata;
import com.dineflow.storage.StorageUploadRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Invoice & financial document storage adapter backed by the unified {@link ObjectStorageService}.
 * Stores private documents under opaque tenant-isolated keys and verifies SHA-256 integrity on download.
 */
@Service
public class DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);
    private static final String KEY_PREFIX = StorageCategory.INVOICE_PDF.getKeyPrefix() + "/";

    private final ObjectStorageService objectStorageService;

    public DocumentStorageService(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    public record StoredDocumentResult(
            String storageKey,
            String fileName,
            long fileSizeBytes,
            String checksumSha256
    ) {}

    public StoredDocumentResult storeDocument(UUID tenantId, UUID entityId, String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException("Document payload is empty", HttpStatus.BAD_REQUEST, "EMPTY_DOCUMENT");
        }
        if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException("Invalid document filename", HttpStatus.BAD_REQUEST, "PATH_TRAVERSAL_DETECTED");
        }

        StorageObjectMetadata meta = objectStorageService.upload(new StorageUploadRequest(
                tenantId,
                null,
                StorageCategory.INVOICE_PDF,
                entityId,
                fileName,
                "application/pdf",
                content
        ));

        String relativeKey = meta.objectKey().startsWith(KEY_PREFIX)
                ? meta.objectKey().substring(KEY_PREFIX.length())
                : meta.objectKey();

        return new StoredDocumentResult(relativeKey, fileName, meta.contentLength(), meta.sha256Checksum());
    }

    public Optional<Resource> loadDocumentAsResource(UUID tenantId, String storageKey) {
        return loadDocumentAsResource(tenantId, storageKey, null);
    }

    public Optional<Resource> loadDocumentAsResource(UUID tenantId, String storageKey, String expectedChecksumSha256) {
        if (tenantId == null || storageKey == null || storageKey.isBlank() || storageKey.contains("..")) {
            return Optional.empty();
        }
        String fullKey = storageKey.startsWith(KEY_PREFIX) ? storageKey : KEY_PREFIX + storageKey;
        try {
            return objectStorageService.downloadAndVerify(tenantId, fullKey, expectedChecksumSha256)
                    .map(result -> new ByteArrayResource(result.content()));
        } catch (BusinessException ex) {
            if ("CHECKSUM_MISMATCH".equals(ex.getErrorCode())) {
                throw ex;
            }
            log.warn("Blocked unauthorized or invalid document load for tenant={}: {}", tenantId, ex.getErrorCode());
            return Optional.empty();
        }
    }

    public Optional<SignedUrlResult> generateTemporaryDownloadUrl(UUID tenantId, String storageKey, Duration ttl) {
        String fullKey = storageKey != null && storageKey.startsWith(KEY_PREFIX) ? storageKey : KEY_PREFIX + storageKey;
        return objectStorageService.generateSignedDownloadUrl(tenantId, fullKey, ttl);
    }

    public String getStorageBackendName() {
        return objectStorageService.getStorageBackendName();
    }
}
