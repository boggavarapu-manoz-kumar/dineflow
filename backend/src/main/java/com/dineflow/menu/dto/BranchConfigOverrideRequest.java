package com.dineflow.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record BranchConfigOverrideRequest(
        @DecimalMin(value = "0.00", message = "Price override cannot be negative")
        BigDecimal priceOverride,

        @NotNull(message = "Availability status is required")
        Boolean isAvailable,

        Boolean isVisible
) {}
