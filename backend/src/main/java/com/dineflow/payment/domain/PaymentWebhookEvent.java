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
@Table(name = "payment_webhook_events")
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_name", nullable = false, length = 32)
    private String providerName;

    @Column(name = "provider_event_id", nullable = false, length = 128)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "RECEIVED"; // RECEIVED, PROCESSED, IGNORED, FAILED

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "payment_reference", length = 64)
    private String paymentReference;

    @Column(name = "provider_payment_id", length = 128)
    private String providerPaymentId;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PaymentWebhookEvent() {}

    public PaymentWebhookEvent(String providerName, String providerEventId, String eventType, String payloadHash, String rawPayload) {
        this.providerName = providerName;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.payloadHash = payloadHash;
        this.rawPayload = rawPayload;
        this.status = "RECEIVED";
        this.retryCount = 0;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public String getProviderEventId() { return providerEventId; }
    public void setProviderEventId(String providerEventId) { this.providerEventId = providerEventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public void incrementRetryCount() { this.retryCount++; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
