package com.dineflow.menu.repository;

import com.dineflow.menu.domain.MenuItemBranchConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuItemBranchConfigurationRepository extends JpaRepository<MenuItemBranchConfiguration, UUID> {
    Optional<MenuItemBranchConfiguration> findByBranchIdAndMenuItemIdAndTenantId(UUID branchId, UUID menuItemId, UUID tenantId);
    List<MenuItemBranchConfiguration> findAllByBranchIdAndTenantId(UUID branchId, UUID tenantId);
    List<MenuItemBranchConfiguration> findAllByMenuItemIdAndTenantId(UUID menuItemId, UUID tenantId);
}
