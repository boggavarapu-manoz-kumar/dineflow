package com.dineflow.promotion.dto;

import com.dineflow.promotion.domain.DiscountType;
import com.dineflow.promotion.domain.PromotionScope;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UpdatePromotionRequest(
        String name,
        String description,
        UUID branchId,
        DiscountType discountType,
        @DecimalMin(value = "0.01", message = "Discount value must be greater than zero")
        BigDecimal discountValue,
        PromotionScope scope,
        BigDecimal minOrderValue,
        BigDecimal maxDiscountAmount,
        Instant startsAt,
        Instant endsAt,
        Integer usageLimit,
        Integer perCustomerLimit,
        Boolean applyBeforeTax,
        Boolean combinable,
        Boolean active,
        Set<UUID> eligibleItemIds,
        Set<UUID> eligibleCategoryIds
) {}
