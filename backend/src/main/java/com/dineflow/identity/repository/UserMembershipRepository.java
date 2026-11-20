package com.dineflow.identity.repository;

import com.dineflow.identity.domain.UserMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserMembershipRepository extends JpaRepository<UserMembership, UUID> {
    List<UserMembership> findByUserId(UUID userId);
    Optional<UserMembership> findByUserIdAndTenantId(UUID userId, UUID tenantId);
    Optional<UserMembership> findByUserIdAndTenantIdIsNull(UUID userId);
}