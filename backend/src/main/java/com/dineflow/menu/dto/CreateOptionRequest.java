package com.dineflow.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateOptionRequest(
        @NotBlank(message = "Option name is required")
        @Size(min = 1, max = 128, message = "Option name must be between 1 and 128 characters")
        String name,

        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String description,

        @NotNull(message = "Additional price is required")
        @DecimalMin(value = "0.00", message = "Additional price cannot be negative")
        BigDecimal additionalPrice,

        Integer displayOrder,

        Boolean isActive,

        Boolean isAvailable
) {}
