package com.dineflow.storage;

import com.dineflow.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Unified, provider-independent object storage service for DineFlow.
 * Enforces tenant isolation, opaque key generation, magic-byte content validation,
 * file size limits, SHA-256 integrity verification, short-lived signed URLs,
 * and zero-downtime local-fallback reads during cloud storage migration.
 */
@Service
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp", "text/csv", "text/plain"
    );
    private static final Pattern SAFE_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9/_\\-.]{5,512}$");

    private final BinaryObjectStorage provider;
    private final StorageProperties properties;
    private final Path localFallbackRoot;

    public ObjectStorageService(BinaryObjectStorage provider, StorageProperties properties) {
        this.provider = provider;
        this.properties = properties;
        this.localFallbackRoot = Paths.get(properties.getLocalRootPath()).toAbsolutePath().normalize();
    }

    public StorageObjectMetadata upload(StorageUploadRequest request) {
        if (request == null || request.tenantId() == null || request.entityId() == null || request.category() == null) {
            throw new BusinessException("Tenant ID, entity ID, and storage category are required", HttpStatus.BAD_REQUEST, "INVALID_STORAGE_REQUEST");
        }
        byte[] content = request.content();
        if (content == null || content.length == 0) {
            throw new BusinessException("Uploaded file content is empty", HttpStatus.BAD_REQUEST, "EMPTY_FILE");
        }

        long maxAllowed = (request.category() == StorageCategory.MENU_IMAGE)
                ? properties.getMaxImageSizeBytes()
                : properties.getMaxDocumentSizeBytes();
        if (content.length > maxAllowed) {
            throw new BusinessException(
                    "Uploaded file exceeds maximum allowed size of " + (maxAllowed / (1024 * 1024)) + "MB",
                    HttpStatus.BAD_REQUEST,
                    "FILE_TOO_LARGE"
            );
        }

        String normalizedContentType = request.contentType() != null ? request.contentType().trim().toLowerCase() : "";
        validateContentTypeAndMagicBytes(request.category(), normalizedContentType, content);

        String extension = resolveSafeExtension(normalizedContentType);
        // Opaque key: NEVER incorporate user-supplied filenames into storage paths/keys
        String opaqueFileName = UUID.randomUUID() + extension;
        String objectKey = request.category().getKeyPrefix() + "/"
                + request.tenantId() + "/"
                + request.entityId() + "/"
                + opaqueFileName;

        String sha256 = computeSha256(content);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("tenantId", request.tenantId().toString());
        if (request.branchId() != null) {
            metadata.put("branchId", request.branchId().toString());
        }
        metadata.put("category", request.category().name());
        metadata.put("sha256", sha256);

        provider.putObject(objectKey, content, normalizedContentType, metadata);

        return new StorageObjectMetadata(
                objectKey,
                request.tenantId(),
                request.branchId(),
                request.category(),
                normalizedContentType,
                content.length,
                sha256,
                provider.getStorageBackendName(),
                Map.copyOf(metadata)
        );
    }

    /**
     * Downloads a tenant-scoped object after verifying key structure and tenant ownership.
     */
    public Optional<StorageDownloadResult> download(UUID tenantId, String objectKey) {
        return downloadAndVerify(tenantId, objectKey, null);
    }

    /**
     * Downloads a tenant-scoped object and verifies its SHA-256 checksum if expectedSha256 is provided.
     */
    public Optional<StorageDownloadResult> downloadAndVerify(UUID tenantId, String objectKey, String expectedSha256) {
        validateObjectKey(objectKey);
        assertTenantOwnsKey(tenantId, objectKey);

        Optional<byte[]> bytesOpt = fetchWithMigrationFallback(objectKey);
        if (bytesOpt.isEmpty()) {
            return Optional.empty();
        }

        byte[] bytes = bytesOpt.get();
        String actualSha256 = computeSha256(bytes);
        if (expectedSha256 != null && !expectedSha256.isBlank() && !expectedSha256.equalsIgnoreCase(actualSha256)) {
            log.error("SHA-256 checksum mismatch for tenant={} objectKey={}", tenantId, objectKey);
            throw new BusinessException("Stored object failed integrity checksum verification", HttpStatus.INTERNAL_SERVER_ERROR, "CHECKSUM_MISMATCH");
        }

        return Optional.of(new StorageDownloadResult(
                objectKey,
                bytes,
                inferContentTypeFromExtension(objectKey),
                bytes.length,
                actualSha256
        ));
    }

    /**
     * Public-menu image read helper. Strictly restricts access to the "images/" prefix so
     * private invoices or business documents can NEVER be served via the public image route.
     */
    public Optional<StorageDownloadResult> downloadMenuImage(String objectKey) {
        validateObjectKey(objectKey);
        if (!objectKey.startsWith(StorageCategory.MENU_IMAGE.getKeyPrefix() + "/")) {
            throw new BusinessException("Unauthorized access to non-image storage key", HttpStatus.FORBIDDEN, "FORBIDDEN_STORAGE_SCOPE");
        }
        return fetchWithMigrationFallback(objectKey).map(bytes ->
                new StorageDownloadResult(objectKey, bytes, inferContentTypeFromExtension(objectKey), bytes.length, computeSha256(bytes))
        );
    }

    public StorageDeleteResult delete(UUID tenantId, String objectKey) {
        validateObjectKey(objectKey);
        assertTenantOwnsKey(tenantId, objectKey);
        boolean existed = provider.exists(objectKey);
        provider.deleteObject(objectKey);
        return new StorageDeleteResult(objectKey, existed, provider.getStorageBackendName());
    }

    public Optional<SignedUrlResult> generateSignedDownloadUrl(UUID tenantId, String objectKey, Duration requestedTtl) {
        validateObjectKey(objectKey);
        assertTenantOwnsKey(tenantId, objectKey);

        Duration ttl = requestedTtl != null
                ? requestedTtl
                : Duration.ofSeconds(properties.getPresignedUrlDefaultTtlSeconds());
        if (ttl.isZero() || ttl.isNegative() || ttl.getSeconds() > properties.getPresignedUrlMaxTtlSeconds()) {
            throw new BusinessException(
                    "Signed URL expiration must be between 1 and " + properties.getPresignedUrlMaxTtlSeconds() + " seconds",
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SIGNED_URL_TTL"
            );
        }
        return provider.generatePresignedDownloadUrl(objectKey, ttl);
    }

    public String getStorageBackendName() {
        return provider.getStorageBackendName();
    }

    public static String computeSha256(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException("SHA-256 digest unavailable", HttpStatus.INTERNAL_SERVER_ERROR, "CHECKSUM_ERROR");
        }
    }

    private Optional<byte[]> fetchWithMigrationFallback(String objectKey) {
        Optional<byte[]> primary = provider.getObject(objectKey);
        if (primary.isPresent()) {
            return primary;
        }
        // Zero-downtime migration fallback: read legacy local file if primary is cloud storage
        if (properties.isLocalFallbackReadEnabled() && !"LOCAL_FILE".equals(provider.getStorageBackendName())) {
            Path legacyPath = localFallbackRoot.resolve(objectKey).normalize();
            if (legacyPath.startsWith(localFallbackRoot) && Files.exists(legacyPath) && Files.isReadable(legacyPath)) {
                try {
                    return Optional.of(Files.readAllBytes(legacyPath));
                } catch (IOException e) {
                    log.warn("Local fallback read failed for key={}", objectKey);
                }
            }
        }
        return Optional.empty();
    }

    private void validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()
                || objectKey.contains("..")
                || objectKey.contains("\\")
                || objectKey.startsWith("/")
                || objectKey.contains("//")
                || !SAFE_KEY_PATTERN.matcher(objectKey).matches()) {
            throw new BusinessException("Malformed or unsafe storage key", HttpStatus.BAD_REQUEST, "INVALID_STORAGE_KEY");
        }
    }

    private void assertTenantOwnsKey(UUID tenantId, String objectKey) {
        if (tenantId == null) {
            throw new BusinessException("Tenant context is required for storage access", HttpStatus.FORBIDDEN, "TENANT_REQUIRED");
        }
        String tenantSegment = "/" + tenantId + "/";
        boolean owned = objectKey.startsWith(tenantId + "/") || objectKey.contains(tenantSegment);
        if (!owned) {
            log.warn("Cross-tenant storage access blocked: tenant={} attempted key={}", tenantId, objectKey);
            throw new BusinessException("Unauthorized cross-tenant storage access", HttpStatus.FORBIDDEN, "CROSS_TENANT_STORAGE_ACCESS");
        }
    }

    private void validateContentTypeAndMagicBytes(StorageCategory category, String contentType, byte[] content) {
        if (category == StorageCategory.MENU_IMAGE) {
            if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
                throw new BusinessException("Only JPEG, PNG, and WebP images are permitted", HttpStatus.BAD_REQUEST, "INVALID_IMAGE_TYPE");
            }
            if (!matchesImageMagicBytes(contentType, content)) {
                throw new BusinessException("File content does not match declared image content type", HttpStatus.BAD_REQUEST, "IMAGE_SIGNATURE_MISMATCH");
            }
        } else if (category == StorageCategory.INVOICE_PDF) {
            if (!"application/pdf".equals(contentType)) {
                throw new BusinessException("Invoices must be application/pdf", HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT_TYPE");
            }
            if (!matchesPdfMagicBytes(content)) {
                throw new BusinessException("File content is not a valid PDF document", HttpStatus.BAD_REQUEST, "CONTENT_SIGNATURE_MISMATCH");
            }
        } else {
            if (!ALLOWED_DOCUMENT_TYPES.contains(contentType)) {
                throw new BusinessException("Unsupported document content type: " + contentType, HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT_TYPE");
            }
            if ("application/pdf".equals(contentType) && !matchesPdfMagicBytes(content)) {
                throw new BusinessException("File content is not a valid PDF document", HttpStatus.BAD_REQUEST, "CONTENT_SIGNATURE_MISMATCH");
            } else if (ALLOWED_IMAGE_TYPES.contains(contentType) && !matchesImageMagicBytes(contentType, content)) {
                throw new BusinessException("File content does not match declared image content type", HttpStatus.BAD_REQUEST, "CONTENT_SIGNATURE_MISMATCH");
            }
        }
    }

    private boolean matchesImageMagicBytes(String contentType, byte[] data) {
        if (data.length < 4) return false;
        return switch (contentType) {
            case "image/jpeg" -> data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF;
            case "image/png" -> data[0] == (byte) 0x89 && data[1] == (byte) 0x50 && data[2] == (byte) 0x4E && data[3] == (byte) 0x47;
            case "image/webp" -> data.length >= 12
                    && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                    && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
            default -> false;
        };
    }

    private boolean matchesPdfMagicBytes(byte[] data) {
        return data.length >= 5
                && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F' && data[4] == '-';
    }

    private String resolveSafeExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            case "text/csv" -> ".csv";
            case "text/plain" -> ".txt";
            default -> ".bin";
        };
    }

    private String inferContentTypeFromExtension(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".csv")) return "text/csv";
        return "application/octet-stream";
    }
}
