package com.dineflow.menu.dto;

import com.dineflow.menu.domain.MenuItemBranchConfiguration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BranchConfigResponse(
        UUID id,
        UUID branchId,
        String branchName,
        UUID menuItemId,
        String menuItemName,
        BigDecimal priceOverride,
        Boolean isAvailable,
        Boolean isVisible,
        Instant updatedAt
) {
    public static BranchConfigResponse fromEntity(MenuItemBranchConfiguration config) {
        return new BranchConfigResponse(
                config.getId(),
                config.getBranch() != null ? config.getBranch().getId() : null,
                config.getBranch() != null ? config.getBranch().getName() : null,
                config.getMenuItem() != null ? config.getMenuItem().getId() : null,
                config.getMenuItem() != null ? config.getMenuItem().getName() : null,
                config.getPriceOverride(),
                config.getIsAvailable(),
                config.getIsVisible(),
                config.getUpdatedAt()
        );
    }
}
