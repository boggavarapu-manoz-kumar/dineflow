package com.dineflow.payment.domain;

import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payment_attempts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_payment_attempt_idempotency", columnNames = {"tenant_id", "idempotency_key"})
        }
)
public class PaymentAttempt extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private RestaurantPayment payment;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "ambiguous_outcome", nullable = false)
    private boolean ambiguousOutcome = false;

    @Column(name = "reconciliation_retry_count", nullable = false)
    private int reconciliationRetryCount = 0;

    @Column(name = "next_reconciliation_at")
    private Instant nextReconciliationAt;

    @Column(name = "manual_review_required", nullable = false)
    private boolean manualReviewRequired = false;

    @Column(name = "provider_name", nullable = false, length = 32)
    private String providerName;

    @Column(name = "provider_order_id", length = 128)
    private String providerOrderId;

    @Column(name = "provider_transaction_id", length = 128)
    private String providerTransactionId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "INITIATED"; // INITIATED, PROCESSING, SUCCEEDED, FAILED, TIMED_OUT, CANCELLED

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 512)
    private String failureMessage;

    @Column(name = "client_redirect_url", length = 1024)
    private String clientRedirectUrl;

    @Column(name = "raw_provider_response", columnDefinition = "TEXT")
    private String rawProviderResponse;

    public PaymentAttempt() {
    }

    public PaymentAttempt(RestaurantPayment payment, UUID tenantId, Integer attemptNumber, String idempotencyKey,
                          String providerName, BigDecimal amount, String currencyCode) {
        this.payment = payment;
        setTenantId(tenantId);
        this.attemptNumber = attemptNumber != null ? attemptNumber : 1;
        this.idempotencyKey = idempotencyKey;
        this.providerName = providerName;
        this.amount = amount;
        this.currencyCode = currencyCode != null ? currencyCode : "INR";
        this.status = "INITIATED";
    }

    // Getters and Setters
    public RestaurantPayment getPayment() { return payment; }
    public void setPayment(RestaurantPayment payment) { this.payment = payment; }

    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }

    public boolean isAmbiguousOutcome() { return ambiguousOutcome; }
    public void setAmbiguousOutcome(boolean ambiguousOutcome) { this.ambiguousOutcome = ambiguousOutcome; }

    public int getReconciliationRetryCount() { return reconciliationRetryCount; }
    public void setReconciliationRetryCount(int reconciliationRetryCount) { this.reconciliationRetryCount = reconciliationRetryCount; }

    public Instant getNextReconciliationAt() { return nextReconciliationAt; }
    public void setNextReconciliationAt(Instant nextReconciliationAt) { this.nextReconciliationAt = nextReconciliationAt; }

    public boolean isManualReviewRequired() { return manualReviewRequired; }
    public void setManualReviewRequired(boolean manualReviewRequired) { this.manualReviewRequired = manualReviewRequired; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public String getProviderOrderId() { return providerOrderId; }
    public void setProviderOrderId(String providerOrderId) { this.providerOrderId = providerOrderId; }

    public String getProviderTransactionId() { return providerTransactionId; }
    public void setProviderTransactionId(String providerTransactionId) { this.providerTransactionId = providerTransactionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }

    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }

    public String getClientRedirectUrl() { return clientRedirectUrl; }
    public void setClientRedirectUrl(String clientRedirectUrl) { this.clientRedirectUrl = clientRedirectUrl; }

    public String getRawProviderResponse() { return rawProviderResponse; }
    public void setRawProviderResponse(String rawProviderResponse) { this.rawProviderResponse = rawProviderResponse; }
}
