package com.dineflow.identity.repository;

import com.dineflow.identity.domain.CustomerSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerSessionRepository extends JpaRepository<CustomerSession, UUID> {
    Optional<CustomerSession> findBySessionTokenHash(String tokenHash);
}