package com.dineflow.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateVariantRequest(
        @NotBlank(message = "Variant name is required")
        @Size(min = 1, max = 64, message = "Variant name must be between 1 and 64 characters")
        String name,

        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String description,

        @NotNull(message = "Variant price is required")
        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        BigDecimal price,

        Integer displayOrder,

        Boolean isActive,

        Boolean isAvailable
) {}
