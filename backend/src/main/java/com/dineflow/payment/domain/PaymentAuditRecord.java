package com.dineflow.payment.domain;

import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payment_audit_records")
public class PaymentAuditRecord extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private RestaurantPayment payment;

    @Column(name = "previous_status", length = 32)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 32)
    private String newStatus;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType = "SYSTEM";

    @Column(name = "amount_delta", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountDelta = BigDecimal.ZERO;

    @Column(name = "audit_payload", columnDefinition = "TEXT")
    private String auditPayload;

    public PaymentAuditRecord() {
    }

    public PaymentAuditRecord(RestaurantPayment payment, UUID tenantId, String previousStatus, String newStatus,
                              String eventType, UUID actorId, String actorType, BigDecimal amountDelta, String auditPayload) {
        this.payment = payment;
        setTenantId(tenantId);
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.eventType = eventType;
        this.actorId = actorId;
        this.actorType = actorType != null ? actorType : "SYSTEM";
        this.amountDelta = amountDelta != null ? amountDelta : BigDecimal.ZERO;
        this.auditPayload = auditPayload;
    }

    // Getters and Setters
    public RestaurantPayment getPayment() { return payment; }
    public void setPayment(RestaurantPayment payment) { this.payment = payment; }

    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public BigDecimal getAmountDelta() { return amountDelta; }
    public void setAmountDelta(BigDecimal amountDelta) { this.amountDelta = amountDelta; }

    public String getAuditPayload() { return auditPayload; }
    public void setAuditPayload(String auditPayload) { this.auditPayload = auditPayload; }
}
