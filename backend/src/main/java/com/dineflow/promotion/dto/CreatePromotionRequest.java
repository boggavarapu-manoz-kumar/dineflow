package com.dineflow.promotion.dto;

import com.dineflow.promotion.domain.DiscountType;
import com.dineflow.promotion.domain.PromotionScope;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreatePromotionRequest(
        @NotBlank(message = "Promotion name is required")
        String name,
        String description,
        UUID branchId,
        String couponCode,
        @NotNull(message = "Discount type is required")
        DiscountType discountType,
        @NotNull(message = "Discount value is required")
        @DecimalMin(value = "0.01", message = "Discount value must be greater than zero")
        BigDecimal discountValue,
        PromotionScope scope,
        BigDecimal minOrderValue,
        BigDecimal maxDiscountAmount,
        @NotNull(message = "Start timestamp (UTC) is required")
        Instant startsAt,
        @NotNull(message = "End timestamp (UTC) is required")
        Instant endsAt,
        Integer usageLimit,
        Integer perCustomerLimit,
        Boolean applyBeforeTax,
        Boolean combinable,
        Boolean active,
        Set<UUID> eligibleItemIds,
        Set<UUID> eligibleCategoryIds
) {}
