package com.dineflow.promotion.dto;

import java.math.BigDecimal;

public record PromotionAnalyticsResponse(
        long totalPromotions,
        long activePromotions,
        long expiredPromotions,
        long totalRedemptions,
        BigDecimal totalDiscountGranted,
        String currencyCode
) {}
