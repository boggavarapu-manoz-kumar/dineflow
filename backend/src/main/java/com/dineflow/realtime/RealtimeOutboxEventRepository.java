package com.dineflow.realtime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RealtimeOutboxEventRepository extends JpaRepository<RealtimeOutboxEvent, UUID> {

    Optional<RealtimeOutboxEvent> findByEventId(UUID eventId);

    @Query("""
            SELECT e FROM RealtimeOutboxEvent e
            WHERE e.status = 'PENDING'
              AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)
            ORDER BY e.createdAt ASC
            """)
    Page<RealtimeOutboxEvent> findDueForPublication(@Param("now") Instant now, Pageable pageable);

    long countByStatus(String status);

    @Modifying
    @Query("DELETE FROM RealtimeOutboxEvent e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
