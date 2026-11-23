package com.dineflow.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record RecordCashPaymentRequest(
        @NotNull(message = "Bill ID is required")
        UUID billId,

        @NotNull(message = "Amount received is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amountReceived,

        String notes
) {}
