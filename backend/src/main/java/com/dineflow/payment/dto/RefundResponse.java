package com.dineflow.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID tenantId,
        UUID branchId,
        UUID paymentId,
        UUID billId,
        String refundReference,
        BigDecimal refundAmount,
        String currencyCode,
        String refundMethod,
        String refundStatus,
        String reason,
        String providerRefundId,
        String failureMessage,
        UUID requestedByUserId,
        UUID approvedByUserId,
        Instant settledAt,
        Instant createdAt
) {}
