package com.dineflow.payment.dto;

import com.dineflow.payment.domain.RefundMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RequestRefundRequest(
        @NotNull(message = "Payment ID is required")
        UUID paymentId,

        @NotNull(message = "Refund amount is required")
        @DecimalMin(value = "0.01", message = "Refund amount must be greater than zero")
        BigDecimal amount,

        @NotNull(message = "Refund method is required")
        RefundMethod refundMethod,

        @NotBlank(message = "Mandatory refund reason is required")
        String reason
) {}
