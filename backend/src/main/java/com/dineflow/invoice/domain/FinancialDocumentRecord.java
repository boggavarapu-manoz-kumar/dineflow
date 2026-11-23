package com.dineflow.invoice.domain;

import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(
        name = "financial_document_records",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_financial_docs_storage_key", columnNames = {"tenant_id", "storage_key"})
        }
)
public class FinancialDocumentRecord extends TenantAwareEntity {

    @Column(name = "document_type", nullable = false, length = 32)
    private String documentType; // INVOICE_PDF, RECEIPT_PDF

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "storage_provider", nullable = false, length = 32)
    private String storageProvider = "LOCAL_FILE";

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType = "application/pdf";

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    public FinancialDocumentRecord() {
    }

    public FinancialDocumentRecord(UUID tenantId, String documentType, UUID entityId, String storageProvider,
                                   String storageKey, String fileName, String contentType,
                                   Long fileSizeBytes, String checksumSha256) {
        setTenantId(tenantId);
        this.documentType = documentType;
        this.entityId = entityId;
        this.storageProvider = storageProvider;
        this.storageKey = storageKey;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.checksumSha256 = checksumSha256;
    }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public String getStorageProvider() { return storageProvider; }
    public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
}
