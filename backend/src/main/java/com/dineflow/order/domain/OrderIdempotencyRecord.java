package com.dineflow.order.domain;

import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "order_idempotency_records",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_session_idempotency_key", columnNames = {"customer_session_id", "idempotency_key"})
        }
)
public class OrderIdempotencyRecord extends TenantAwareEntity {

    @Column(name = "customer_session_id", nullable = false)
    private UUID customerSessionId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "IN_PROGRESS"; // IN_PROGRESS, COMPLETED, FAILED

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public OrderIdempotencyRecord() {
    }

    public OrderIdempotencyRecord(UUID tenantId, UUID customerSessionId, String idempotencyKey, String requestHash, Instant expiresAt) {
        setTenantId(tenantId);
        this.customerSessionId = customerSessionId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.expiresAt = expiresAt;
        this.status = "IN_PROGRESS";
    }

    public UUID getCustomerSessionId() {
        return customerSessionId;
    }

    public void setCustomerSessionId(UUID customerSessionId) {
        this.customerSessionId = customerSessionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
