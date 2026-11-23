package com.dineflow.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_reconciliation_logs")
public class PaymentReconciliationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "reconciliation_type", nullable = false, length = 64)
    private String reconciliationType;

    @Column(name = "scanned_count", nullable = false)
    private int scannedCount = 0;

    @Column(name = "mismatch_count", nullable = false)
    private int mismatchCount = 0;

    @Column(name = "reconciled_count", nullable = false)
    private int reconciledCount = 0;

    @Column(name = "discrepancy_details", columnDefinition = "TEXT")
    private String discrepancyDetails;

    @Column(name = "execution_status", nullable = false, length = 32)
    private String executionStatus = "COMPLETED";

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "triggered_by", length = 64)
    private String triggeredBy = "SCHEDULER";

    public PaymentReconciliationLog() {}

    public PaymentReconciliationLog(UUID tenantId, String reconciliationType) {
        this.tenantId = tenantId;
        this.reconciliationType = reconciliationType;
        this.startedAt = Instant.now();
        this.executionStatus = "RUNNING";
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getReconciliationType() { return reconciliationType; }
    public void setReconciliationType(String reconciliationType) { this.reconciliationType = reconciliationType; }
    public int getScannedCount() { return scannedCount; }
    public void setScannedCount(int scannedCount) { this.scannedCount = scannedCount; }
    public int getMismatchCount() { return mismatchCount; }
    public void setMismatchCount(int mismatchCount) { this.mismatchCount = mismatchCount; }
    public int getReconciledCount() { return reconciledCount; }
    public void setReconciledCount(int reconciledCount) { this.reconciledCount = reconciledCount; }
    public String getDiscrepancyDetails() { return discrepancyDetails; }
    public void setDiscrepancyDetails(String discrepancyDetails) { this.discrepancyDetails = discrepancyDetails; }
    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
}
