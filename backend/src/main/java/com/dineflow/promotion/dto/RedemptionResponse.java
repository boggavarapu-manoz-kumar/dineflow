package com.dineflow.promotion.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RedemptionResponse(
        UUID id,
        UUID promotionId,
        UUID billId,
        String billNumber,
        UUID branchId,
        UUID customerSessionId,
        String couponCode,
        String promotionName,
        BigDecimal discountAmount,
        String currencyCode,
        String status,
        String actorType,
        Instant redeemedAt,
        Instant removedAt
) {}
