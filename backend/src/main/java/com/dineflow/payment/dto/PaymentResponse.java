package com.dineflow.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID tenantId,
        UUID branchId,
        UUID billId,
        String paymentReference,
        String paymentMethod,
        String paymentStatus,
        String currencyCode,
        BigDecimal payableAmount,
        BigDecimal paidAmount,
        BigDecimal refundedAmount,
        String paymentProvider,
        String providerPaymentId,
        String providerOrderId,
        String clientSecretOrKeyId,
        String clientRedirectUrl,
        String notes,
        Instant settledAt,
        Instant createdAt
) {}
