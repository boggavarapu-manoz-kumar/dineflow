package com.dineflow.menu.repository;

import com.dineflow.menu.domain.MenuVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuVariantRepository extends JpaRepository<MenuVariant, UUID> {
    List<MenuVariant> findAllByMenuItemIdAndTenantIdOrderByDisplayOrderAsc(UUID menuItemId, UUID tenantId);
    Optional<MenuVariant> findByIdAndTenantId(UUID id, UUID tenantId);
}
