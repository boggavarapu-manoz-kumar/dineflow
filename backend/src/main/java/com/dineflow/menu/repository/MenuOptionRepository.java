package com.dineflow.menu.repository;

import com.dineflow.menu.domain.MenuOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuOptionRepository extends JpaRepository<MenuOption, UUID> {
    List<MenuOption> findAllByOptionGroupIdAndTenantIdOrderByDisplayOrderAsc(UUID optionGroupId, UUID tenantId);
    Optional<MenuOption> findByIdAndTenantId(UUID id, UUID tenantId);
}
