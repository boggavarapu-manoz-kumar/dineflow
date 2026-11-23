package com.dineflow.promotion.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PromotionResponse(
        UUID id,
        UUID tenantId,
        UUID branchId,
        String name,
        String description,
        String couponCode,
        String discountType,
        BigDecimal discountValue,
        String scope,
        BigDecimal minOrderValue,
        BigDecimal maxDiscountAmount,
        Instant startsAt,
        Instant endsAt,
        Integer usageLimit,
        Integer currentUsageCount,
        Integer perCustomerLimit,
        boolean applyBeforeTax,
        boolean combinable,
        boolean active,
        boolean expired,
        Set<UUID> eligibleItemIds,
        Set<UUID> eligibleCategoryIds,
        Instant createdAt
) {}
