package com.dineflow.promotion.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Customer-safe response for coupon validation, application, and removal.
 * Never exposes internal usage limits, internal notes, or other tenants' promotions.
 */
public record CouponApplicationResponse(
        boolean valid,
        String message,
        UUID billId,
        String couponCode,
        String promotionName,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal serviceChargeAmount,
        BigDecimal taxAmount,
        BigDecimal grandTotal,
        BigDecimal outstandingBalance,
        String currencyCode,
        boolean appliedBeforeTax
) {}
