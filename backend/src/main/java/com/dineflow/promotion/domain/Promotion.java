package com.dineflow.promotion.domain;

import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "promotions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tenant_coupon_code", columnNames = {"tenant_id", "coupon_code"})
        }
)
public class Promotion extends TenantAwareEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "coupon_code", length = 64)
    private String couponCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 32)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    private PromotionScope scope = PromotionScope.ORDER_LEVEL;

    @Column(name = "min_order_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal minOrderValue = BigDecimal.ZERO;

    @Column(name = "max_discount_amount", precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "current_usage_count", nullable = false)
    private Integer currentUsageCount = 0;

    @Column(name = "per_customer_limit")
    private Integer perCustomerLimit;

    @Column(name = "apply_before_tax", nullable = false)
    private boolean applyBeforeTax = true;

    @Column(name = "combinable", nullable = false)
    private boolean combinable = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "promotion_eligible_items", joinColumns = @JoinColumn(name = "promotion_id"))
    @Column(name = "menu_item_id")
    private Set<UUID> eligibleItemIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "promotion_eligible_categories", joinColumns = @JoinColumn(name = "promotion_id"))
    @Column(name = "category_id")
    private Set<UUID> eligibleCategoryIds = new HashSet<>();

    public Promotion() {}

    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public PromotionScope getScope() { return scope; }
    public void setScope(PromotionScope scope) { this.scope = scope; }
    public BigDecimal getMinOrderValue() { return minOrderValue; }
    public void setMinOrderValue(BigDecimal minOrderValue) { this.minOrderValue = minOrderValue; }
    public BigDecimal getMaxDiscountAmount() { return maxDiscountAmount; }
    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) { this.maxDiscountAmount = maxDiscountAmount; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }
    public Integer getCurrentUsageCount() { return currentUsageCount; }
    public void setCurrentUsageCount(Integer currentUsageCount) { this.currentUsageCount = currentUsageCount; }
    public Integer getPerCustomerLimit() { return perCustomerLimit; }
    public void setPerCustomerLimit(Integer perCustomerLimit) { this.perCustomerLimit = perCustomerLimit; }
    public boolean isApplyBeforeTax() { return applyBeforeTax; }
    public void setApplyBeforeTax(boolean applyBeforeTax) { this.applyBeforeTax = applyBeforeTax; }
    public boolean isCombinable() { return combinable; }
    public void setCombinable(boolean combinable) { this.combinable = combinable; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }
    public Long getVersion() { return version; }
    public Set<UUID> getEligibleItemIds() { return eligibleItemIds; }
    public void setEligibleItemIds(Set<UUID> eligibleItemIds) {
        this.eligibleItemIds = eligibleItemIds != null ? new HashSet<>(eligibleItemIds) : new HashSet<>();
    }
    public Set<UUID> getEligibleCategoryIds() { return eligibleCategoryIds; }
    public void setEligibleCategoryIds(Set<UUID> eligibleCategoryIds) {
        this.eligibleCategoryIds = eligibleCategoryIds != null ? new HashSet<>(eligibleCategoryIds) : new HashSet<>();
    }
}
