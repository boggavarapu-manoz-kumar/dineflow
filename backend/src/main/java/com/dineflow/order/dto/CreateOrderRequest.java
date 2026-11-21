package com.dineflow.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @Size(max = 512, message = "Special notes must not exceed 512 characters")
        String specialNotes,

        @DecimalMin(value = "0.0", inclusive = false, message = "Expected total must be positive")
        BigDecimal expectedGrandTotal
) {}
