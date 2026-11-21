package com.dineflow.menu.dto;

import com.dineflow.menu.domain.DietaryClassification;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateMenuItemRequest(
        @NotNull(message = "Category ID is required")
        UUID categoryId,

        @NotBlank(message = "Item name is required")
        @Size(min = 2, max = 128, message = "Item name must be between 2 and 128 characters")
        String name,

        @Size(max = 255, message = "Short description cannot exceed 255 characters")
        String shortDescription,

        String detailedDescription,

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.00", message = "Base price cannot be negative")
        BigDecimal basePrice,

        DietaryClassification dietaryClassification,

        Integer preparationTimeMinutes,

        Boolean isActive,

        Boolean isAvailable,

        Integer displayOrder
) {}
