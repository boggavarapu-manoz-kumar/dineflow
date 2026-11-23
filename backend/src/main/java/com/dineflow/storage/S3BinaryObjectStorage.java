package com.dineflow.storage;

import com.dineflow.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * S3/GCS-interoperable/R2/MinIO cloud object storage implementation using AWS SDK v2.
 * Enforces private-by-default storage, server-side encryption, explicit API timeouts,
 * and short-lived presigned URL generation.
 */
@Service
@ConditionalOnProperty(name = "dineflow.storage.type", havingValue = "s3")
public class S3BinaryObjectStorage implements BinaryObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(S3BinaryObjectStorage.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final boolean serverSideEncryption;
    private final long maxPresignTtlSeconds;

    @Autowired
    public S3BinaryObjectStorage(StorageProperties properties) {
        StorageProperties.S3Config cfg = properties.getS3();
        this.bucket = cfg.getBucket();
        this.serverSideEncryption = cfg.isServerSideEncryption();
        this.maxPresignTtlSeconds = Math.max(60L, properties.getPresignedUrlMaxTtlSeconds());

        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMillis(Math.max(1000L, cfg.getApiCallTimeoutMs())))
                .apiCallAttemptTimeout(Duration.ofMillis(Math.max(500L, cfg.getApiCallAttemptTimeoutMs())))
                .build();

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(cfg.isPathStyleAccess())
                .build();

        S3ClientBuilder clientBuilder = S3Client.builder()
                .region(Region.of(cfg.getRegion()))
                .serviceConfiguration(s3Config)
                .overrideConfiguration(overrideConfig);

        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .region(Region.of(cfg.getRegion()))
                .serviceConfiguration(s3Config);

        if (cfg.getEndpoint() != null && !cfg.getEndpoint().isBlank()) {
            URI endpointUri = URI.create(cfg.getEndpoint());
            clientBuilder.endpointOverride(endpointUri);
            presignerBuilder.endpointOverride(endpointUri);
        }

        this.s3Client = clientBuilder.build();
        this.s3Presigner = presignerBuilder.build();
        log.info("Initialized S3-compatible object storage: bucket={}, region={}, sse={}",
                bucket, cfg.getRegion(), serverSideEncryption);
    }

    /**
     * Explicit constructor for deterministic testing with mocked or local-endpoint S3 clients.
     */
    public S3BinaryObjectStorage(S3Client s3Client,
                                 S3Presigner s3Presigner,
                                 String bucket,
                                 boolean serverSideEncryption,
                                 long maxPresignTtlSeconds) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.serverSideEncryption = serverSideEncryption;
        this.maxPresignTtlSeconds = Math.max(60L, maxPresignTtlSeconds);
    }

    @Override
    public void putObject(String objectKey, byte[] content, String contentType) {
        putObject(objectKey, content, contentType, Map.of());
    }

    @Override
    public void putObject(String objectKey, byte[] content, String contentType, Map<String, String> metadata) {
        validateKey(objectKey);
        try {
            PutObjectRequest.Builder reqBuilder = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType != null ? contentType : "application/octet-stream");

            if (serverSideEncryption) {
                reqBuilder.serverSideEncryption(ServerSideEncryption.AES256);
            }
            if (metadata != null && !metadata.isEmpty()) {
                reqBuilder.metadata(metadata);
            }

            s3Client.putObject(reqBuilder.build(), RequestBody.fromBytes(content));
        } catch (ApiCallTimeoutException | ApiCallAttemptTimeoutException timeoutEx) {
            log.error("Cloud storage upload timed out for key={}", objectKey);
            throw new BusinessException("Cloud storage upload timed out", HttpStatus.GATEWAY_TIMEOUT, "STORAGE_TIMEOUT");
        } catch (Exception e) {
            log.error("Failed to upload object key={} to cloud storage: {}", objectKey, e.getClass().getSimpleName());
            throw new BusinessException("Failed to store object in cloud storage", HttpStatus.SERVICE_UNAVAILABLE, "CLOUD_STORAGE_ERROR");
        }
    }

    @Override
    public Optional<byte[]> getObject(String objectKey) {
        validateKey(objectKey);
        try {
            GetObjectRequest req = GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
            return Optional.of(s3Client.getObjectAsBytes(req).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (ApiCallTimeoutException | ApiCallAttemptTimeoutException timeoutEx) {
            log.error("Cloud storage download timed out for key={}", objectKey);
            throw new BusinessException("Cloud storage download timed out", HttpStatus.GATEWAY_TIMEOUT, "STORAGE_TIMEOUT");
        } catch (Exception e) {
            log.error("Failed to fetch object key={} from cloud storage: {}", objectKey, e.getClass().getSimpleName());
            throw new BusinessException("Failed to retrieve object from cloud storage", HttpStatus.SERVICE_UNAVAILABLE, "CLOUD_STORAGE_ERROR");
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        validateKey(objectKey);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (Exception e) {
            log.warn("Failed to delete cloud object key={}: {}", objectKey, e.getClass().getSimpleName());
        }
    }

    @Override
    public boolean exists(String objectKey) {
        validateKey(objectKey);
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Optional<SignedUrlResult> generatePresignedDownloadUrl(String objectKey, Duration ttl) {
        validateKey(objectKey);
        if (s3Presigner == null) {
            return Optional.empty();
        }
        Duration effectiveTtl = (ttl == null || ttl.isZero() || ttl.isNegative())
                ? Duration.ofSeconds(Math.min(300L, maxPresignTtlSeconds))
                : ttl;
        if (effectiveTtl.getSeconds() > maxPresignTtlSeconds) {
            throw new BusinessException(
                    "Requested signed URL expiration exceeds maximum allowed TTL of " + maxPresignTtlSeconds + " seconds",
                    HttpStatus.BAD_REQUEST,
                    "SIGNED_URL_TTL_EXCEEDED"
            );
        }

        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();
            GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                    .signatureDuration(effectiveTtl)
                    .getObjectRequest(getReq)
                    .build();
            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignReq);
            Instant expiresAt = presigned.expiration() != null
                    ? presigned.expiration()
                    : Instant.now().plus(effectiveTtl);
            return Optional.of(new SignedUrlResult(objectKey, presigned.url().toString(), expiresAt));
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for key={}: {}", objectKey, e.getClass().getSimpleName());
            throw new BusinessException("Failed to generate temporary download URL", HttpStatus.SERVICE_UNAVAILABLE, "SIGNED_URL_ERROR");
        }
    }

    @Override
    public String getStorageBackendName() {
        return "S3_COMPATIBLE";
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.contains("..") || key.startsWith("/") || key.contains("\\") || key.contains("\0")) {
            throw new BusinessException("Invalid cloud storage key", HttpStatus.BAD_REQUEST, "INVALID_STORAGE_KEY");
        }
    }
}
