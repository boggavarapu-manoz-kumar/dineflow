package com.dineflow.identity.repository;

import com.dineflow.identity.domain.RefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    Optional<RefreshSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshSession r SET r.revoked = true WHERE r.familyId = :familyId")
    void revokeFamily(UUID familyId);

    @Modifying
    @Query("UPDATE RefreshSession r SET r.revoked = true WHERE r.user.id = :userId")
    void revokeAllForUser(UUID userId);
}