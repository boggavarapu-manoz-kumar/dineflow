package com.dineflow.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "dineflow.storage")
public class StorageProperties {

    private String type = "local"; // local | s3
    private String localRootPath = "uploads";
    private boolean localFallbackReadEnabled = true;
    private long maxImageSizeBytes = 5L * 1024 * 1024; // 5 MB
    private long maxDocumentSizeBytes = 10L * 1024 * 1024; // 10 MB
    private long presignedUrlDefaultTtlSeconds = 300; // 5 minutes
    private long presignedUrlMaxTtlSeconds = 900; // 15 minutes hard cap
    private S3Config s3 = new S3Config();

    public static class S3Config {
        private String bucket = "dineflow-assets";
        private String region = "ap-south-1";
        private String endpoint;
        private boolean pathStyleAccess = false;
        private boolean serverSideEncryption = true;
        private long apiCallTimeoutMs = 15000;
        private long apiCallAttemptTimeoutMs = 5000;

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public boolean isPathStyleAccess() { return pathStyleAccess; }
        public void setPathStyleAccess(boolean pathStyleAccess) { this.pathStyleAccess = pathStyleAccess; }
        public boolean isServerSideEncryption() { return serverSideEncryption; }
        public void setServerSideEncryption(boolean serverSideEncryption) { this.serverSideEncryption = serverSideEncryption; }
        public long getApiCallTimeoutMs() { return apiCallTimeoutMs; }
        public void setApiCallTimeoutMs(long apiCallTimeoutMs) { this.apiCallTimeoutMs = apiCallTimeoutMs; }
        public long getApiCallAttemptTimeoutMs() { return apiCallAttemptTimeoutMs; }
        public void setApiCallAttemptTimeoutMs(long apiCallAttemptTimeoutMs) { this.apiCallAttemptTimeoutMs = apiCallAttemptTimeoutMs; }
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLocalRootPath() { return localRootPath; }
    public void setLocalRootPath(String localRootPath) { this.localRootPath = localRootPath; }
    public boolean isLocalFallbackReadEnabled() { return localFallbackReadEnabled; }
    public void setLocalFallbackReadEnabled(boolean localFallbackReadEnabled) { this.localFallbackReadEnabled = localFallbackReadEnabled; }
    public long getMaxImageSizeBytes() { return maxImageSizeBytes; }
    public void setMaxImageSizeBytes(long maxImageSizeBytes) { this.maxImageSizeBytes = maxImageSizeBytes; }
    public long getMaxDocumentSizeBytes() { return maxDocumentSizeBytes; }
    public void setMaxDocumentSizeBytes(long maxDocumentSizeBytes) { this.maxDocumentSizeBytes = maxDocumentSizeBytes; }
    public long getPresignedUrlDefaultTtlSeconds() { return presignedUrlDefaultTtlSeconds; }
    public void setPresignedUrlDefaultTtlSeconds(long presignedUrlDefaultTtlSeconds) { this.presignedUrlDefaultTtlSeconds = presignedUrlDefaultTtlSeconds; }
    public long getPresignedUrlMaxTtlSeconds() { return presignedUrlMaxTtlSeconds; }
    public void setPresignedUrlMaxTtlSeconds(long presignedUrlMaxTtlSeconds) { this.presignedUrlMaxTtlSeconds = presignedUrlMaxTtlSeconds; }
    public S3Config getS3() { return s3; }
    public void setS3(S3Config s3) { this.s3 = s3; }
}
