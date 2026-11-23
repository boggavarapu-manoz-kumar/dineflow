package com.dineflow.promotion.repository;

import com.dineflow.promotion.domain.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    Optional<Promotion> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Promotion> findByTenantIdAndCouponCodeIgnoreCase(UUID tenantId, String couponCode);

    boolean existsByTenantIdAndCouponCodeIgnoreCase(UUID tenantId, String couponCode);

    List<Promotion> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /**
     * Atomic concurrency-safe usage counter increment.
     * Returns 1 if incremented within usage limit, 0 if limit is exhausted or promotion inactive.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Promotion p
            SET p.currentUsageCount = p.currentUsageCount + 1
            WHERE p.id = :promotionId
              AND p.tenantId = :tenantId
              AND p.active = true
              AND (p.usageLimit IS NULL OR p.currentUsageCount < p.usageLimit)
            """)
    int incrementUsageIfAvailable(@Param("promotionId") UUID promotionId, @Param("tenantId") UUID tenantId);

    /**
     * Atomic usage counter decrement when a coupon is removed from an unpaid bill.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Promotion p
            SET p.currentUsageCount = p.currentUsageCount - 1
            WHERE p.id = :promotionId
              AND p.tenantId = :tenantId
              AND p.currentUsageCount > 0
            """)
    int decrementUsageIfPositive(@Param("promotionId") UUID promotionId, @Param("tenantId") UUID tenantId);
}
