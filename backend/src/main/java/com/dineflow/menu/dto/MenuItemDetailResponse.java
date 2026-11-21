package com.dineflow.menu.dto;

import com.dineflow.menu.domain.DietaryClassification;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.domain.MenuItemImage;
import com.dineflow.menu.domain.MenuOption;
import com.dineflow.menu.domain.MenuOptionGroup;
import com.dineflow.menu.domain.MenuVariant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MenuItemDetailResponse(
        UUID id,
        UUID tenantId,
        UUID categoryId,
        String categoryName,
        String name,
        String shortDescription,
        String detailedDescription,
        BigDecimal basePrice,
        DietaryClassification dietaryClassification,
        Integer preparationTimeMinutes,
        Boolean isActive,
        Boolean isAvailable,
        Integer displayOrder,
        List<ImageResponse> images,
        List<VariantResponse> variants,
        List<OptionGroupResponse> optionGroups,
        Instant createdAt,
        Instant updatedAt
) {
    public static MenuItemDetailResponse fromEntity(MenuItem entity) {
        return new MenuItemDetailResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getCategory() != null ? entity.getCategory().getId() : null,
                entity.getCategory() != null ? entity.getCategory().getName() : null,
                entity.getName(),
                entity.getShortDescription(),
                entity.getDetailedDescription(),
                entity.getBasePrice(),
                entity.getDietaryClassification(),
                entity.getPreparationTimeMinutes(),
                entity.getIsActive(),
                entity.getIsAvailable(),
                entity.getDisplayOrder(),
                entity.getImages() != null ? entity.getImages().stream().map(ImageResponse::fromEntity).toList() : List.of(),
                entity.getVariants() != null ? entity.getVariants().stream().map(VariantResponse::fromEntity).toList() : List.of(),
                entity.getOptionGroups() != null ? entity.getOptionGroups().stream().map(OptionGroupResponse::fromEntity).toList() : List.of(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public record ImageResponse(
            UUID id,
            String imageUrl,
            String storageKey,
            String fileName,
            Long fileSizeBytes,
            String contentType,
            Integer displayOrder,
            Boolean isPrimary
    ) {
        public static ImageResponse fromEntity(MenuItemImage img) {
            return new ImageResponse(
                    img.getId(),
                    img.getImageUrl(),
                    img.getStorageKey(),
                    img.getFileName(),
                    img.getFileSizeBytes(),
                    img.getContentType(),
                    img.getDisplayOrder(),
                    img.getIsPrimary()
            );
        }
    }

    public record VariantResponse(
            UUID id,
            String name,
            String description,
            BigDecimal price,
            Integer displayOrder,
            Boolean isActive,
            Boolean isAvailable
    ) {
        public static VariantResponse fromEntity(MenuVariant v) {
            return new VariantResponse(
                    v.getId(),
                    v.getName(),
                    v.getDescription(),
                    v.getPrice(),
                    v.getDisplayOrder(),
                    v.getIsActive(),
                    v.getIsAvailable()
            );
        }
    }

    public record OptionGroupResponse(
            UUID id,
            String name,
            String description,
            Integer minSelections,
            Integer maxSelections,
            Boolean isRequired,
            Integer displayOrder,
            Boolean isActive,
            List<OptionResponse> options
    ) {
        public static OptionGroupResponse fromEntity(MenuOptionGroup group) {
            return new OptionGroupResponse(
                    group.getId(),
                    group.getName(),
                    group.getDescription(),
                    group.getMinSelections(),
                    group.getMaxSelections(),
                    group.getIsRequired(),
                    group.getDisplayOrder(),
                    group.getIsActive(),
                    group.getOptions() != null ? group.getOptions().stream().map(OptionResponse::fromEntity).toList() : List.of()
            );
        }
    }

    public record OptionResponse(
            UUID id,
            String name,
            String description,
            BigDecimal additionalPrice,
            Integer displayOrder,
            Boolean isActive,
            Boolean isAvailable
    ) {
        public static OptionResponse fromEntity(MenuOption opt) {
            return new OptionResponse(
                    opt.getId(),
                    opt.getName(),
                    opt.getDescription(),
                    opt.getAdditionalPrice(),
                    opt.getDisplayOrder(),
                    opt.getIsActive(),
                    opt.getIsAvailable()
            );
        }
    }
}
