package com.dineflow.payment.domain;

import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "restaurant_payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tenant_payment_ref", columnNames = {"tenant_id", "payment_reference"})
        }
)
public class RestaurantPayment extends TenantAwareEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private RestaurantBill bill;

    @Column(name = "payment_reference", nullable = false, length = 64)
    private String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    private PaymentStatus paymentStatus = PaymentStatus.CREATED;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "payable_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal payableAmount;

    @Column(name = "paid_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", nullable = false, length = 32)
    private PaymentProviderType paymentProvider = PaymentProviderType.INTERNAL;

    @Column(name = "provider_payment_id", length = 128)
    private String providerPaymentId;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "collected_by_user_id")
    private UUID collectedByUserId;

    @Column(name = "settled_at")
    private Instant settledAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("attemptNumber ASC")
    private List<PaymentAttempt> attempts = new ArrayList<>();

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<PaymentAuditRecord> auditRecords = new ArrayList<>();

    public RestaurantPayment() {
    }

    public RestaurantPayment(UUID tenantId, UUID branchId, RestaurantBill bill, String paymentReference,
                             PaymentMethod paymentMethod, BigDecimal payableAmount, String currencyCode,
                             PaymentProviderType paymentProvider) {
        setTenantId(tenantId);
        this.branchId = branchId;
        this.bill = bill;
        this.paymentReference = paymentReference;
        this.paymentMethod = paymentMethod;
        this.payableAmount = payableAmount;
        this.currencyCode = currencyCode != null ? currencyCode : "INR";
        this.paymentProvider = paymentProvider != null ? paymentProvider : PaymentProviderType.INTERNAL;
        this.paymentStatus = PaymentStatus.CREATED;
    }

    public void addAttempt(PaymentAttempt attempt) {
        attempts.add(attempt);
        attempt.setPayment(this);
    }

    public void addAuditRecord(PaymentAuditRecord record) {
        auditRecords.add(record);
        record.setPayment(this);
    }

    // Getters and Setters
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }

    public RestaurantBill getBill() { return bill; }
    public void setBill(RestaurantBill bill) { this.bill = bill; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public BigDecimal getPayableAmount() { return payableAmount; }
    public void setPayableAmount(BigDecimal payableAmount) { this.payableAmount = payableAmount; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }

    public PaymentProviderType getPaymentProvider() { return paymentProvider; }
    public void setPaymentProvider(PaymentProviderType paymentProvider) { this.paymentProvider = paymentProvider; }

    public String getProviderPaymentId() { return providerPaymentId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public UUID getCollectedByUserId() { return collectedByUserId; }
    public void setCollectedByUserId(UUID collectedByUserId) { this.collectedByUserId = collectedByUserId; }

    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }

    public List<PaymentAttempt> getAttempts() { return attempts; }
    public void setAttempts(List<PaymentAttempt> attempts) { this.attempts = attempts; }

    public List<PaymentAuditRecord> getAuditRecords() { return auditRecords; }
    public void setAuditRecords(List<PaymentAuditRecord> auditRecords) { this.auditRecords = auditRecords; }
}
