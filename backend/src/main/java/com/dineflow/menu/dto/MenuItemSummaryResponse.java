package com.dineflow.menu.dto;

import com.dineflow.menu.domain.DietaryClassification;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.domain.MenuItemImage;

import java.math.BigDecimal;
import java.util.UUID;

public record MenuItemSummaryResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String name,
        String shortDescription,
        BigDecimal basePrice,
        DietaryClassification dietaryClassification,
        Boolean isActive,
        Boolean isAvailable,
        Integer displayOrder,
        String primaryImageUrl
) {
    public static MenuItemSummaryResponse fromEntity(MenuItem item) {
        String primaryImg = null;
        if (item.getImages() != null && !item.getImages().isEmpty()) {
            primaryImg = item.getImages().stream()
                    .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                    .map(MenuItemImage::getImageUrl)
                    .findFirst()
                    .orElseGet(() -> item.getImages().iterator().next().getImageUrl());
        }

        return new MenuItemSummaryResponse(
                item.getId(),
                item.getCategory() != null ? item.getCategory().getId() : null,
                item.getCategory() != null ? item.getCategory().getName() : null,
                item.getName(),
                item.getShortDescription(),
                item.getBasePrice(),
                item.getDietaryClassification(),
                item.getIsActive(),
                item.getIsAvailable(),
                item.getDisplayOrder(),
                primaryImg
        );
    }
}
