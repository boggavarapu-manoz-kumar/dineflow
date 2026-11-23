package com.dineflow.payment.dto;

import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.PaymentProviderType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record InitiatePaymentRequest(
        @NotNull(message = "Bill ID is required")
        UUID billId,
        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,
        PaymentProviderType providerType,
        BigDecimal amount,
        String notes,
        String returnUrl,
        String idempotencyKey
) {
    public InitiatePaymentRequest(UUID billId,
                                  PaymentMethod paymentMethod,
                                  PaymentProviderType providerType,
                                  BigDecimal amount,
                                  String notes,
                                  String returnUrl) {
        this(billId, paymentMethod, providerType, amount, notes, returnUrl, null);
    }
}
