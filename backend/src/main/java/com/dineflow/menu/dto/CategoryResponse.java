package com.dineflow.menu.dto;

import com.dineflow.menu.domain.MenuCategory;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        Integer displayOrder,
        Boolean isActive,
        String iconName,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static CategoryResponse fromEntity(MenuCategory entity) {
        return new CategoryResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getDescription(),
                entity.getDisplayOrder(),
                entity.getIsActive(),
                entity.getIconName(),
                entity.getImageUrl(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
