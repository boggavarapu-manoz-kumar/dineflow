package com.dineflow.payment.repository;

import com.dineflow.payment.domain.RefundStatus;
import com.dineflow.payment.domain.RestaurantRefund;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantRefundRepository extends JpaRepository<RestaurantRefund, UUID> {
    Optional<RestaurantRefund> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RestaurantRefund r WHERE r.id = :id AND r.tenantId = :tenantId")
    Optional<RestaurantRefund> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("""
            SELECT r FROM RestaurantRefund r
            WHERE (:tenantId IS NULL OR r.tenantId = :tenantId)
              AND r.refundStatus = 'INITIATED'
              AND r.manualReviewRequired = false
              AND (r.nextReconciliationAt IS NULL OR r.nextReconciliationAt <= :now)
            ORDER BY r.createdAt ASC
            """)
    Page<RestaurantRefund> findInitiatedRefundsForReconciliation(@Param("tenantId") UUID tenantId,
                                                                 @Param("now") Instant now,
                                                                 Pageable pageable);
    List<RestaurantRefund> findAllByPaymentIdAndTenantId(UUID paymentId, UUID tenantId);
    List<RestaurantRefund> findAllByBillIdAndTenantId(UUID billId, UUID tenantId);
    List<RestaurantRefund> findAllByTenantIdAndBranchIdAndRefundStatus(UUID tenantId, UUID branchId, RefundStatus status);

    List<RestaurantRefund> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<RestaurantRefund> findAllByTenantIdAndBranchIdOrderByCreatedAtDesc(UUID tenantId, UUID branchId);

    /**
     * Aggregates completed (SUCCEEDED) refunds in the UTC reporting window.
     * Returns Object[]: [refundCount, totalRefundAmount]
     */
    @Query("""
            SELECT COUNT(r),
                   COALESCE(SUM(r.refundAmount), 0)
            FROM RestaurantRefund r
            WHERE r.tenantId = :tenantId
              AND (:branchId IS NULL OR r.branchId = :branchId)
              AND r.refundStatus = 'SUCCEEDED'
              AND r.settledAt >= :start
              AND r.settledAt < :end
            """)
    Object[] aggregateSucceededRefunds(@Param("tenantId") UUID tenantId,
                                       @Param("branchId") UUID branchId,
                                       @Param("start") Instant start,
                                       @Param("end") Instant end);

    /**
     * Aggregates completed refunds grouped by branchId.
     */
    @Query("""
            SELECT r.branchId,
                   COALESCE(SUM(r.refundAmount), 0)
            FROM RestaurantRefund r
            WHERE r.tenantId = :tenantId
              AND (:branchId IS NULL OR r.branchId = :branchId)
              AND r.refundStatus = 'SUCCEEDED'
              AND r.settledAt >= :start
              AND r.settledAt < :end
            GROUP BY r.branchId
            """)
    List<Object[]> aggregateSucceededRefundsByBranch(@Param("tenantId") UUID tenantId,
                                                     @Param("branchId") UUID branchId,
                                                     @Param("start") Instant start,
                                                     @Param("end") Instant end);
}
