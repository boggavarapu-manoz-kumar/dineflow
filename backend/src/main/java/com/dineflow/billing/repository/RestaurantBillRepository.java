package com.dineflow.billing.repository;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
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
public interface RestaurantBillRepository extends JpaRepository<RestaurantBill, UUID> {

    Optional<RestaurantBill> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM RestaurantBill b WHERE b.id = :id AND b.tenantId = :tenantId")
    Optional<RestaurantBill> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    Optional<RestaurantBill> findByTenantIdAndBillNumber(UUID tenantId, String billNumber);

    List<RestaurantBill> findAllByTenantIdAndBranchIdAndStatusOrderByCreatedAtDesc(UUID tenantId, UUID branchId, BillStatus status);

    List<RestaurantBill> findAllByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, BillStatus status);

    List<RestaurantBill> findAllByTenantIdOrderByIssuedAtDesc(UUID tenantId);

    List<RestaurantBill> findAllByTenantIdAndBranchIdOrderByIssuedAtDesc(UUID tenantId, UUID branchId);

    List<RestaurantBill> findAllByTenantIdAndTableNumberAndStatusInOrderByCreatedAtDesc(UUID tenantId, String tableNumber, List<BillStatus> statuses);

    Optional<RestaurantBill> findFirstByCustomerSessionIdAndStatusInOrderByCreatedAtDesc(UUID customerSessionId, List<BillStatus> statuses);

    @Query("SELECT b FROM RestaurantBill b JOIN b.orderMappings m WHERE m.order.id = :orderId AND b.tenantId = :tenantId")
    List<RestaurantBill> findAllByOrderIdAndTenantId(@Param("orderId") UUID orderId, @Param("tenantId") UUID tenantId);

    @Query("SELECT b FROM RestaurantBill b JOIN b.orderMappings m WHERE m.order.id = :orderId AND b.tenantId = :tenantId AND b.status <> 'VOIDED'")
    Optional<RestaurantBill> findActiveBillByOrderIdAndTenantId(@Param("orderId") UUID orderId, @Param("tenantId") UUID tenantId);

    /**
     * Database-level financial summary aggregation for non-voided bills in a UTC window.
     * Returns Object[]: [billCount, grossSales(subtotal), discounts, serviceCharge, tax, grandTotal, outstandingBalance]
     */
    @Query("""
            SELECT COUNT(b),
                   COALESCE(SUM(b.subtotal), 0),
                   COALESCE(SUM(b.discountAmount), 0),
                   COALESCE(SUM(b.serviceChargeAmount), 0),
                   COALESCE(SUM(b.taxAmount), 0),
                   COALESCE(SUM(b.grandTotal), 0),
                   COALESCE(SUM(CASE WHEN b.status IN ('ISSUED', 'PARTIALLY_PAID') THEN b.outstandingBalance ELSE 0 END), 0),
                   SUM(CASE WHEN b.status IN ('ISSUED', 'PARTIALLY_PAID') THEN 1 ELSE 0 END)
            FROM RestaurantBill b
            WHERE b.tenantId = :tenantId
              AND (:branchId IS NULL OR b.branchId = :branchId)
              AND b.status <> 'VOIDED'
              AND b.issuedAt >= :start
              AND b.issuedAt < :end
            """)
    Object[] aggregateBillSummary(@Param("tenantId") UUID tenantId,
                                  @Param("branchId") UUID branchId,
                                  @Param("start") Instant start,
                                  @Param("end") Instant end);

    /**
     * Branch-wise bill aggregation.
     * Returns rows of Object[]: [branchId, branchNameSnapshot, billCount, grossSales, discounts, grandTotal, outstandingBalance]
     */
    @Query("""
            SELECT b.branchId,
                   MAX(b.branchNameSnapshot),
                   COUNT(b),
                   COALESCE(SUM(b.subtotal), 0),
                   COALESCE(SUM(b.discountAmount), 0),
                   COALESCE(SUM(b.grandTotal), 0),
                   COALESCE(SUM(CASE WHEN b.status IN ('ISSUED', 'PARTIALLY_PAID') THEN b.outstandingBalance ELSE 0 END), 0)
            FROM RestaurantBill b
            WHERE b.tenantId = :tenantId
              AND (:branchId IS NULL OR b.branchId = :branchId)
              AND b.status <> 'VOIDED'
              AND b.issuedAt >= :start
              AND b.issuedAt < :end
            GROUP BY b.branchId
            """)
    List<Object[]> aggregateBillsByBranch(@Param("tenantId") UUID tenantId,
                                          @Param("branchId") UUID branchId,
                                          @Param("start") Instant start,
                                          @Param("end") Instant end);

    /**
     * Lightweight projection for time-bucket trend grouping (daily/monthly in tenant timezone).
     * Returns rows of Object[]: [issuedAt, subtotal, discountAmount, grandTotal]
     */
    @Query("""
            SELECT b.issuedAt, b.subtotal, b.discountAmount, b.grandTotal
            FROM RestaurantBill b
            WHERE b.tenantId = :tenantId
              AND (:branchId IS NULL OR b.branchId = :branchId)
              AND b.status <> 'VOIDED'
              AND b.issuedAt >= :start
              AND b.issuedAt < :end
            ORDER BY b.issuedAt ASC
            """)
    List<Object[]> findBillTimestampsAndTotalsForTrend(@Param("tenantId") UUID tenantId,
                                                       @Param("branchId") UUID branchId,
                                                       @Param("start") Instant start,
                                                       @Param("end") Instant end);

    /**
     * Paginated query for memory-safe streaming CSV exports.
     */
    @Query("""
            SELECT b FROM RestaurantBill b
            WHERE b.tenantId = :tenantId
              AND (:branchId IS NULL OR b.branchId = :branchId)
              AND b.issuedAt >= :start
              AND b.issuedAt < :end
            ORDER BY b.issuedAt ASC
            """)
    Page<RestaurantBill> findPageForExport(@Param("tenantId") UUID tenantId,
                                           @Param("branchId") UUID branchId,
                                           @Param("start") Instant start,
                                           @Param("end") Instant end,
                                           Pageable pageable);
}
