package com.dineflow.menu.repository;

import com.dineflow.menu.domain.MenuItemImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuItemImageRepository extends JpaRepository<MenuItemImage, UUID> {
    List<MenuItemImage> findAllByMenuItemIdAndTenantIdOrderByDisplayOrderAsc(UUID menuItemId, UUID tenantId);
    Optional<MenuItemImage> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<MenuItemImage> findByStorageKeyAndTenantId(String storageKey, UUID tenantId);
}
