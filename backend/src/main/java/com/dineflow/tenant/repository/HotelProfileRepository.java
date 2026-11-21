package com.dineflow.tenant.repository;

import com.dineflow.tenant.domain.HotelProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HotelProfileRepository extends JpaRepository<HotelProfile, UUID> {
    Optional<HotelProfile> findByTenantId(UUID tenantId);
}
