package com.dineflow.menu.repository;

import com.dineflow.menu.domain.MenuItem;
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
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    Optional<MenuItem> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT mi FROM MenuItem mi " +
           "LEFT JOIN FETCH mi.category " +
           "LEFT JOIN FETCH mi.variants " +
           "LEFT JOIN FETCH mi.images " +
           "WHERE mi.id = :id AND mi.tenantId = :tenantId")
    Optional<MenuItem> findByIdAndTenantIdWithDetails(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    List<MenuItem> findAllByTenantIdOrderByDisplayOrderAsc(UUID tenantId);

    List<MenuItem> findAllByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID tenantId);

    List<MenuItem> findAllByTenantIdAndCategoryIdOrderByDisplayOrderAsc(UUID tenantId, UUID categoryId);

    Page<MenuItem> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<MenuItem> findAllByTenantIdAndCategoryId(UUID tenantId, UUID categoryId, Pageable pageable);

    @Query("SELECT mi FROM MenuItem mi WHERE mi.tenantId = :tenantId AND mi.normalizedName = :normalizedName")
    Optional<MenuItem> findByTenantIdAndNormalizedName(@Param("tenantId") UUID tenantId, @Param("normalizedName") String normalizedName);

    @Query("SELECT CASE WHEN COUNT(mi) > 0 THEN true ELSE false END FROM MenuItem mi " +
           "WHERE mi.tenantId = :tenantId AND mi.normalizedName = :normalizedName AND mi.id != :excludeId")
    boolean existsByTenantIdAndNormalizedNameExcludingId(
            @Param("tenantId") UUID tenantId,
            @Param("normalizedName") String normalizedName,
            @Param("excludeId") UUID excludeId
    );

    long countByCategoryIdAndTenantId(UUID categoryId, UUID tenantId);
}
