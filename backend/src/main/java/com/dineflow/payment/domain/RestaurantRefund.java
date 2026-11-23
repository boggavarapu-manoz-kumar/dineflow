package com.dineflow.payment.domain;

import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "restaurant_refunds",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tenant_refund_ref", columnNames = {"tenant_id", "refund_reference"})
        }
)
public class RestaurantRefund extends TenantAwareEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private RestaurantPayment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private RestaurantBill bill;

    @Column(name = "refund_reference", nullable = false, length = 64)
    private String refundReference;

    @Column(name = "refund_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", nullable = false, length = 32)
    private RefundMethod refundMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 32)
    private RefundStatus refundStatus = RefundStatus.PENDING_APPROVAL;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    @Column(name = "provider_refund_id", length = 128)
    private String providerRefundId;

    @Column(name = "failure_message", length = 512)
    private String failureMessage;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "reconciliation_retry_count", nullable = false)
    private int reconciliationRetryCount = 0;

    @Column(name = "next_reconciliation_at")
    private Instant nextReconciliationAt;

    @Column(name = "manual_review_required", nullable = false)
    private boolean manualReviewRequired = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public RestaurantRefund() {}

    public RestaurantRefund(UUID tenantId, UUID branchId, RestaurantPayment payment, RestaurantBill bill,
                            String refundReference, BigDecimal refundAmount, String currencyCode,
                            RefundMethod refundMethod, String reason, UUID requestedByUserId) {
        setTenantId(tenantId);
        this.branchId = branchId;
        this.payment = payment;
        this.bill = bill;
        this.refundReference = refundReference;
        this.refundAmount = refundAmount;
        this.currencyCode = currencyCode;
        this.refundMethod = refundMethod;
        this.reason = reason;
        this.requestedByUserId = requestedByUserId;
        this.refundStatus = RefundStatus.PENDING_APPROVAL;
    }

    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public RestaurantPayment getPayment() { return payment; }
    public void setPayment(RestaurantPayment payment) { this.payment = payment; }
    public RestaurantBill getBill() { return bill; }
    public void setBill(RestaurantBill bill) { this.bill = bill; }
    public String getRefundReference() { return refundReference; }
    public void setRefundReference(String refundReference) { this.refundReference = refundReference; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public RefundMethod getRefundMethod() { return refundMethod; }
    public void setRefundMethod(RefundMethod refundMethod) { this.refundMethod = refundMethod; }
    public RefundStatus getRefundStatus() { return refundStatus; }
    public void setRefundStatus(RefundStatus refundStatus) { this.refundStatus = refundStatus; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getProviderRefundId() { return providerRefundId; }
    public void setProviderRefundId(String providerRefundId) { this.providerRefundId = providerRefundId; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(UUID requestedByUserId) { this.requestedByUserId = requestedByUserId; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public void setApprovedByUserId(UUID approvedByUserId) { this.approvedByUserId = approvedByUserId; }
    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }
    public int getReconciliationRetryCount() { return reconciliationRetryCount; }
    public void setReconciliationRetryCount(int reconciliationRetryCount) { this.reconciliationRetryCount = reconciliationRetryCount; }
    public Instant getNextReconciliationAt() { return nextReconciliationAt; }
    public void setNextReconciliationAt(Instant nextReconciliationAt) { this.nextReconciliationAt = nextReconciliationAt; }
    public boolean isManualReviewRequired() { return manualReviewRequired; }
    public void setManualReviewRequired(boolean manualReviewRequired) { this.manualReviewRequired = manualReviewRequired; }
    public Long getVersion() { return version; }
}
