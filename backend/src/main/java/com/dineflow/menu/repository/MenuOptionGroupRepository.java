package com.dineflow.menu.repository;

import com.dineflow.menu.domain.MenuOptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuOptionGroupRepository extends JpaRepository<MenuOptionGroup, UUID> {
    List<MenuOptionGroup> findAllByTenantIdOrderByDisplayOrderAsc(UUID tenantId);
    Optional<MenuOptionGroup> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT og FROM MenuOptionGroup og LEFT JOIN FETCH og.options WHERE og.id = :id AND og.tenantId = :tenantId")
    Optional<MenuOptionGroup> findByIdAndTenantIdWithOptions(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
