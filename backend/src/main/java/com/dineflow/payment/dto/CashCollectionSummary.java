package com.dineflow.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashCollectionSummary(
        UUID tenantId,
        UUID branchId,
        UUID staffUserId,
        String staffName,
        Instant periodStart,
        Instant periodEnd,
        int totalTransactions,
        BigDecimal totalCashReceived,
        BigDecimal totalReversed,
        BigDecimal netCashCollected,
        String currencyCode
) {}
