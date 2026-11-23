package com.dineflow.storage;

import com.dineflow.common.exception.BusinessException;
import com.dineflow.config.ProductionConfigurationValidator;
import com.dineflow.invoice.service.DocumentStorageService;
import com.dineflow.menu.service.ImageStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security, integrity, and provider abstraction tests for DineFlow's unified object storage.
 * Note: S3 provider tests use mocked AWS SDK S3Client/S3Presigner (not live AWS/GCS cloud calls).
 */
@SpringBootTest
@ActiveProfiles("test")
class CloudObjectStorageSecurityTests {

    private static final byte[] VALID_PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };
    private static final byte[] VALID_PDF_BYTES = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private ObjectStorageService objectStorageService;

    @Autowired
    private ImageStorageService imageStorageService;

    @Autowired
    private DocumentStorageService documentStorageService;

    @Autowired
    private StorageMigrationService migrationService;

    @Test
    void uploadDownloadAndCrossTenantIsolationVerifiedOnLocalProvider() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        StorageObjectMetadata uploaded = objectStorageService.upload(new StorageUploadRequest(
                tenantA, UUID.randomUUID(), StorageCategory.INVOICE_PDF, invoiceId,
                "../../evil-name.pdf", "application/pdf", VALID_PDF_BYTES
        ));

        // Opaque key must NEVER contain user-supplied filename
        assertFalse(uploaded.objectKey().contains("evil-name"));
        assertTrue(uploaded.objectKey().startsWith("documents/invoices/" + tenantA + "/" + invoiceId + "/"));
        assertNotNull(uploaded.sha256Checksum());

        // Tenant A downloads & verifies checksum cleanly
        StorageDownloadResult downloaded = objectStorageService
                .downloadAndVerify(tenantA, uploaded.objectKey(), uploaded.sha256Checksum())
                .orElseThrow();
        assertArrayEquals(VALID_PDF_BYTES, downloaded.content());

        // Tenant B is strictly blocked from accessing Tenant A's object
        BusinessException crossTenantEx = assertThrows(BusinessException.class,
                () -> objectStorageService.download(tenantB, uploaded.objectKey()));
        assertEquals(HttpStatus.FORBIDDEN, crossTenantEx.getStatus());
        assertEquals("CROSS_TENANT_STORAGE_ACCESS", crossTenantEx.getErrorCode());

        // Public image loader MUST reject private invoice keys
        BusinessException scopeEx = assertThrows(BusinessException.class,
                () -> objectStorageService.downloadMenuImage(uploaded.objectKey()));
        assertEquals(HttpStatus.FORBIDDEN, scopeEx.getStatus());
        assertEquals("FORBIDDEN_STORAGE_SCOPE", scopeEx.getErrorCode());

        // Checksum mismatch detection
        BusinessException checksumEx = assertThrows(BusinessException.class,
                () -> objectStorageService.downloadAndVerify(tenantA, uploaded.objectKey(), "00000000000000000000000000000000"));
        assertEquals("CHECKSUM_MISMATCH", checksumEx.getErrorCode());

        // Controlled tenant-scoped deletion
        StorageDeleteResult del = objectStorageService.delete(tenantA, uploaded.objectKey());
        assertTrue(del.deleted());
        assertTrue(objectStorageService.download(tenantA, uploaded.objectKey()).isEmpty());
    }

    @Test
    void invalidContentTypeMagicBytesOversizedAndMalformedKeysAreRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        // Spoofed executable disguised as PNG
        byte[] fakePng = "MZ_NOT_A_PNG_EXECUTABLE".getBytes(StandardCharsets.UTF_8);
        BusinessException spoofEx = assertThrows(BusinessException.class, () ->
                objectStorageService.upload(new StorageUploadRequest(
                        tenantId, null, StorageCategory.MENU_IMAGE, entityId, "dish.png", "image/png", fakePng)));
        assertEquals("IMAGE_SIGNATURE_MISMATCH", spoofEx.getErrorCode());

        // Unsupported MIME type for menu image
        BusinessException mimeEx = assertThrows(BusinessException.class, () ->
                objectStorageService.upload(new StorageUploadRequest(
                        tenantId, null, StorageCategory.MENU_IMAGE, entityId, "dish.svg", "image/svg+xml", VALID_PNG_BYTES)));
        assertEquals("INVALID_IMAGE_TYPE", mimeEx.getErrorCode());

        // Oversized image (> 5MB)
        byte[] oversized = new byte[5 * 1024 * 1024 + 64];
        System.arraycopy(VALID_PNG_BYTES, 0, oversized, 0, VALID_PNG_BYTES.length);
        BusinessException sizeEx = assertThrows(BusinessException.class, () ->
                objectStorageService.upload(new StorageUploadRequest(
                        tenantId, null, StorageCategory.MENU_IMAGE, entityId, "huge.png", "image/png", oversized)));
        assertEquals("FILE_TOO_LARGE", sizeEx.getErrorCode());

        // Path traversal / malformed storage keys
        for (String badKey : new String[]{"../secret.pdf", "/etc/passwd", "images//double/key.png", "images\\win\\key.png"}) {
            BusinessException keyEx = assertThrows(BusinessException.class,
                    () -> objectStorageService.download(tenantId, badKey));
            assertEquals("INVALID_STORAGE_KEY", keyEx.getErrorCode());
        }

        // Missing object returns Optional.empty()
        assertTrue(objectStorageService.download(tenantId, "documents/invoices/" + tenantId + "/missing.pdf").isEmpty());
    }

    @Test
    void imageAndDocumentAdaptersPreserveExistingApisThroughUnifiedStorage() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        MockMultipartFile multipart = new MockMultipartFile("file", "paneer.png", "image/png", VALID_PNG_BYTES);
        ImageStorageService.StoredImageResult imgResult = imageStorageService.storeImage(tenantId, itemId, multipart);
        assertTrue(imgResult.imageUrl().startsWith("/api/v1/public/images/" + tenantId + "/" + itemId + "/"));
        assertTrue(imageStorageService.loadAsResource(imgResult.storageKey()).isPresent());

        imageStorageService.deleteImage(imgResult.storageKey());
        assertTrue(imageStorageService.loadAsResource(imgResult.storageKey()).isEmpty());

        // DocumentStorageService adapter
        DocumentStorageService.StoredDocumentResult docResult =
                documentStorageService.storeDocument(tenantId, itemId, "INV-2026-001.pdf", VALID_PDF_BYTES);
        assertTrue(documentStorageService.loadDocumentAsResource(tenantId, docResult.storageKey(), docResult.checksumSha256()).isPresent());
        // Cross-tenant document load returns empty
        assertTrue(documentStorageService.loadDocumentAsResource(UUID.randomUUID(), docResult.storageKey()).isEmpty());
    }

    @Test
    void mockedS3ProviderEnforcesSseTimeoutsFailuresAndShortSignedUrlExpiration() throws Exception {
        S3Client mockS3 = mock(S3Client.class);
        S3Presigner mockPresigner = mock(S3Presigner.class);

        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        S3BinaryObjectStorage s3Storage = new S3BinaryObjectStorage(mockS3, mockPresigner, "dineflow-prod-bucket", true, 900);
        StorageProperties props = new StorageProperties();
        props.setType("s3");
        props.setLocalFallbackReadEnabled(false);
        ObjectStorageService s3ObjectService = new ObjectStorageService(s3Storage, props);

        UUID tenantId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        // 1. Upload verifies SSE-AES256 & tenant metadata
        StorageObjectMetadata meta = s3ObjectService.upload(new StorageUploadRequest(
                tenantId, null, StorageCategory.INVOICE_PDF, entityId, "inv.pdf", "application/pdf", VALID_PDF_BYTES));
        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3).putObject(putCaptor.capture(), any(RequestBody.class));
        assertEquals(ServerSideEncryption.AES256, putCaptor.getValue().serverSideEncryption());
        assertEquals(tenantId.toString(), putCaptor.getValue().metadata().get("tenantId"));

        // 2. Download via mocked S3
        when(mockS3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), VALID_PDF_BYTES));
        Optional<StorageDownloadResult> dl = s3ObjectService.downloadAndVerify(tenantId, meta.objectKey(), meta.sha256Checksum());
        assertTrue(dl.isPresent());
        assertArrayEquals(VALID_PDF_BYTES, dl.get().content());

        // 3. Missing key returns empty
        when(mockS3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("missing").build());
        assertTrue(s3ObjectService.download(tenantId, meta.objectKey()).isEmpty());

        // 4. Provider timeout maps to 504 STORAGE_TIMEOUT
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(ApiCallTimeoutException.builder().message("Timed out").build());
        BusinessException timeoutEx = assertThrows(BusinessException.class, () ->
                s3ObjectService.upload(new StorageUploadRequest(
                        tenantId, null, StorageCategory.INVOICE_PDF, entityId, "inv.pdf", "application/pdf", VALID_PDF_BYTES)));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, timeoutEx.getStatus());
        assertEquals("STORAGE_TIMEOUT", timeoutEx.getErrorCode());

        // 5. Failed upload maps to 503 CLOUD_STORAGE_ERROR
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 503 SlowDown"));
        BusinessException failEx = assertThrows(BusinessException.class, () ->
                s3ObjectService.upload(new StorageUploadRequest(
                        tenantId, null, StorageCategory.INVOICE_PDF, entityId, "inv.pdf", "application/pdf", VALID_PDF_BYTES)));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failEx.getStatus());
        assertEquals("CLOUD_STORAGE_ERROR", failEx.getErrorCode());

        // 6. Signed URL generation succeeds within TTL cap and rejects > 900s
        PresignedGetObjectRequest mockPresigned = mock(PresignedGetObjectRequest.class);
        when(mockPresigned.url()).thenReturn(URI.create("https://dineflow-prod-bucket.s3.ap-south-1.amazonaws.com/" + meta.objectKey() + "?X-Amz-Expires=300").toURL());
        when(mockPresigned.expiration()).thenReturn(Instant.now().plusSeconds(300));
        when(mockPresigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(mockPresigned);

        Optional<SignedUrlResult> signed = s3ObjectService.generateSignedDownloadUrl(tenantId, meta.objectKey(), Duration.ofMinutes(5));
        assertTrue(signed.isPresent());
        assertTrue(signed.get().url().contains("X-Amz-Expires=300"));

        BusinessException ttlEx = assertThrows(BusinessException.class,
                () -> s3ObjectService.generateSignedDownloadUrl(tenantId, meta.objectKey(), Duration.ofHours(2)));
        assertEquals("INVALID_SIGNED_URL_TTL", ttlEx.getErrorCode());
    }

    @Test
    void migrationUtilityVerifiesChecksumsAndPreservesSourceFiles(@TempDir Path tempSource, @TempDir Path tempTarget) throws Exception {
        Path sampleDir = tempSource.resolve("documents/invoices/tenant-1");
        Files.createDirectories(sampleDir);
        Path sampleFile = sampleDir.resolve("sample.pdf");
        Files.write(sampleFile, VALID_PDF_BYTES);

        StorageProperties targetProps = new StorageProperties();
        targetProps.setLocalRootPath(tempTarget.toString());
        LocalBinaryObjectStorage targetStorage = new LocalBinaryObjectStorage(targetProps);

        StorageMigrationService.MigrationSummary summary =
                migrationService.migrateDirectoryToTarget(tempSource, targetStorage, false);

        assertEquals(1, summary.scannedFiles());
        assertEquals(1, summary.migratedFiles());
        assertEquals(1, summary.verifiedCount());
        assertEquals(0, summary.failedCount());
        assertTrue(Files.exists(sampleFile), "Source file must remain intact when deleteVerifiedSource=false");
        assertTrue(targetStorage.exists("documents/invoices/tenant-1/sample.pdf"));
    }

    @Test
    void productionValidatorRejectsLocalStorageAndMissingS3Bucket() {
        StorageProperties localProps = new StorageProperties();
        localProps.setType("local");
        assertThrows(IllegalStateException.class, () -> ProductionConfigurationValidator.validateStorage(localProps));

        StorageProperties badS3 = new StorageProperties();
        badS3.setType("s3");
        badS3.getS3().setBucket("");
        assertThrows(IllegalStateException.class, () -> ProductionConfigurationValidator.validateStorage(badS3));

        StorageProperties validS3 = new StorageProperties();
        validS3.setType("s3");
        validS3.getS3().setBucket("dineflow-prod-assets");
        validS3.getS3().setRegion("ap-south-1");
        ProductionConfigurationValidator.validateStorage(validS3);
    }
}
