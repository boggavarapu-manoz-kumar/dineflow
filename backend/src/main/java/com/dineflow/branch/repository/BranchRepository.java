package com.dineflow.branch.repository;

import com.dineflow.branch.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {
    List<Branch> findAllByTenantId(UUID tenantId);
    Optional<Branch> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Branch> findByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
    Optional<Branch> findByTenantIdAndIsMainTrue(UUID tenantId);
}
