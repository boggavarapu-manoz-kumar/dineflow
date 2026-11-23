package com.dineflow.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPaymentRequest(
        @NotBlank(message = "Provider transaction ID is required")
        String providerTransactionId,
        String providerOrderId,
        String signature
) {}
