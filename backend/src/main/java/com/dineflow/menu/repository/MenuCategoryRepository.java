package com.dineflow.menu.repository;

import com.dineflow.menu.domain.MenuCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, UUID> {

    List<MenuCategory> findAllByTenantIdOrderByDisplayOrderAsc(UUID tenantId);

    List<MenuCategory> findAllByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID tenantId);

    Page<MenuCategory> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<MenuCategory> findAllByTenantIdAndIsActive(UUID tenantId, Boolean isActive, Pageable pageable);

    Optional<MenuCategory> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT mc FROM MenuCategory mc WHERE mc.tenantId = :tenantId AND mc.normalized_name = :normalizedName")
    Optional<MenuCategory> findByTenantIdAndNormalizedName(
            @Param("tenantId") UUID tenantId,
            @Param("normalizedName") String normalizedName
    );

    @Query("SELECT CASE WHEN COUNT(mc) > 0 THEN true ELSE false END FROM MenuCategory mc " +
           "WHERE mc.tenantId = :tenantId AND mc.normalized_name = :normalizedName AND mc.id != :excludeId")
    boolean existsByTenantIdAndNormalizedNameExcludingId(
            @Param("tenantId") UUID tenantId,
            @Param("normalizedName") String normalizedName,
            @Param("excludeId") UUID excludeId
    );
}
