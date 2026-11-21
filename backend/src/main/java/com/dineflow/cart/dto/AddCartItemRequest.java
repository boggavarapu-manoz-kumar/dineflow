package com.dineflow.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AddCartItemRequest(
        @NotNull(message = "Menu item ID is required")
        UUID menuItemId,

        UUID variantId,

        List<UUID> optionIds,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 50, message = "Quantity cannot exceed 50")
        Integer quantity,

        @Size(max = 255, message = "Special instructions cannot exceed 255 characters")
        String specialInstructions
) {}
