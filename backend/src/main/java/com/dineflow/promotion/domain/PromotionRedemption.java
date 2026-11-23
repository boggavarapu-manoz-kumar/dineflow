package com.dineflow.promotion.domain;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promotion_redemptions")
public class PromotionRedemption extends TenantAwareEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private RestaurantBill bill;

    @Column(name = "customer_session_id")
    private UUID customerSessionId;

    @Column(name = "coupon_code_snapshot", length = 64)
    private String couponCodeSnapshot;

    @Column(name = "promotion_name_snapshot", nullable = false, length = 128)
    private String promotionNameSnapshot;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RedemptionStatus status = RedemptionStatus.APPLIED;

    @Column(name = "applied_by_user_id")
    private UUID appliedByUserId;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType = "CUSTOMER";

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt = Instant.now();

    @Column(name = "removed_at")
    private Instant removedAt;

    public PromotionRedemption() {}

    public PromotionRedemption(UUID tenantId, UUID branchId, Promotion promotion, RestaurantBill bill,
                               UUID customerSessionId, BigDecimal discountAmount, String currencyCode,
                               UUID appliedByUserId, String actorType) {
        setTenantId(tenantId);
        this.branchId = branchId;
        this.promotion = promotion;
        this.bill = bill;
        this.customerSessionId = customerSessionId;
        this.couponCodeSnapshot = promotion.getCouponCode();
        this.promotionNameSnapshot = promotion.getName();
        this.discountAmount = discountAmount;
        this.currencyCode = currencyCode;
        this.appliedByUserId = appliedByUserId;
        this.actorType = actorType != null ? actorType : "CUSTOMER";
        this.status = RedemptionStatus.APPLIED;
        this.redeemedAt = Instant.now();
    }

    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public Promotion getPromotion() { return promotion; }
    public void setPromotion(Promotion promotion) { this.promotion = promotion; }
    public RestaurantBill getBill() { return bill; }
    public void setBill(RestaurantBill bill) { this.bill = bill; }
    public UUID getCustomerSessionId() { return customerSessionId; }
    public void setCustomerSessionId(UUID customerSessionId) { this.customerSessionId = customerSessionId; }
    public String getCouponCodeSnapshot() { return couponCodeSnapshot; }
    public void setCouponCodeSnapshot(String couponCodeSnapshot) { this.couponCodeSnapshot = couponCodeSnapshot; }
    public String getPromotionNameSnapshot() { return promotionNameSnapshot; }
    public void setPromotionNameSnapshot(String promotionNameSnapshot) { this.promotionNameSnapshot = promotionNameSnapshot; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public RedemptionStatus getStatus() { return status; }
    public void setStatus(RedemptionStatus status) { this.status = status; }
    public UUID getAppliedByUserId() { return appliedByUserId; }
    public void setAppliedByUserId(UUID appliedByUserId) { this.appliedByUserId = appliedByUserId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public Instant getRedeemedAt() { return redeemedAt; }
    public void setRedeemedAt(Instant redeemedAt) { this.redeemedAt = redeemedAt; }
    public Instant getRemovedAt() { return removedAt; }
    public void setRemovedAt(Instant removedAt) { this.removedAt = removedAt; }
}
