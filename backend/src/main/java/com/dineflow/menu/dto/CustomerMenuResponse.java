package com.dineflow.menu.dto;

import com.dineflow.menu.domain.DietaryClassification;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CustomerMenuResponse(
        UUID tenantId,
        String restaurantName,
        String tableNumber,
        String currencyCode,
        BigDecimal taxPercentage,
        BigDecimal serviceChargePercentage,
        List<CustomerCategoryResponse> categories
) {
    public record CustomerCategoryResponse(
            UUID id,
            String name,
            String description,
            Integer displayOrder,
            String iconName,
            List<CustomerMenuItemResponse> items
    ) {}

    public record CustomerMenuItemResponse(
            UUID id,
            String name,
            String shortDescription,
            String detailedDescription,
            BigDecimal effectivePrice,
            BigDecimal originalBasePrice,
            boolean isBranchPriceOverridden,
            DietaryClassification dietaryClassification,
            Integer preparationTimeMinutes,
            boolean isAvailable,
            String primaryImageUrl,
            List<String> imageUrls,
            List<CustomerVariantResponse> variants,
            List<CustomerOptionGroupResponse> optionGroups
    ) {}

    public record CustomerVariantResponse(
            UUID id,
            String name,
            String description,
            BigDecimal price,
            boolean isAvailable
    ) {}

    public record CustomerOptionGroupResponse(
            UUID id,
            String name,
            String description,
            int minSelections,
            int maxSelections,
            boolean isRequired,
            List<CustomerOptionResponse> options
    ) {}

    public record CustomerOptionResponse(
            UUID id,
            String name,
            String description,
            BigDecimal additionalPrice,
            boolean isAvailable
    ) {}
}
